package com.zjkl.common.config;

import com.zjkl.common.config.properties.AuthProperties;
import com.zjkl.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 - 拦截器
 * 虚拟线程已启用，无需自定义异步执行器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AuthProperties authProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(authProperties.getWhitelist() != null
                        ? authProperties.getWhitelist().toArray(new String[0])
                        : new String[0]);
    }
}
