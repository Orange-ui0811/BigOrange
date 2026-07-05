package com.orangejd.server.service.impl;

import com.orangejd.server.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpMailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public SmtpMailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendCode(String toEmail, String code) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("OrangeJD 注册验证码");
            msg.setText("您的验证码是：" + code + "，5 分钟内有效。请勿泄露给他人。");
            mailSender.send(msg);
            log.info("验证码已发送到: {}", toEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
            throw new RuntimeException("邮件发送失败，请检查邮箱地址");
        }
    }
}
