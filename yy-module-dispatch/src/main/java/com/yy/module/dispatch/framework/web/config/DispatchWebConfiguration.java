package com.yy.module.dispatch.framework.web.config;

import com.yy.framework.swagger.config.YySwaggerAutoConfiguration;
import com.yy.module.dispatch.framework.merchant.core.MerchantContextInterceptor;
import com.yy.module.dispatch.service.merchant.DispatchMerchantService;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * dispatch 模块的 web 组件 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class DispatchWebConfiguration implements WebMvcConfigurer {

    /**
     * 使用 ObjectProvider 延迟获取，避免 Configuration 与自身 @Bean 之间形成循环依赖
     */
    private final ObjectProvider<MerchantContextInterceptor> merchantContextInterceptorProvider;

    public DispatchWebConfiguration(ObjectProvider<MerchantContextInterceptor> merchantContextInterceptorProvider) {
        this.merchantContextInterceptorProvider = merchantContextInterceptorProvider;
    }

    @Bean
    public GroupedOpenApi dispatchGroupedOpenApi() {
        return YySwaggerAutoConfiguration.buildGroupedOpenApi("dispatch");
    }

    @Bean
    public MerchantContextInterceptor merchantContextInterceptor(DispatchMerchantService merchantService) {
        return new MerchantContextInterceptor(merchantService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(merchantContextInterceptorProvider.getObject())
                .addPathPatterns("/app-api/merchant/**");
    }

}
