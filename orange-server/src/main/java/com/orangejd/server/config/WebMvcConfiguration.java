package com.orangejd.server.config;

import com.orangejd.common.json.JacksonObjectMapper;
import com.orangejd.server.interceptor.JwtTokenAdminInterceptor;
import com.orangejd.server.interceptor.JwtTokenAppInterceptor;
import com.orangejd.server.interceptor.AgentDeviceInterceptor;
import com.orangejd.server.interceptor.RequestIdInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Value("${orange.upload.path}")
    private String uploadPath;

    @Value("${orange.security.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:15173,http://localhost:15174}")
    private String allowedOrigins;

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    @Autowired
    private JwtTokenAppInterceptor jwtTokenAppInterceptor;

    @Autowired
    private AgentDeviceInterceptor agentDeviceInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true);
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0, converter);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + File.separator);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestIdInterceptor()).addPathPatterns("/**");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/auth/login");

        registry.addInterceptor(jwtTokenAppInterceptor)
                .addPathPatterns("/app/**")
                .excludePathPatterns("/app/auth/login", "/app/auth/register", "/app/auth/send-code",
                        "/app/auth/send-reset-code", "/app/auth/reset-password",
                        "/app/category", "/app/work/public", "/app/requirement/public",
                        "/app/config/trade-mode",
                        "/app/leaderboard/**",
                        "/app/trend/graph",
                        "/ws/**");

        registry.addInterceptor(agentDeviceInterceptor)
                .addPathPatterns("/agent/**")
                .excludePathPatterns("/agent/connect/exchange");
    }
}
