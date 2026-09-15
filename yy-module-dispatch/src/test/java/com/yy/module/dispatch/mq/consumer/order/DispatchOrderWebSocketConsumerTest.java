package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DispatchOrderWebSocketConsumerTest {

    @Test
    public void testOnOrderCreated_broadcastToMembers() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderWebSocketConsumer consumer = new DispatchOrderWebSocketConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "webSocketMessageSender", sender);

        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(1L);
        consumer.onOrderCreated(message);

        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_CREATED), same(message));
    }

    @Test
    public void testOnOrderAccepted_broadcastAndNotifyMerchant() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderWebSocketConsumer consumer = new DispatchOrderWebSocketConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "webSocketMessageSender", sender);

        DispatchOrderAcceptedMessage message = new DispatchOrderAcceptedMessage();
        message.setOrderId(1L);
        message.setUserId(1001L);
        message.setMerchantMemberUserId(2001L);
        consumer.onOrderAccepted(message);

        // 广播给所有会员
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_ACCEPTED), same(message));
        // 定向通知商家
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()), eq(2001L),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_ACCEPTED), same(message));
    }
}
