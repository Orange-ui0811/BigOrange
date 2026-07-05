package com.orangejd.server.service;

/**
 * 邮箱验证码发送服务
 */
public interface MailService {
    void sendCode(String toEmail, String code);
}
