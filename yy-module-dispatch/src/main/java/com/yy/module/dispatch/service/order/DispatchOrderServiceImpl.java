package com.yy.module.dispatch.service.order;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderCreateReqVO;
import com.yy.module.dispatch.controller.admin.order.vo.DispatchOrderPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderOperateTypeEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.order.DispatchOrderStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Override
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
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long orderId, Long userId) {
        validateOrderExists(orderId);
        // 抢单：条件更新保证原子
        int rows = orderMapper.updateAccept(orderId, userId);
        if (rows == 0) {
            throw exception(ORDER_ALREADY_ACCEPTED);
        }
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_ACCEPT,
                DispatchOrderOperateTypeEnum.USER_ACCEPT.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startOrder(Long orderId, Long userId) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (!DispatchOrderStatusEnum.isAccepted(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        validateOrderOwnerByUser(order, userId);
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.SERVING.getStatus())
                .startTime(LocalDateTime.now()).build();
        orderMapper.updateById(update);
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
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.COMPLETED.getStatus())
                .finishTime(LocalDateTime.now()).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.USER_FINISH,
                DispatchOrderOperateTypeEnum.USER_FINISH.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long merchantId, String reason) {
        DispatchOrderDO order = validateOrderOwnerByMerchant(orderId, merchantId);
        // 仅待接单可取消（已接单后的取消走后续退款流程，暂不允许）
        if (!DispatchOrderStatusEnum.isPending(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.CANCELED.getStatus())
                .cancelReason(reason).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.MERCHANT_CANCEL, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByAdmin(Long orderId, String reason) {
        DispatchOrderDO order = validateOrderExists(orderId);
        if (DispatchOrderStatusEnum.isCompleted(order.getStatus()) || DispatchOrderStatusEnum.isCanceled(order.getStatus())) {
            throw exception(ORDER_STATUS_ERROR);
        }
        DispatchOrderDO update = DispatchOrderDO.builder()
                .id(orderId).status(DispatchOrderStatusEnum.CANCELED.getStatus())
                .cancelReason(reason).build();
        orderMapper.updateById(update);
        orderLogService.createLog(orderId, DispatchOrderOperateTypeEnum.ADMIN_CANCEL, reason);
    }

    @Override
    public DispatchOrderDO getOrder(Long orderId) {
        return orderMapper.selectById(orderId);
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
