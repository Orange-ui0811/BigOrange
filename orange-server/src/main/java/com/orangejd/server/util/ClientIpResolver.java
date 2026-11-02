package com.orangejd.server.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {
    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        // Only the same-host reverse proxy may supply the canonical client address.
        if ("127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote)) {
            String realIp = request.getHeader("X-Real-IP");
            if (isIpLiteral(realIp)) return realIp.trim();
        }
        return remote == null ? "unknown" : remote;
    }

    private static boolean isIpLiteral(String value) {
        if (value == null || value.length() > 45) return false;
        return value.matches("^[0-9a-fA-F:.]+$");
    }
}
