package com.yy.module.dispatch.mq.consumer.order;

import com.yy.framework.common.enums.UserTypeEnum;
import com.yy.framework.websocket.core.sender.WebSocketMessageSender;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCancelledMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import jakarta.annotation.Resource;

/**
 * 派单订单事件消费者：WebSocket 实时推送
 *
 * 使用 {@link TransactionalEventListener}（AFTER_COMMIT）：事务提交后才广播，
 * 避免「回滚了却已广播」的幽灵消息，也避免发送异常连带回滚业务写入。
 * fallbackExecution=true：无事务时（如直接调用）也立即执行。
 *
 * 推送是「尽力而为」：发送异常只记 warn，不向上抛（否则事务已提交，调用方却收到失败）。
 */
@Slf4j
@Component
public class DispatchOrderWebSocketConsumer {

    /** 新单广播（所有在线会员） */
    public static final String MESSAGE_TYPE_CREATED = "dispatch.new";
    /** 抢单广播（所有在线会员，其他端从大厅移除） */
    public static final String MESSAGE_TYPE_GRABBED = "dispatch.grabbed";
    /** 接单定向通知（发单商家） */
    public static final String MESSAGE_TYPE_ACCEPTED = "dispatch.accepted";
    /** 取消定向通知（发单商家，如超时未接单） */
    public static final String MESSAGE_TYPE_CANCELLED = "dispatch.cancelled";

    @Resource
    private WebSocketMessageSender webSocketMessageSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCreated(DispatchOrderCreatedMessage message) {
        send(UserTypeEnum.MEMBER.getValue(), null, MESSAGE_TYPE_CREATED, message);
        log.info("[onOrderCreated][广播新单({})]", message.getOrderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderAccepted(DispatchOrderAcceptedMessage message) {
        // 广播给所有在线会员：其他用户端从大厅移除该单
        send(UserTypeEnum.MEMBER.getValue(), null, MESSAGE_TYPE_GRABBED, message);
        // 定向通知发单商家
        if (message.getMerchantMemberUserId() != null) {
            send(UserTypeEnum.MEMBER.getValue(), message.getMerchantMemberUserId(), MESSAGE_TYPE_ACCEPTED, message);
        }
        log.info("[onOrderAccepted][订单({})被用户({})接单]", message.getOrderId(), message.getUserId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(DispatchOrderCancelledMessage message) {
        // 定向通知发单商家：订单被取消（如超时未接单）
        if (message.getMerchantMemberUserId() != null) {
            send(UserTypeEnum.MEMBER.getValue(), message.getMerchantMemberUserId(), MESSAGE_TYPE_CANCELLED, message);
        }
        log.info("[onOrderCancelled][订单({})被取消：{}]", message.getOrderId(), message.getReason());
    }

    /**
     * 尽力而为地发送：异常只记 warn，不向上抛（事务已提交，不能因推送失败让调用方以为业务失败）
     *
     * @param userId null 表示广播给该类型所有用户
     */
    private void send(Integer userType, Long userId, String messageType, Object content) {
        try {
            if (userId != null) {
                webSocketMessageSender.sendObject(userType, userId, messageType, content);
            } else {
                webSocketMessageSender.sendObject(userType, messageType, content);
            }
        } catch (Exception ex) {
            log.warn("[send][WebSocket 推送失败，type({}) userId({}) messageType({})]",
                    userType, userId, messageType, ex);
        }
    }

}
