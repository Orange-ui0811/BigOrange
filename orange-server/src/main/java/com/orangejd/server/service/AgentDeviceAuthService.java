package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.AgentDeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentDeviceAuthService {

    public static final String SCOPES = String.join(",",
            "quota.balance.read",
            "launch_check.project.list",
            "launch_check.project.create",
            "launch_check.stage.generate",
            "open_source.issue.list",
            "open_source.issue.detail",
            "open_source.issue.job_match",
            "open_source.issue.guide",
            "trend.analysis.generate",
            "trend.profile.generate",
            "plan.phase.generate",
            "agent.task.list",
            "notification.unread.read",
            "leaderboard.github.list");

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentDeviceMapper mapper;

    public AgentDeviceAuthService(AgentDeviceMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> createLinkCode(Long accountId, String requestedName) {
        String deviceName = normalizeDeviceName(requestedName);
        String code = randomCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("accountId", accountId);
        record.put("codeHash", hash(normalizeCode(code)));
        record.put("deviceName", deviceName);
        record.put("expiresAt", expiresAt);
        mapper.insertLinkCode(record);
        return Map.of("code", code.substring(0, 4) + "-" + code.substring(4),
                "deviceName", deviceName, "expiresAt", expiresAt, "expiresInSeconds", 600);
    }

    @Transactional
    public Map<String, Object> exchange(String suppliedCode, String requestedName) {
        String code = normalizeCode(suppliedCode);
        if (code.length() != 8) throw new BaseException("绑定码无效或已过期");
        Map<String, Object> link = mapper.selectUsableLinkCode(hash(code));
        if (link == null || mapper.consumeLinkCode(((Number) link.get("id")).longValue()) != 1) {
            throw new BaseException("绑定码无效或已过期");
        }

        String token = "ojd_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes(32));
        String deviceName = StringUtils.hasText(requestedName)
                ? normalizeDeviceName(requestedName)
                : normalizeDeviceName(String.valueOf(link.get("deviceName")));
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(90);
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("accountId", ((Number) link.get("accountId")).longValue());
        credential.put("deviceName", deviceName);
        credential.put("tokenHash", hash(token));
        credential.put("tokenHint", token.substring(token.length() - 8));
        credential.put("scopes", SCOPES);
        credential.put("expiresAt", expiresAt);
        mapper.insertCredential(credential);
        return Map.of("deviceId", credential.get("id"), "deviceName", deviceName,
                "token", token, "scopes", List.of(SCOPES.split(",")), "expiresAt", expiresAt);
    }

    public Map<String, Object> authenticate(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BaseException("设备令牌无效");
        }
        String token = authorization.substring(7).trim();
        if (!token.startsWith("ojd_") || token.length() < 30) throw new BaseException("设备令牌无效");
        Map<String, Object> credential = mapper.selectActiveCredential(hash(token));
        if (credential == null) throw new BaseException("设备令牌无效或已撤销");
        mapper.touchCredential(((Number) credential.get("id")).longValue());
        return credential;
    }

    public List<Map<String, Object>> list(Long accountId) {
        return mapper.selectCredentialsByAccount(accountId);
    }

    public void revoke(Long accountId, Long deviceId) {
        if (mapper.revokeCredential(deviceId, accountId) != 1) throw new BaseException("设备不存在或已撤销");
    }

    private String normalizeDeviceName(String value) {
        String name = StringUtils.hasText(value) ? value.trim() : "小智客户端";
        if (name.length() > 64) throw new BaseException("设备名称过长");
        return name;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    private String randomCode() {
        StringBuilder result = new StringBuilder(8);
        for (int i = 0; i < 8; i++) result.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return result.toString();
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
