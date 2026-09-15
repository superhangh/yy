package com.yy.module.dispatch.enums.settlement;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum DispatchSettlementStatusEnum implements ArrayValuable<Integer> {

    UNPAID(0, "待结算"),
    PAID(1, "已结算"),
    REFUNDED(2, "已退款");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchSettlementStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
