package com.yy.module.dispatch.mq.message.order;

import lombok.Data;

@Data
public class DispatchOrderCompletedMessage {
    private Long orderId;
}
