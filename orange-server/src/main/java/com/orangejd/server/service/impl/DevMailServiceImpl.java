package com.orangejd.server.service.impl;

import com.orangejd.server.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 仅供本地开发使用：不依赖 SMTP，将验证码写入后端日志。
 * 生产环境不会加载此实现，避免验证码泄露到线上日志。
 */
@Slf4j
@Service
@Profile("dev")
public class DevMailServiceImpl implements MailService {

    @Override
    public void sendCode(String toEmail, String code) {
        log.warn("[仅本地开发] 邮箱验证码 email={} code={}", maskEmail(toEmail), code);
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
