package com.yy.module.dispatch.mq.consumer.settlement;

import com.yy.module.dispatch.mq.message.order.DispatchOrderCompletedMessage;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;

/**
 * 订单完成 → 自动结算（事务提交后执行；失败仅 log，不影响已完成订单）
 */
@Slf4j
@Component
public class DispatchSettlementConsumer {

    @Resource
    private DispatchSettlementService settlementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCompleted(DispatchOrderCompletedMessage message) {
        try {
            settlementService.settleOrder(message.getOrderId());
            log.info("[onOrderCompleted][订单({})结算成功]", message.getOrderId());
        } catch (Exception ex) {
            // 结算失败（如余额不足）不影响已完成订单，记录日志即可
            log.warn("[onOrderCompleted][订单({})结算失败：{}]", message.getOrderId(), ex.getMessage());
        }
    }
}
