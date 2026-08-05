package com.orangejd.server.service;

import com.orangejd.server.dto.PersonaSettingsRequest;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PersonaSettingsService {
    static final Map<String, Double> FACE_DEFAULTS = Map.ofEntries(
            Map.entry("bodyWidth", 1.0), Map.entry("bodyHeight", 1.0),
            Map.entry("eyeSize", 1.0), Map.entry("eyeSpacing", 1.0),
            Map.entry("eyeHeight", 0.0), Map.entry("pupilSize", 1.0),
            Map.entry("browLength", 1.0), Map.entry("browThickness", 1.0),
            Map.entry("browHeight", 0.0), Map.entry("browAngle", 0.0),
            Map.entry("mouthWidth", 1.0), Map.entry("mouthCurve", 1.0),
            Map.entry("mouthHeight", 0.0), Map.entry("mouthThickness", 1.0),
            Map.entry("mouthOpen", 0.0));
    private static final Map<String, double[]> FACE_RANGES = Map.ofEntries(
            Map.entry("bodyWidth", new double[]{0.82, 1.2}), Map.entry("bodyHeight", new double[]{0.84, 1.16}),
            Map.entry("eyeSize", new double[]{0.68, 1.4}), Map.entry("eyeSpacing", new double[]{0.72, 1.32}),
            Map.entry("eyeHeight", new double[]{-0.22, 0.24}), Map.entry("pupilSize", new double[]{0.62, 1.35}),
            Map.entry("browLength", new double[]{0.65, 1.4}), Map.entry("browThickness", new double[]{0.65, 1.45}),
            Map.entry("browHeight", new double[]{-0.16, 0.24}), Map.entry("browAngle", new double[]{-0.48, 0.48}),
            Map.entry("mouthWidth", new double[]{0.62, 1.45}), Map.entry("mouthCurve", new double[]{-1.15, 1.25}),
            Map.entry("mouthHeight", new double[]{-0.22, 0.2}), Map.entry("mouthThickness", new double[]{0.65, 1.5}),
            Map.entry("mouthOpen", new double[]{0.0, 1.0}));

    private final PersonaWorkshopMapper mapper;

    public PersonaSettingsService(PersonaWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> load(Long accountId) {
        Map<String, Object> saved = mapper.selectSettings(accountId);
        if (saved == null) return defaults();
        Map<String, Object> settings = new LinkedHashMap<>(saved);
        settings.put("faceConfig", PersonaValueSupport.config(settings.get("faceConfig"), FACE_DEFAULTS));
        return settings;
    }

    public Map<String, Object> appearance(Map<String, Object> settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bodyColor", value(null, settings, "bodyColor", "#F37021"));
        result.put("faceStyle", value(null, settings, "faceStyle", "FRIENDLY"));
        result.put("leafStyle", value(null, settings, "leafStyle", "CLASSIC"));
        result.put("surfaceStyle", value(null, settings, "surfaceStyle", "NATURAL"));
        result.put("faceConfig", PersonaValueSupport.config(settings.get("faceConfig"), FACE_DEFAULTS));
        return result;
    }

    public Map<String, Object> update(Long accountId, PersonaSettingsRequest request) {
        Map<String, Object> current = load(accountId);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("accountId", accountId);
        settings.put("showcaseEnabled", request.showcaseEnabled());
        settings.put("avatarEnabled", request.showcaseEnabled() && request.avatarEnabled());
        settings.put("bodyColor", value(request.bodyColor(), current, "bodyColor", "#F37021"));
        settings.put("faceStyle", value(request.faceStyle(), current, "faceStyle", "FRIENDLY"));
        settings.put("leafStyle", value(request.leafStyle(), current, "leafStyle", "CLASSIC"));
        settings.put("surfaceStyle", value(request.surfaceStyle(), current, "surfaceStyle", "NATURAL"));
        Map<String, Double> face = PersonaValueSupport.validatedConfig(request.faceConfig(),
                PersonaValueSupport.config(current.get("faceConfig"), FACE_DEFAULTS), FACE_RANGES,
                "五官调整参数无效");
        settings.put("faceConfigJson", PersonaValueSupport.json(face, "DIY 参数保存失败"));
        mapper.upsertSettings(settings);
        return load(accountId);
    }

    private Map<String, Object> defaults() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("showcaseEnabled", false);
        result.put("avatarEnabled", false);
        result.put("bodyColor", "#F37021");
        result.put("faceStyle", "FRIENDLY");
        result.put("leafStyle", "CLASSIC");
        result.put("surfaceStyle", "NATURAL");
        result.put("faceConfig", new LinkedHashMap<>(FACE_DEFAULTS));
        return result;
    }

    private String value(String requested, Map<String, Object> current, String key, String fallback) {
        if (requested != null && !requested.isBlank()) return requested.trim().toUpperCase(Locale.ROOT);
        Object saved = current.get(key);
        return saved == null || String.valueOf(saved).isBlank() ? fallback : String.valueOf(saved);
    }
}
