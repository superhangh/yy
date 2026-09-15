package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCancelledMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DispatchOrderWebSocketConsumerTest {

    private DispatchOrderWebSocketConsumer newConsumer(WebSocketMessageSender sender) {
        DispatchOrderWebSocketConsumer consumer = new DispatchOrderWebSocketConsumer();
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "webSocketMessageSender", sender);
        return consumer;
    }

    @Test
    public void testOnOrderCreated_broadcastToMembers() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(1L);
        newConsumer(sender).onOrderCreated(message);

        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_CREATED), same(message));
    }

    @Test
    public void testOnOrderAccepted_broadcastAndNotifyMerchant() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderAcceptedMessage message = new DispatchOrderAcceptedMessage();
        message.setOrderId(1L);
        message.setUserId(1001L);
        message.setMerchantMemberUserId(2001L);
        newConsumer(sender).onOrderAccepted(message);

        // 广播给所有会员（dispatch.grabbed）
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_GRABBED), same(message));
        // 定向通知商家（dispatch.accepted）
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()), eq(2001L),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_ACCEPTED), same(message));
    }

    @Test
    public void testOnOrderCancelled_notifyMerchant() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        DispatchOrderCancelledMessage message = new DispatchOrderCancelledMessage();
        message.setOrderId(1L);
        message.setMerchantMemberUserId(2001L);
        message.setReason("超时未接单，系统自动取消");
        newConsumer(sender).onOrderCancelled(message);

        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()), eq(2001L),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_CANCELLED), same(message));
    }

    @Test
    public void testSendFailure_isSwallowed() {
        WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
        doThrow(new RuntimeException("ws down")).when(sender)
                .sendObject(anyInt(), anyString(), any());
        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(1L);
        // 推送失败不应抛出（事务已提交）
        newConsumer(sender).onOrderCreated(message);
        verify(sender).sendObject(eq(UserTypeEnum.MEMBER.getValue()),
                eq(DispatchOrderWebSocketConsumer.MESSAGE_TYPE_CREATED), same(message));
    }
}
