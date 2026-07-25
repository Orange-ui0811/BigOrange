package com.orangejd.server.security;

import java.util.List;

public final class ApiRoutePolicy {
    private ApiRoutePolicy() {}

    public static final List<String> APP_PUBLIC_PATHS = List.of(
            "/app/auth/login",
            "/app/auth/register",
            "/app/auth/send-code",
            "/app/auth/send-reset-code",
            "/app/auth/reset-password",
            "/app/category",
            "/app/work/public",
            "/app/requirement/public",
            "/app/config/trade-mode",
            "/app/config/rewards",
            "/app/leaderboard/**",
            "/app/trend/graph",
            "/ws/**"
    );

    public static final List<String> ADMIN_PUBLIC_PATHS = List.of("/admin/auth/login");
    public static final List<String> AGENT_PUBLIC_PATHS = List.of("/agent/connect/exchange");
}
