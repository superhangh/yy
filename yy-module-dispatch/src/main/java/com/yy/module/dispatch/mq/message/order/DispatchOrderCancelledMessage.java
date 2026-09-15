package com.yy.module.dispatch.mq.message.order;

import lombok.Data;

/**
 * 派单订单被取消消息（如超时未接单系统自动取消）：定向通知商家
 */
@Data
public class DispatchOrderCancelledMessage {

    private Long orderId;
    /** 发单商家的会员编号，用于定向通知 */
    private Long merchantMemberUserId;
    /** 取消原因 */
    private String reason;

}
