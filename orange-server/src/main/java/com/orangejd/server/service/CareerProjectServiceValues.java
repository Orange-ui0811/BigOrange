package com.orangejd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;

public final class CareerProjectServiceValues {
    public static String text(JsonNode node, String field) { return node.path(field).asText("").trim(); }

    public static int value(Integer value) { return value == null ? 0 : value; }

    public static String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    public static String safe(String value) { return value == null ? "" : value; }

    public static String abbreviate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }

    public static LocalDate currentWeekStart() { return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    public static String json(ObjectMapper objectMapper, Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("职业项目数据无法序列化", e); }
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public static String roleName(CareerRoleTaxonomy taxonomy, String code) {
        return taxonomy.categories().stream().filter(item -> item.code().equals(code))
                .map(CareerRoleTaxonomy.RoleCategory::name).findFirst().orElse("未分类");
    }
}
