package com.yy.module.dispatch.enums.merchant;

import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 商家状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchMerchantStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待审核"),
    ENABLED(1, "正常"),
    DISABLED(2, "禁用");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchMerchantStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
