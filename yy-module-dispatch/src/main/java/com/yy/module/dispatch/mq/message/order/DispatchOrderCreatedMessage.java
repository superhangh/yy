package com.yy.module.dispatch.mq.message.order;

import com.yy.module.dispatch.dal.dataobject.order.DispatchOrderDO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单订单创建消息：用于 WebSocket 广播给在线用户抢单
 */
@Data
public class DispatchOrderCreatedMessage {

    private Long orderId;
    private String title;
    private Integer amount;
    private String address;
    private LocalDateTime deadline;

    public static DispatchOrderCreatedMessage of(DispatchOrderDO order) {
        DispatchOrderCreatedMessage message = new DispatchOrderCreatedMessage();
        message.setOrderId(order.getId());
        message.setTitle(order.getTitle());
        message.setAmount(order.getAmount());
        message.setAddress(order.getAddress());
        message.setDeadline(order.getDeadline());
        return message;
    }
}
