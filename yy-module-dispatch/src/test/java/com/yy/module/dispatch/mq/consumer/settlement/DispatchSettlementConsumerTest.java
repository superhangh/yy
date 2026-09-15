package com.yy.module.dispatch.mq.consumer.settlement;

import com.yy.module.dispatch.mq.message.order.DispatchOrderCompletedMessage;
import com.yy.module.dispatch.service.settlement.DispatchSettlementService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class DispatchSettlementConsumerTest {

    @Test
    public void testOnOrderCompleted_success() {
        DispatchSettlementService service = mock(DispatchSettlementService.class);
        DispatchSettlementConsumer consumer = new DispatchSettlementConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "settlementService", service);

        DispatchOrderCompletedMessage message = new DispatchOrderCompletedMessage();
        message.setOrderId(1L);
        consumer.onOrderCompleted(message);

        verify(service).settleOrder(1L);
    }

    @Test
    public void testOnOrderCompleted_failureSwallowed() {
        DispatchSettlementService service = mock(DispatchSettlementService.class);
        doThrow(new RuntimeException("余额不足")).when(service).settleOrder(anyLong());
        DispatchSettlementConsumer consumer = new DispatchSettlementConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "settlementService", service);

        DispatchOrderCompletedMessage message = new DispatchOrderCompletedMessage();
        message.setOrderId(1L);
        // 异常被吞掉，不向上抛
        consumer.onOrderCompleted(message);

        verify(service).settleOrder(1L);
    }
}
