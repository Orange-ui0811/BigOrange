package com.orangejd.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ComponentScan(basePackages = {"com.orangejd.common", "com.orangejd.pojo", "com.orangejd.server"})
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
public class OrangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrangeApplication.class, args);
    }
}

