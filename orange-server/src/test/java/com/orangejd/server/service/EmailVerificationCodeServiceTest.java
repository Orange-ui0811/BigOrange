package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationCodeServiceTest {

    @Test
    void concurrentRegistrationRequestsSendOnlyOneEmail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        MailService mail = mock(MailService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("mail:limit:user@example.com", "1", 60, TimeUnit.SECONDS))
                .thenReturn(true, false);
        EmailVerificationCodeService service = new EmailVerificationCodeService(redis, mail);

        service.sendRegistrationCode("user@example.com");
        assertThrows(BaseException.class, () -> service.sendRegistrationCode("user@example.com"));

        verify(mail, times(1)).sendCode(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                matches("\\d{6}"));
        org.mockito.InOrder order = inOrder(values, mail);
        order.verify(values).set(
                org.mockito.ArgumentMatchers.eq("mail:code:user@example.com"),
                matches("\\d{6}"),
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MINUTES));
        order.verify(mail).sendCode(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                matches("\\d{6}"));
    }

    @Test
    void releasesCooldownWhenMailDeliveryFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        MailService mail = mock(MailService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("mail:reset:limit:user@example.com", "1", 60, TimeUnit.SECONDS))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP unavailable"))
                .when(mail).sendCode(
                        org.mockito.ArgumentMatchers.eq("user@example.com"),
                        matches("\\d{6}"));
        EmailVerificationCodeService service = new EmailVerificationCodeService(redis, mail);

        assertThrows(RuntimeException.class, () -> service.sendPasswordResetCode("user@example.com"));

        verify(redis).delete("mail:reset:code:user@example.com");
        verify(redis).delete("mail:reset:limit:user@example.com");
    }

    @Test
    void verifiesPasswordResetCodeUsingNormalizedEmailAndConsumesIt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("mail:reset:code:user@example.com")).thenReturn("012345");
        EmailVerificationCodeService service = new EmailVerificationCodeService(redis, mock(MailService.class));

        service.verifyAndConsumePasswordResetCode("  USER@EXAMPLE.COM ", " 012345 ");

        verify(redis).delete("mail:reset:code:user@example.com");
        verify(redis).delete("mail:reset:limit:user@example.com");
    }

    @Test
    void wrongPasswordResetCodeIsNotConsumed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("mail:reset:code:user@example.com")).thenReturn("012345");
        EmailVerificationCodeService service = new EmailVerificationCodeService(redis, mock(MailService.class));

        assertThrows(BaseException.class,
                () -> service.verifyAndConsumePasswordResetCode("user@example.com", "654321"));

        org.mockito.Mockito.verify(redis, org.mockito.Mockito.never())
                .delete("mail:reset:code:user@example.com");
    }
}
