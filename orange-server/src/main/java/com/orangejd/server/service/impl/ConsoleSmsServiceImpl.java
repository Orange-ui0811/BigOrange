package com.orangejd.server.service.impl;

import com.orangejd.server.service.SmsService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 开发环境：验证码打印到控制台
 */
@Service
@Profile("dev")
public class ConsoleSmsServiceImpl implements SmsService {
    @Override
    public void sendCode(String phone, String code) {
        System.out.println("============================================");
        System.out.println("  验证码发送到: " + phone);
        System.out.println("  验证码: " + code);
        System.out.println("============================================");
    }
}
