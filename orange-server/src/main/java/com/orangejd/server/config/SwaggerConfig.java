package com.orangejd.server.config;

import com.orangejd.common.constant.BrandConstant;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI orangeJdOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(BrandConstant.PUBLIC_NAME + " 平台 API 文档")
                        .description("创意服务交易平台接口文档 — 管理端 /admin/** 、用户端 /app/**")
                        .version("1.0.0")
                        .contact(new Contact().name(BrandConstant.PUBLIC_NAME)));
    }
}
