package com.yy.module.dispatch.framework.merchant.core;

import java.lang.annotation.*;

/**
 * 标记商家身份接口：请求必须是「正常状态」的商家
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MerchantIdentity {
}
