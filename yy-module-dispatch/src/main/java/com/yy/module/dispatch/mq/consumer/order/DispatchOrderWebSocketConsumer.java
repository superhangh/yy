package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单事件消费者：WebSocket 实时推送
 */
@Slf4j
@Component
public class DispatchOrderWebSocketConsumer {

    public static final String MESSAGE_TYPE_CREATED = "dispatch-order-created";
    public static final String MESSAGE_TYPE_ACCEPTED = "dispatch-order-accepted";

    @Resource
    private WebSocketMessageSender webSocketMessageSender;

    @EventListener
    public void onOrderCreated(DispatchOrderCreatedMessage message) {
        // 广播给所有在线会员：抢单大厅实时刷新
        webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), MESSAGE_TYPE_CREATED, message);
        log.info("[onOrderCreated][广播新单({})]", message.getOrderId());
    }

    @EventListener
    public void onOrderAccepted(DispatchOrderAcceptedMessage message) {
        // 广播给所有在线会员：其他用户端从大厅移除该单
        webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), MESSAGE_TYPE_ACCEPTED, message);
        // 定向通知发单商家
        if (message.getMerchantMemberUserId() != null) {
            webSocketMessageSender.sendObject(UserTypeEnum.MEMBER.getValue(), message.getMerchantMemberUserId(),
                    MESSAGE_TYPE_ACCEPTED, message);
        }
        log.info("[onOrderAccepted][订单({})被用户({})接单]", message.getOrderId(), message.getUserId());
    }

}
