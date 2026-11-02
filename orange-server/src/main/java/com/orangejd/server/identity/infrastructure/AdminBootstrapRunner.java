package com.orangejd.server.identity.infrastructure;

import com.orangejd.pojo.entity.Account;
import com.orangejd.server.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrapRunner(AccountMapper accountMapper,
                                PasswordEncoder passwordEncoder,
                                @Value("${orange.bootstrap-admin.username:}") String username,
                                @Value("${orange.bootstrap-admin.password:}") String password) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountMapper.countActiveAdmins() > 0) return;
        if (username.isBlank() && password.isBlank()) {
            log.warn("No active administrator exists. Set ORANGE_ADMIN_BOOTSTRAP_USERNAME and "
                    + "ORANGE_ADMIN_BOOTSTRAP_PASSWORD for the first startup.");
            return;
        }
        if (username.length() < 3 || password.length() < 12) {
            throw new IllegalStateException("Bootstrap administrator requires a username of at least 3 "
                    + "characters and a password of at least 12 characters");
        }
        if (accountMapper.selectByUsername(username) != null) {
            throw new IllegalStateException("Bootstrap administrator username already exists");
        }

        Account admin = new Account();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setStatus("ACTIVE");
        admin.setRole("ADMIN");
        admin.setLegalVersion("bootstrap");
        accountMapper.insert(admin);
        log.warn("Created the initial administrator account '{}'. Remove the bootstrap password "
                + "from the environment before the next startup.", username);
    }
}
