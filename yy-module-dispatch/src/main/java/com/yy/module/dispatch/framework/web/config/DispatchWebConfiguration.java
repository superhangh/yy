package com.yy.module.dispatch.framework.web.config;

import com.yy.framework.swagger.config.YySwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * dispatch 模块的 web 组件 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class DispatchWebConfiguration {

    @Bean
    public GroupedOpenApi dispatchGroupedOpenApi() {
        return YySwaggerAutoConfiguration.buildGroupedOpenApi("dispatch");
    }

}
