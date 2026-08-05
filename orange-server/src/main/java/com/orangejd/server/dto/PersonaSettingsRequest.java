package com.orangejd.server.dto;

import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record PersonaSettingsRequest(
        boolean showcaseEnabled,
        boolean avatarEnabled,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "橙子颜色格式无效") String bodyColor,
        @Pattern(regexp = "^(FRIENDLY|CURIOUS|COOL)$", message = "橙子表情无效") String faceStyle,
        @Pattern(regexp = "^(CLASSIC|DOUBLE|SPROUT)$", message = "叶子造型无效") String leafStyle,
        @Pattern(regexp = "^(NATURAL|GLOSSY|MATTE)$", message = "橙子质感无效") String surfaceStyle,
        Map<String, Double> faceConfig,
        Map<String, Double> handConfig) {

    public PersonaSettingsRequest(boolean showcaseEnabled, boolean avatarEnabled) {
        this(showcaseEnabled, avatarEnabled, null, null, null, null, null, null);
    }

    public PersonaSettingsRequest(boolean showcaseEnabled, boolean avatarEnabled, String bodyColor,
                                  String faceStyle, String leafStyle, String surfaceStyle) {
        this(showcaseEnabled, avatarEnabled, bodyColor, faceStyle, leafStyle, surfaceStyle, null, null);
    }

    public PersonaSettingsRequest(boolean showcaseEnabled, boolean avatarEnabled, String bodyColor,
                                  String faceStyle, String leafStyle, String surfaceStyle,
                                  Map<String, Double> faceConfig) {
        this(showcaseEnabled, avatarEnabled, bodyColor, faceStyle, leafStyle, surfaceStyle, faceConfig, null);
    }
}
