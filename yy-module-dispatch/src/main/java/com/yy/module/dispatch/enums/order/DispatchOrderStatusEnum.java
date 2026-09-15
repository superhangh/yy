package com.yy.module.dispatch.enums.order;

import cn.hutool.core.util.ObjectUtil;
import com.yy.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 派单订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum DispatchOrderStatusEnum implements ArrayValuable<Integer> {

    PENDING(0, "待接单"),
    ACCEPTED(10, "已接单"),
    SERVING(20, "服务中"),
    COMPLETED(30, "已完成"),
    CANCELED(40, "已取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(DispatchOrderStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isPending(Integer status) {
        return ObjectUtil.equal(PENDING.getStatus(), status);
    }

    public static boolean isAccepted(Integer status) {
        return ObjectUtil.equal(ACCEPTED.getStatus(), status);
    }

    public static boolean isServing(Integer status) {
        return ObjectUtil.equal(SERVING.getStatus(), status);
    }

    public static boolean isCompleted(Integer status) {
        return ObjectUtil.equal(COMPLETED.getStatus(), status);
    }

    public static boolean isCanceled(Integer status) {
        return ObjectUtil.equal(CANCELED.getStatus(), status);
    }
}
