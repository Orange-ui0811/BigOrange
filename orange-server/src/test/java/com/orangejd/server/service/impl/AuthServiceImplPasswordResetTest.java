package com.orangejd.server.service.impl;

import com.orangejd.common.exception.BaseException;
import com.orangejd.common.properties.JwtProperties;
import com.orangejd.pojo.entity.Account;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.service.EmailVerificationCodeService;
import com.orangejd.server.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplPasswordResetTest {

    @Mock private AccountMapper accountMapper;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private QuotaService quotaService;
    @Mock private EmailVerificationCodeService verificationCodeService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(accountMapper, userProfileMapper, passwordEncoder,
                new JwtProperties(), quotaService, verificationCodeService);
    }

    @Test
    void invalidNewPasswordDoesNotConsumeVerificationCode() {
        assertThrows(BaseException.class,
                () -> authService.resetPassword("user@example.com", "012345", "short"));

        verify(verificationCodeService, never())
                .verifyAndConsumePasswordResetCode("user@example.com", "012345");
        verify(accountMapper, never()).updatePassword(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validResetConsumesCodeAndUpdatesPassword() {
        Account account = new Account();
        account.setId(7L);
        when(accountMapper.selectByEmail("user@example.com")).thenReturn(account);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded-password");

        authService.resetPassword(" USER@EXAMPLE.COM ", "012345", "NewPassword123");

        verify(verificationCodeService)
                .verifyAndConsumePasswordResetCode("user@example.com", "012345");
        verify(accountMapper).updatePassword(7L, "encoded-password");
    }
}
