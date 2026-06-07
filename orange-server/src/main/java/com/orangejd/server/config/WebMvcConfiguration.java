package com.orangejd.server.config;

import com.orangejd.server.interceptor.JwtTokenAdminInterceptor;
import com.orangejd.server.interceptor.JwtTokenAppInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenAppInterceptor jwtTokenAppInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/login");

        registry.addInterceptor(jwtTokenAppInterceptor)
                .addPathPatterns("/app/**")
                .excludePathPatterns("/app/auth/login", "/app/auth/register");
    }
}

