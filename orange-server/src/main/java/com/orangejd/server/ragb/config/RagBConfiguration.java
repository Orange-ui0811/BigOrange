package com.orangejd.server.ragb.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagBProperties.class)
public class RagBConfiguration {
    private final RagBProperties properties;

    public RagBConfiguration(RagBProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateNamespace() {
        properties.validate();
    }
}
