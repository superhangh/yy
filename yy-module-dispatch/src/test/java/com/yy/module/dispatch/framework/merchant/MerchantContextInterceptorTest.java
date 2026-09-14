package com.yy.module.dispatch.framework.merchant;

import com.yy.framework.common.exception.ServiceException;
import com.yy.framework.security.core.util.SecurityFrameworkUtils;
import com.yy.module.dispatch.dal.dataobject.merchant.DispatchMerchantDO;
import com.yy.module.dispatch.enums.merchant.DispatchMerchantStatusEnum;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextHolder;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextInterceptor;
import com.yy.module.dispatch.framework.merchant.core.MerchantIdentity;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.method.HandlerMethod;

import static com.yy.module.dispatch.enums.ErrorCodeConstants.MERCHANT_NOT_ENABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MerchantContextInterceptorTest {

    @AfterEach
    public void tearDown() {
        MerchantContextHolder.clear();
    }

    @Test
    public void testPreHandle_nonHandler() {
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(mock(DispatchMerchantService.class));
        assertTrue(interceptor.preHandle(null, null, new Object()));
    }

    @Test
    public void testPreHandle_merchantEnabled() throws Exception {
        DispatchMerchantService service = mock(DispatchMerchantService.class);
        when(service.getMerchantByMemberUserId(1001L)).thenReturn(
                DispatchMerchantDO.builder().id(2001L)
                        .status(DispatchMerchantStatusEnum.ENABLED.getStatus()).build());
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(service);
        HandlerMethod handlerMethod = new HandlerMethod(new FakeMerchantController(),
                FakeMerchantController.class.getMethod("doSomething"));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            assertTrue(interceptor.preHandle(null, null, handlerMethod));
            assertEquals(2001L, MerchantContextHolder.getMerchantId());
        }
    }

    @Test
    public void testPreHandle_merchantNotEnabled() throws Exception {
        DispatchMerchantService service = mock(DispatchMerchantService.class);
        when(service.getMerchantByMemberUserId(1001L)).thenReturn(
                DispatchMerchantDO.builder().id(2001L)
                        .status(DispatchMerchantStatusEnum.PENDING.getStatus()).build());
        MerchantContextInterceptor interceptor = new MerchantContextInterceptor(service);
        HandlerMethod handlerMethod = new HandlerMethod(new FakeMerchantController(),
                FakeMerchantController.class.getMethod("doSomething"));
        try (MockedStatic<SecurityFrameworkUtils> mocked = mockStatic(SecurityFrameworkUtils.class)) {
            mocked.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1001L);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> interceptor.preHandle(null, null, handlerMethod));
            assertEquals(MERCHANT_NOT_ENABLED.getCode(), ex.getCode());
        }
    }

    static class FakeMerchantController {
        @MerchantIdentity
        public void doSomething() {
        }
    }
}
