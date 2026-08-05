package com.orangejd.server.config;

import com.orangejd.common.json.JacksonObjectMapper;
import com.orangejd.server.interceptor.JwtTokenAdminInterceptor;
import com.orangejd.server.interceptor.JwtTokenAppInterceptor;
import com.orangejd.server.interceptor.AgentDeviceInterceptor;
import com.orangejd.server.interceptor.RequestIdInterceptor;
import com.orangejd.server.security.ApiRoutePolicy;
import com.orangejd.server.security.RateLimitInterceptor;
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
@lombok.RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Value("${orange.upload.path}")
    private String uploadPath;

    @Value("${orange.security.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:15173,http://localhost:15174}")
    private String allowedOrigins;

    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    private final JwtTokenAppInterceptor jwtTokenAppInterceptor;

    private final AgentDeviceInterceptor agentDeviceInterceptor;

    private final RateLimitInterceptor rateLimitInterceptor;

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
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/app/**", "/admin/**", "/agent/**");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(ApiRoutePolicy.ADMIN_PUBLIC_PATHS);

        registry.addInterceptor(jwtTokenAppInterceptor)
                .addPathPatterns("/app/**")
                .excludePathPatterns(ApiRoutePolicy.APP_PUBLIC_PATHS);

        registry.addInterceptor(agentDeviceInterceptor)
                .addPathPatterns("/agent/**")
                .excludePathPatterns(ApiRoutePolicy.AGENT_PUBLIC_PATHS);
    }
}
