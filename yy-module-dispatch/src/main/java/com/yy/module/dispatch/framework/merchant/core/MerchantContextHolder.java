package com.yy.module.dispatch.framework.merchant.core;

/**
 * 商家上下文 Holder，基于 ThreadLocal
 *
 * 仅由 MerchantContextInterceptor 写入，业务代码只读取。
 */
public class MerchantContextHolder {

    private static final ThreadLocal<Long> MERCHANT_ID = new ThreadLocal<>();

    public static void setMerchantId(Long merchantId) {
        MERCHANT_ID.set(merchantId);
    }

    public static Long getMerchantId() {
        return MERCHANT_ID.get();
    }

    public static void clear() {
        MERCHANT_ID.remove();
    }
}
