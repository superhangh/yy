package com.yy.module.dispatch.framework.merchant.core;

import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.yy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_ENABLED;

/**
 * 商家身份拦截器：拦截 /app-api/merchant/**，从登录态推导商家并写入上下文
 *
 * 只信任登录态，不信任前端传入的 merchantId。
 *
 * 注意：本类由 DispatchWebConfiguration 用 new 创建，故依赖走「构造器注入」，
 * 不能用 @Resource 字段注入（new 出来的对象不会被 Spring 注入字段）。
 */
public class MerchantContextInterceptor implements HandlerInterceptor {

    private final DispatchMerchantService merchantService;

    public MerchantContextInterceptor(DispatchMerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 仅处理标注了 @MerchantIdentity 的接口
        boolean needMerchant = handlerMethod.hasMethodAnnotation(MerchantIdentity.class)
                || handlerMethod.getBeanType().isAnnotationPresent(MerchantIdentity.class);
        if (!needMerchant) {
            return true;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        DispatchMerchantDO merchant = merchantService.getMerchantByMemberUserId(userId);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        if (!DispatchMerchantStatusEnum.ENABLED.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_NOT_ENABLED);
        }
        MerchantContextHolder.setMerchantId(merchant.getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MerchantContextHolder.clear();
    }
}
