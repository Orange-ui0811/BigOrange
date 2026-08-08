package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orangejd.common.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public record QuotaRequestIdentity(String scope, String requestId,
                                   String idempotencyKeyHash, String requestHash) {
    private static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{16,128}");
    private static final Pattern SCOPE_PATTERN = Pattern.compile("[a-z0-9:._-]{1,64}");
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    public static QuotaRequestIdentity current(String scope, Long accountId, Object payload) {
        String normalizedScope = normalizeScope(scope);
        String requestHash = sha256(canonical(payload));
        String key = currentHeader();
        if (key == null || key.isBlank()) {
            return new QuotaRequestIdentity(normalizedScope,
                    normalizedScope.replace(':', '_') + "_" + UUID.randomUUID(),
                    null, requestHash);
        }
        String normalizedKey = key.trim();
        if (!KEY_PATTERN.matcher(normalizedKey).matches()) {
            throw new BaseException("幂等键格式无效");
        }
        String keyHash = sha256(normalizedKey);
        String requestId = "idem_" + sha256(normalizedScope + "\n" + accountId + "\n" + keyHash);
        return new QuotaRequestIdentity(normalizedScope, requestId, keyHash, requestHash);
    }

    public static QuotaRequestIdentity legacy(String scope, String requestId) {
        String normalizedScope = normalizeScope(scope);
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? normalizedScope.replace(':', '_') + "_" + UUID.randomUUID()
                : requestId.trim();
        if (normalizedRequestId.length() > 128) throw new BaseException("配额请求号过长");
        return new QuotaRequestIdentity(normalizedScope, normalizedRequestId, null,
                sha256(normalizedRequestId));
    }

    private static String currentHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request == null ? null : request.getHeader(HEADER);
    }

    private static String normalizeScope(String scope) {
        String value = scope == null ? "quota:unknown" : scope.trim().toLowerCase(Locale.ROOT);
        if (!SCOPE_PATTERN.matcher(value).matches()) throw new BaseException("配额业务范围无效");
        return value;
    }

    private static String canonical(Object payload) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(sort(CANONICAL_MAPPER.valueToTree(payload)));
        } catch (Exception exception) {
            throw new BaseException("配额请求摘要生成失败");
        }
    }

    private static JsonNode sort(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) return node;
        if (node.isArray()) {
            ArrayNode result = CANONICAL_MAPPER.createArrayNode();
            node.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = CANONICAL_MAPPER.createObjectNode();
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        names.forEach(name -> result.set(name, sort(node.get(name))));
        return result;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
