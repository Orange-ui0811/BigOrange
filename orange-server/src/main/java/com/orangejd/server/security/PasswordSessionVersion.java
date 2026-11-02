package com.orangejd.server.security;

import com.orangejd.pojo.entity.Account;
import io.jsonwebtoken.Claims;

public final class PasswordSessionVersion {

    private PasswordSessionVersion() {
    }

    public static boolean matches(Claims claims, Account account) {
        long currentVersion = account.getPasswordVersion() == null ? 0L : account.getPasswordVersion();
        Object claim = claims.get("passwordVersion");
        long tokenVersion = claim == null ? 0L : Long.parseLong(claim.toString());
        return tokenVersion == currentVersion;
    }
}
