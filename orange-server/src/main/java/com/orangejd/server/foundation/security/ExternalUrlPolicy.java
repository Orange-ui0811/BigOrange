package com.orangejd.server.foundation.security;

import com.orangejd.common.exception.BaseException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates user-provided external links without resolving or requesting them. */
@Component
public class ExternalUrlPolicy {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern DNS_NAME = Pattern.compile("^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern IPV4 = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");
    private static final Set<String> FORBIDDEN_SUFFIXES = Set.of(
            ".local", ".localhost", ".internal", ".lan", ".home", ".test", ".invalid");

    public String requireSafeOptional(String fieldName, String rawValue) {
        if (rawValue == null) return null;
        String value = rawValue.trim();
        if (value.isEmpty()) return "";
        if (!isSafe(value)) {
            throw new BaseException(fieldName + "仅支持安全的 HTTPS 公网地址");
        }
        return value;
    }

    public boolean isSafe(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.length() > MAX_URL_LENGTH) return false;
        try {
            URI uri = URI.create(rawValue.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;
            host = stripIpv6Brackets(host.toLowerCase(Locale.ROOT));
            if (host.startsWith("xn--") || host.contains(".xn--")) return false;
            for (String suffix : FORBIDDEN_SUFFIXES) {
                if (host.equals(suffix.substring(1)) || host.endsWith(suffix)) return false;
            }
            if (host.indexOf(':') >= 0) return isPublicIpv6(host);
            if (IPV4.matcher(host).matches()) return isPublicIpv4(host);
            return DNS_NAME.matcher(host).matches();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private boolean isPublicIpv4(String host) {
        String[] parts = host.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (octets[i] > 255) return false;
        }
        int a = octets[0], b = octets[1];
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 169 && b == 254)
                && !(a == 172 && b >= 16 && b <= 31)
                && !(a == 192 && b == 0)
                && !(a == 192 && b == 168)
                && !(a == 198 && (b == 18 || b == 19))
                && !(a == 198 && b == 51 && octets[2] == 100)
                && !(a == 203 && b == 0 && octets[2] == 113);
    }

    private boolean isPublicIpv6(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("::") || normalized.equals("::1") || normalized.startsWith("::ffff:")) return false;
        if (normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("ff")) return false;
        if (normalized.startsWith("2001:db8") || normalized.startsWith("2001:2:")) return false;
        return !(normalized.startsWith("fe8") || normalized.startsWith("fe9")
                || normalized.startsWith("fea") || normalized.startsWith("feb"));
    }
}
