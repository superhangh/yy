package com.yy.module.dispatch.mq.producer.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import com.yy.module.dispatch.mq.message.order.DispatchOrderAcceptedMessage;
import com.yy.module.dispatch.mq.message.order.DispatchOrderCreatedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 派单订单事件 Producer
 */
@Slf4j
@Component
public class DispatchOrderProducer {

    @Resource
    private ApplicationContext applicationContext;

    public void sendOrderCreated(DispatchOrderDO order) {
        applicationContext.publishEvent(DispatchOrderCreatedMessage.of(order));
    }

    public void sendOrderAccepted(Long orderId, Long userId, Long merchantMemberUserId) {
        DispatchOrderAcceptedMessage message = new DispatchOrderAcceptedMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setMerchantMemberUserId(merchantMemberUserId);
        applicationContext.publishEvent(message);
    }

}
