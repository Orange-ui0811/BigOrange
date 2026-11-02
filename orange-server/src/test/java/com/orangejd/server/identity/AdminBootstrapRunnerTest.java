package com.orangejd.server.identity;

import com.orangejd.pojo.entity.Account;
import com.orangejd.server.identity.infrastructure.AdminBootstrapRunner;
import com.orangejd.server.mapper.AccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {
    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void doesNothingWhenAnActiveAdministratorExists() {
        AccountMapper mapper = mock(AccountMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.countActiveAdmins()).thenReturn(1);

        new AdminBootstrapRunner(mapper, encoder, "owner", "strong-password")
                .run(NO_ARGS);

        verify(mapper, never()).insert(any());
    }

    @Test
    void createsInitialAdministratorOnlyFromExplicitBootstrapCredentials() {
        AccountMapper mapper = mock(AccountMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(mapper.countActiveAdmins()).thenReturn(0);
        when(mapper.selectByUsername("owner")).thenReturn(null);
        when(encoder.encode("strong-password")).thenReturn("bcrypt-value");

        new AdminBootstrapRunner(mapper, encoder, "owner", "strong-password")
                .run(NO_ARGS);

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(mapper).insert(account.capture());
        assertEquals("owner", account.getValue().getUsername());
        assertEquals("bcrypt-value", account.getValue().getPassword());
        assertEquals("ADMIN", account.getValue().getRole());
        assertEquals("ACTIVE", account.getValue().getStatus());
    }
}
