package com.yy.module.dispatch.service.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import com.yy.module.dispatch.mq.producer.order.DispatchOrderProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchOrderServiceImpl implements DispatchOrderService {

    @Resource
    private DispatchOrderMapper orderMapper;
    @Resource
    private DispatchOrderLogService orderLogService;
    @Resource
    private DispatchOrderProducer orderProducer;
    @Resource
    private com.yy.module.dispatch.service.merchant.DispatchMerchantService merchantService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long merchantId, DispatchOrderCreateReqVO reqVO) {
        DispatchOrderDO order = DispatchOrderDO.builder()
                .no(generateNo())
                .merchantId(merchantId)
                .status(DispatchOrderStatusEnum.PENDING.getStatus())
                .payStatus(DispatchOrderPayStatusEnum.UNPAID.getStatus())
                .title(reqVO.getTitle())
                .description(reqVO.getDescription())
                .images(reqVO.getImages())
                .address(reqVO.getAddress())
                .contactName(reqVO.getContactName())
                .contactMobile(reqVO.getContactMobile())
                .amount(reqVO.getAmount())
                .deadline(reqVO.getDeadline())
                .build();
        orderMapper.insert(order);
        orderLogService.createLog(order.getId(), DispatchOrderOperateTypeEnum.MERCHANT_CREATE,
                DispatchOrderOperateTypeEnum.MERCHANT_CREATE.getContent());
        orderProducer.sendOrderCreated(order);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        // 抢单：条件更新保证原子
        int rows = orderMapper.updateAccept(orderId, userId);
        if (rows == 0) {
            throw exception(ORDER_ALREADY_ACCEPTED);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_ACCEPT,
                DispatchOrderOperateTypeEnum.USER_ACCEPT.getContent());
        // 通知：广播给在线用户 + 定向通知商家
        Long merchantMemberUserId = null;
        var merchant = merchantService.getMerchant(order.getMerchantId());
        if (merchant != null) {
            merchantMemberUserId = merchant.getMemberUserId();
        }
        orderProducer.sendOrderAccepted(orderId, userId, merchantMemberUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!DispatchOrderStatusEnum.isAccepted(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        validateOrderOwnerByUser(order, userId);
        // 条件更新，避免与并发取消/完成互相覆盖
        if (orderMapper.updateStart(orderId) == 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_START,
                DispatchOrderOperateTypeEnum.USER_START.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!DispatchOrderStatusEnum.isServing(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        validateOrderOwnerByUser(order, userId);
        // 条件更新，避免与并发取消互相覆盖
        if (orderMapper.updateFinish(orderId) == 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_FINISH,
                DispatchOrderOperateTypeEnum.USER_FINISH.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long merchantId, String reason) {
        validateOrderOwnerByMerchant(orderId, merchantId);
        // 仅待接单可取消；条件更新避免覆盖并发抢单
        if (orderMapper.updateCancel(orderId, reason, DispatchOrderStatusEnum.PENDING.getStatus()) == 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.MERCHANT_CANCEL, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByAdmin(Long orderId, String reason) {
        validateOrderExists(orderId);
        // 未完成/未取消可强制取消；条件更新避免覆盖并发完成
        if (orderMapper.updateCancel(orderId, reason,
                DispatchOrderStatusEnum.PENDING.getStatus(),
                DispatchOrderStatusEnum.ACCEPTED.getStatus(),
                DispatchOrderStatusEnum.SERVING.getStatus()) == 0) {
            throw exception(ORDER_STATUS_ERROR);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.ADMIN_CANCEL, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders() {
        List<DispatchOrderDO> orders = orderMapper.selectListByStatusAndDeadlineLt(
                DispatchOrderStatusEnum.PENDING.getStatus(), LocalDateTime.now());
        int count = 0;
        for (DispatchOrderDO order : orders) {
            String reason = "超时未接单，系统自动取消";
            // 条件更新：仅当仍是待接单才取消，避免与并发抢单互相覆盖
            if (orderMapper.updateCancel(order.getId(), reason,
                    DispatchOrderStatusEnum.PENDING.getStatus()) > 0) {
                orderLogService.createLog(order.getId(), DispatchOrderOperateTypeEnum.SYSTEM_CANCEL,
                        DispatchOrderOperateTypeEnum.SYSTEM_CANCEL.getContent());
                // 通知发单商家
                Long merchantMemberUserId = null;
                var merchant = merchantService.getMerchant(order.getMerchantId());
                if (merchant != null) {
                    merchantMemberUserId = merchant.getMemberUserId();
                }
                orderProducer.sendOrderCancelled(order.getId(), merchantMemberUserId, reason);
                count++;
            }
        }
        return count;
    }

    @Override
    public DispatchOrderDO getOrder(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    public DispatchOrderDO getUserOrder(Long orderId, Long userId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        // 待接单订单在大厅公开可见；其余仅本人接单可见（避免越权读取他人订单的地址/联系方式）
        boolean visible = DispatchOrderStatusEnum.isPending(order.getStatus())
                || java.util.Objects.equals(order.getUserId(), userId);
        if (!visible) {
            throw exception(ORDER_NOT_EXISTS);
        }
        return order;
    }

    @Override
    public DispatchOrderDO getMerchantOrder(Long orderId, Long merchantId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null || !java.util.Objects.equals(order.getMerchantId(), merchantId)) {
            throw exception(ORDER_NOT_EXISTS);
        }
        return order;
    }

    @Override
    public PageResult<DispatchOrderDO> getHallPage(DispatchOrderPageReqVO reqVO) {
        reqVO.setStatus(DispatchOrderStatusEnum.PENDING.getStatus());
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getUserOrderPage(Long userId, DispatchOrderPageReqVO reqVO) {
        reqVO.setUserId(userId);
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getMerchantOrderPage(Long merchantId, DispatchOrderPageReqVO reqVO) {
        reqVO.setMerchantId(merchantId);
        return orderMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<DispatchOrderDO> getAdminOrderPage(DispatchOrderPageReqVO reqVO) {
        return orderMapper.selectPage(reqVO);
    }

    // ========== 校验 ==========

    private DispatchOrderDO validateOrderExists(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        return order;
    }

    private void validateOrderOwnerByUser(DispatchOrderDO order, Long userId) {
        if (!java.util.Objects.equals(order.getUserId(), userId)) {
            throw exception(ORDER_NOT_USER_OWNER);
        }
    }

    private DispatchOrderDO validateOrderOwnerByMerchant(Long orderId, Long merchantId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!java.util.Objects.equals(order.getMerchantId(), merchantId)) {
            throw exception(ORDER_NOT_MERCHANT_OWNER);
        }
        return order;
    }

    private String generateNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
