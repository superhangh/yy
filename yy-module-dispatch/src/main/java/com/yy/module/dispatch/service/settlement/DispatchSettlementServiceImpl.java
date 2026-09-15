package com.yy.module.dispatch.service.settlement;

import com.yy.framework.common.pojo.PageResult;
import com.yy.module.dispatch.controller.admin.settlement.vo.DispatchSettlementPageReqVO;
import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.dal.dataobject.settlement.DispatchSettlementDO;
import com.yy.module.dispatch.dal.mysql.order.DispatchOrderMapper;
import com.yy.module.dispatch.dal.mysql.settlement.DispatchSettlementMapper;
import com.yy.module.dispatch.enums.order.DispatchOrderPayStatusEnum;
import com.yy.module.dispatch.enums.settlement.DispatchSettlementStatusEnum;
import com.yy.module.dispatch.service.wallet.DispatchMerchantWalletService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.*;

@Service
@Validated
public class DispatchSettlementServiceImpl implements DispatchSettlementService {

    @Resource
    private DispatchSettlementMapper settlementMapper;
    @Resource
    private DispatchOrderMapper orderMapper;
    @Resource
    private DispatchMerchantWalletService walletService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrder(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        if (!DispatchOrderPayStatusEnum.UNPAID.getStatus().equals(order.getPayStatus())) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
        // 原子扣款：余额不足直接失败
        boolean ok = walletService.deduct(order.getMerchantId(), order.getAmount());
        if (!ok) {
            throw exception(WALLET_INSUFFICIENT_BALANCE);
        }
        // 写结算记录
        DispatchSettlementDO settlement = DispatchSettlementDO.builder()
                .orderId(orderId).merchantId(order.getMerchantId()).userId(order.getUserId())
                .amount(order.getAmount()).status(DispatchSettlementStatusEnum.PAID.getStatus())
                .settleTime(LocalDateTime.now()).build();
        settlementMapper.insert(settlement);
        // 条件更新订单 pay_status（防并发重复结算）
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.PAID.getStatus())
                .eq(DispatchOrderDO::getId, orderId)
                .eq(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.UNPAID.getStatus()));
        if (rows == 0) {
            // 被并发结算了，回滚扣款
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long orderId) {
        DispatchOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            throw exception(ORDER_NOT_EXISTS);
        }
        if (!DispatchOrderPayStatusEnum.PAID.getStatus().equals(order.getPayStatus())) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
        // 退款入账到商家钱包
        walletService.credit(order.getMerchantId(), order.getAmount());
        // 更新结算记录为已退款
        settlementMapper.update(null, new LambdaUpdateWrapper<DispatchSettlementDO>()
                .set(DispatchSettlementDO::getStatus, DispatchSettlementStatusEnum.REFUNDED.getStatus())
                .set(DispatchSettlementDO::getRefundTime, LocalDateTime.now())
                .eq(DispatchSettlementDO::getOrderId, orderId)
                .eq(DispatchSettlementDO::getStatus, DispatchSettlementStatusEnum.PAID.getStatus()));
        // 条件更新订单 pay_status
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<DispatchOrderDO>()
                .set(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.REFUNDED.getStatus())
                .eq(DispatchOrderDO::getId, orderId)
                .eq(DispatchOrderDO::getPayStatus, DispatchOrderPayStatusEnum.PAID.getStatus()));
        if (rows == 0) {
            throw exception(SETTLEMENT_STATUS_ERROR);
        }
    }

    @Override
    public DispatchSettlementDO getSettlement(Long orderId) {
        return settlementMapper.selectByOrderId(orderId);
    }

    @Override
    public PageResult<DispatchSettlementDO> getSettlementPage(DispatchSettlementPageReqVO reqVO) {
        return settlementMapper.selectPage(reqVO);
    }
}
