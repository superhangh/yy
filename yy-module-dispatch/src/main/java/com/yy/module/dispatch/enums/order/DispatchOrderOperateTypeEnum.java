package com.yy.module.dispatch.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 派单订单操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderOperateTypeEnum {

    MERCHANT_CREATE(1, "商家发单"),
    USER_ACCEPT(10, "用户接单"),
    USER_START(20, "用户开始服务"),
    USER_FINISH(30, "用户完成服务"),
    MERCHANT_CANCEL(40, "商家取消订单"),
    SYSTEM_CANCEL(41, "系统自动取消订单"),
    ADMIN_CANCEL(42, "管理员取消订单");

    private final Integer type;
    private final String content;
}
