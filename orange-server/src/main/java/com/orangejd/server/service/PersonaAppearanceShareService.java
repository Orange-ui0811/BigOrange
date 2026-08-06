package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.dto.PersonaSettingsRequest;
import com.orangejd.server.mapper.PersonaAppearanceShareMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PersonaAppearanceShareService {
    private static final int SCHEMA_VERSION = 1;
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonaAppearanceShareMapper shareMapper;
    private final PersonaWorkshopMapper workshopMapper;
    private final PersonaSettingsService settingsService;
    private final PersonaEquipmentService equipmentService;
    private final ObjectMapper objectMapper;

    public PersonaAppearanceShareService(PersonaAppearanceShareMapper shareMapper,
                                         PersonaWorkshopMapper workshopMapper,
                                         PersonaSettingsService settingsService,
                                         PersonaEquipmentService equipmentService,
                                         ObjectMapper objectMapper) {
        this.shareMapper = shareMapper;
        this.workshopMapper = workshopMapper;
        this.settingsService = settingsService;
        this.equipmentService = equipmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> exportCode(Long accountId) {
        Set<PersonaDimension> completed = requireComplete(accountId);
        Map<String, Object> payload = visualPayload(accountId, completed);
        String payloadJson = json(payload);
        String payloadHash = sha256(payloadJson);
        Map<String, Object> active = shareMapper.selectActiveByOwner(accountId);
        if (active != null && payloadHash.equals(active.get("payloadHash"))) return codeView(active);

        shareMapper.revokeActiveByOwner(accountId);
        for (int attempt = 0; attempt < 5; attempt++) {
            Map<String, Object> share = new LinkedHashMap<>();
            share.put("code", nextCode());
            share.put("ownerAccountId", accountId);
            share.put("schemaVersion", SCHEMA_VERSION);
            share.put("payloadJson", payloadJson);
            share.put("payloadHash", payloadHash);
            if (shareMapper.insert(share) == 1) return codeView(share);
        }
        throw new BaseException("形象码生成失败，请稍后重试");
    }

    public Map<String, Object> currentCode(Long accountId) {
        Set<PersonaDimension> completed = completedDimensions(accountId);
        if (completed.size() != PersonaDimension.values().length) return null;
        Map<String, Object> active = shareMapper.selectActiveByOwner(accountId);
        if (active == null) return null;
        String currentHash = sha256(json(visualPayload(accountId, completed)));
        return currentHash.equals(active.get("payloadHash")) ? codeView(active) : null;
    }

    public Map<String, Object> preview(Long accountId, String requestedCode) {
        String code = normalizeCode(requestedCode);
        Map<String, Object> share = requireShare(code);
        Map<String, Object> payload = payload(share);
        Set<PersonaDimension> completed = completedDimensions(accountId);
        List<Map<String, Object>> equipment = equipment(payload);
        List<String> blocked = new ArrayList<>();
        for (Map<String, Object> item : equipment) {
            PersonaVisualLanguage.EquipmentDefinition definition =
                    PersonaVisualLanguage.requireSlot(String.valueOf(item.get("slotCode")));
            boolean available = completed.contains(definition.dimension());
            item.put("available", available);
            item.put("name", definition.name());
            if (!available && PersonaValueSupport.bool(item.get("equipped"))) blocked.add(definition.name());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("schemaVersion", share.get("schemaVersion"));
        result.put("appearance", object(payload.get("appearance")));
        result.put("equipment", equipment);
        result.put("blockedEquipment", blocked);
        result.put("readyToImport", completed.size() == PersonaDimension.values().length);
        return result;
    }

    @Transactional
    public Map<String, Object> apply(Long accountId, String requestedCode) {
        Set<PersonaDimension> completed = requireComplete(accountId);
        String code = normalizeCode(requestedCode);
        Map<String, Object> share = requireShare(code);
        Map<String, Object> payload = payload(share);
        applyAppearance(accountId, object(payload.get("appearance")));

        int imported = 0;
        int skipped = 0;
        for (Map<String, Object> item : equipment(payload)) {
            PersonaVisualLanguage.EquipmentDefinition definition =
                    PersonaVisualLanguage.requireSlot(String.valueOf(item.get("slotCode")));
            if (!completed.contains(definition.dimension())) {
                skipped++;
                continue;
            }
            applyEquipment(accountId, definition, item);
            imported++;
        }
        shareMapper.recordImport(code);
        return Map.of("code", code, "importedEquipment", imported, "skippedEquipment", skipped);
    }

    private void applyAppearance(Long accountId, Map<String, Object> appearance) {
        if (appearance == null) throw new BaseException("形象码缺少外观数据，请重新生成");
        Map<String, Object> current = settingsService.load(accountId);
        PersonaSettingsRequest request = new PersonaSettingsRequest(
                PersonaValueSupport.bool(current.get("showcaseEnabled")),
                PersonaValueSupport.bool(current.get("avatarEnabled")),
                string(appearance, "bodyColor"), string(appearance, "faceStyle"),
                string(appearance, "leafStyle"), string(appearance, "surfaceStyle"),
                doubles(appearance.get("faceConfig")), doubles(appearance.get("handConfig")));
        settingsService.update(accountId, request);
    }

    private void applyEquipment(Long accountId, PersonaVisualLanguage.EquipmentDefinition definition,
                                Map<String, Object> item) {
        String itemCode = String.valueOf(item.get("itemCode"));
        String variantCode = String.valueOf(item.get("variantCode"));
        String colorHex = String.valueOf(item.get("colorHex"));
        PersonaEquipmentRequest request = new PersonaEquipmentRequest(itemCode, variantCode, colorHex,
                PersonaValueSupport.bool(item.get("equipped")), doubles(item.get("transform")));
        equipmentService.update(accountId, definition.slotCode(), request);
        Map<String, Object> blueprint = object(item.get("blueprint"));
        if (blueprint != null) {
            PersonaAccessoryBlueprintRequest blueprintRequest =
                    objectMapper.convertValue(blueprint, PersonaAccessoryBlueprintRequest.class);
            equipmentService.updateBlueprint(accountId, definition.slotCode(), blueprintRequest);
        }
    }

    private List<Map<String, Object>> shareableEquipment(List<Map<String, Object>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            Map<String, Object> shared = new LinkedHashMap<>();
            for (String key : List.of("slotCode", "itemCode", "variantCode", "colorHex", "equipped",
                    "transform", "blueprint")) shared.put(key, item.get(key));
            result.add(shared);
        }
        return result;
    }

    private Map<String, Object> visualPayload(Long accountId, Set<PersonaDimension> completed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("appearance", settingsService.appearance(settingsService.load(accountId)));
        payload.put("equipment", shareableEquipment(equipmentService.view(accountId, completed)));
        return payload;
    }

    private Set<PersonaDimension> requireComplete(Long accountId) {
        Set<PersonaDimension> completed = completedDimensions(accountId);
        if (completed.size() != PersonaDimension.values().length)
            throw new BaseException("请先完成全部画像维度，再使用形象码");
        return completed;
    }

    private Set<PersonaDimension> completedDimensions(Long accountId) {
        Set<PersonaDimension> completed = EnumSet.noneOf(PersonaDimension.class);
        for (Map<String, Object> entry : workshopMapper.selectEntries(accountId)) {
            completed.add(PersonaDimension.parse(String.valueOf(entry.get("dimensionCode"))));
        }
        return completed;
    }

    private Map<String, Object> requireShare(String code) {
        Map<String, Object> share = shareMapper.selectActiveByCode(code);
        if (share == null) throw new BaseException("形象码不存在或已失效");
        Number version = (Number) share.get("schemaVersion");
        if (version == null || version.intValue() != SCHEMA_VERSION)
            throw new BaseException("该形象码版本暂不支持");
        return share;
    }

    private Map<String, Object> payload(Map<String, Object> share) {
        Object raw = share.get("payloadJson");
        try {
            if (raw instanceof Map<?, ?> map) return objectMapper.convertValue(map, new TypeReference<>() {});
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception error) {
            throw new BaseException("形象码内容损坏，请重新生成");
        }
    }

    private List<Map<String, Object>> equipment(Map<String, Object> payload) {
        Object raw = payload.get("equipment");
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> converted = objectMapper.convertValue(item,
                    new TypeReference<Map<String, Object>>() {});
            result.add(new LinkedHashMap<>(converted));
        }
        return result;
    }

    private Map<String, Object> object(Object raw) {
        if (raw == null) return null;
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private Map<String, Double> doubles(Object raw) {
        if (raw == null) return null;
        return objectMapper.convertValue(raw, new TypeReference<>() {});
    }

    private String string(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BaseException("橙子形象整理失败，请稍后重试");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String nextCode() {
        StringBuilder value = new StringBuilder("OVC-");
        for (int index = 0; index < 8; index++) {
            if (index == 4) value.append('-');
            value.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return value.toString();
    }

    private String normalizeCode(String requested) {
        String code = requested == null ? "" : requested.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!code.matches("^OVC-[A-Z2-9]{4}-[A-Z2-9]{4}$")) throw new BaseException("请输入正确的橙子形象码");
        return code;
    }

    private Map<String, Object> codeView(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", source.get("code"));
        result.put("schemaVersion", source.getOrDefault("schemaVersion", SCHEMA_VERSION));
        result.put("createTime", source.get("createTime"));
        return result;
    }
}
