package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class EmailVerificationCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long CODE_TTL_MINUTES = 5;
    private static final long SEND_COOLDOWN_SECONDS = 60;
    private static final String REGISTRATION_CODE_PREFIX = "mail:code:";
    private static final String REGISTRATION_LIMIT_PREFIX = "mail:limit:";
    private static final String RESET_CODE_PREFIX = "mail:reset:code:";
    private static final String RESET_LIMIT_PREFIX = "mail:reset:limit:";

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;

    public EmailVerificationCodeService(StringRedisTemplate redisTemplate, MailService mailService) {
        this.redisTemplate = redisTemplate;
        this.mailService = mailService;
    }

    public void sendRegistrationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        send(normalizedEmail, REGISTRATION_CODE_PREFIX + normalizedEmail,
                REGISTRATION_LIMIT_PREFIX + normalizedEmail);
    }

    public void sendPasswordResetCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        send(normalizedEmail, RESET_CODE_PREFIX + normalizedEmail,
                RESET_LIMIT_PREFIX + normalizedEmail);
    }

    public void verifyAndConsumeRegistrationCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        verifyAndConsume(REGISTRATION_CODE_PREFIX + normalizedEmail,
                REGISTRATION_LIMIT_PREFIX + normalizedEmail, code);
    }

    public void verifyAndConsumePasswordResetCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        verifyAndConsume(RESET_CODE_PREFIX + normalizedEmail,
                RESET_LIMIT_PREFIX + normalizedEmail, code);
    }

    private void send(String email, String codeKey, String limitKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BaseException("验证码已发送，请60秒后再试");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        try {
            redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            mailService.sendCode(email, code);
        } catch (RuntimeException e) {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(limitKey);
            throw e;
        }
    }

    private void verifyAndConsume(String codeKey, String limitKey, String submittedCode) {
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null) {
            throw new BaseException("验证码已过期，请重新获取");
        }
        String normalizedCode = submittedCode == null ? "" : submittedCode.trim();
        if (!cachedCode.equals(normalizedCode)) {
            throw new BaseException("验证码错误");
        }
        redisTemplate.delete(codeKey);
        redisTemplate.delete(limitKey);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BaseException("邮箱格式不正确");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
