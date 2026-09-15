package com.yy.module.dispatch.mq.message.order;

import lombok.Data;

/**
 * 派单订单被接单消息：广播给在线用户（从大厅移除）+ 定向通知商家
 */
@Data
public class DispatchOrderAcceptedMessage {

    private Long orderId;
    private Long userId;
    /** 发单商家的会员编号，用于定向通知 */
    private Long merchantMemberUserId;

}
