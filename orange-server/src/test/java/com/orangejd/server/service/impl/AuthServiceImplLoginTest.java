package com.orangejd.server.service.impl;

import com.orangejd.common.properties.JwtProperties;
import com.orangejd.pojo.dto.LoginDTO;
import com.orangejd.pojo.entity.Account;
import com.orangejd.pojo.vo.LoginVO;
import com.orangejd.server.mapper.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplLoginTest {

    @Mock private AccountMapper accountMapper;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setAppSecretKey("app-login-test-secret-key-at-least-32-characters");
        jwtProperties.setAppTtl(3_600_000L);
        authService = new AuthServiceImpl(accountMapper,
                org.mockito.Mockito.mock(com.orangejd.server.mapper.UserProfileMapper.class),
                passwordEncoder, jwtProperties,
                org.mockito.Mockito.mock(com.orangejd.server.service.QuotaService.class),
                org.mockito.Mockito.mock(com.orangejd.server.service.EmailVerificationCodeService.class));
    }

    @Test
    void logsInWithNormalizedEmailWhenUsernameDoesNotMatch() {
        Account account = activeAccount();
        when(accountMapper.selectByEmail("user@example.com")).thenReturn(account);
        when(passwordEncoder.matches("Password123", account.getPassword())).thenReturn(true);

        LoginVO result = authService.appLogin(login("  USER@EXAMPLE.COM  "));

        assertNotNull(result.getToken());
        assertEquals("member", result.getUsername());
        verify(accountMapper).selectByUsername("USER@EXAMPLE.COM");
        verify(accountMapper).selectByEmail("user@example.com");
    }

    @Test
    void logsInWithTrimmedUsernameWithoutEmailLookup() {
        Account account = activeAccount();
        when(accountMapper.selectByUsername("member")).thenReturn(account);
        when(passwordEncoder.matches("Password123", account.getPassword())).thenReturn(true);

        LoginVO result = authService.appLogin(login("  member  "));

        assertNotNull(result.getToken());
        verify(accountMapper).selectByUsername("member");
        verify(accountMapper, never()).selectByEmail("member");
    }

    private LoginDTO login(String identity) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(identity);
        dto.setPassword("Password123");
        return dto;
    }

    private Account activeAccount() {
        Account account = new Account();
        account.setId(7L);
        account.setUsername("member");
        account.setPassword("$2a$10$encoded");
        account.setPasswordVersion(1L);
        account.setRole("USER");
        account.setStatus("ACTIVE");
        return account;
    }
}
