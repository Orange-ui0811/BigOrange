package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class EmailVerificationCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long CODE_TTL_MINUTES = 5;
    private static final long SEND_COOLDOWN_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;

    public EmailVerificationCodeService(StringRedisTemplate redisTemplate, MailService mailService) {
        this.redisTemplate = redisTemplate;
        this.mailService = mailService;
    }

    public void sendRegistrationCode(String email) {
        send(email, "mail:code:" + email, "mail:limit:" + email);
    }

    public void sendPasswordResetCode(String email) {
        send(email, "mail:reset:code:" + email, "mail:reset:limit:" + email);
    }

    private void send(String email, String codeKey, String limitKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BaseException("验证码已发送，请60秒后再试");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        try {
            mailService.sendCode(email, code);
            redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            redisTemplate.delete(limitKey);
            throw e;
        }
    }
}
