package com.yy.module.dispatch.enums.order;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 派单订单结算状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderPayStatusEnum implements ArrayValuable<Integer> {

    UNPAID(0, "待结算"),
    PAID(10, "已结算"),
    REFUNDED(20, "已退款");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchOrderPayStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
