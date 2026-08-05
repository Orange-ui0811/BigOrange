package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonaEquipmentService {
    private static final Map<String, Double> TRANSFORM_DEFAULTS = Map.ofEntries(
            Map.entry("scale", 1.0), Map.entry("offsetX", 0.0), Map.entry("offsetY", 0.0),
            Map.entry("offsetZ", 0.0), Map.entry("rotationX", 0.0),
            Map.entry("rotationY", 0.0), Map.entry("rotationZ", 0.0));
    private static final Map<String, double[]> TRANSFORM_RANGES = Map.ofEntries(
            Map.entry("scale", new double[]{0.55, 1.6}),
            Map.entry("offsetX", new double[]{-0.8, 0.8}), Map.entry("offsetY", new double[]{-0.8, 0.8}),
            Map.entry("offsetZ", new double[]{-0.55, 0.55}),
            Map.entry("rotationX", new double[]{-45, 45}), Map.entry("rotationY", new double[]{-45, 45}),
            Map.entry("rotationZ", new double[]{-45, 45}));

    private final PersonaWorkshopMapper mapper;
    private final PersonaBlueprintValidator blueprintValidator;

    public PersonaEquipmentService(PersonaWorkshopMapper mapper, PersonaBlueprintValidator blueprintValidator) {
        this.mapper = mapper;
        this.blueprintValidator = blueprintValidator;
    }

    public Map<String, Object> update(Long accountId, String slotCode, PersonaEquipmentRequest request) {
        String slot = PersonaValueSupport.normalizeCode(slotCode);
        String item = PersonaValueSupport.normalizeCode(request.itemCode());
        String variant = PersonaValueSupport.normalizeCode(request.variantCode());
        PersonaVisualLanguage.validate(slot, item, variant);
        PersonaVisualLanguage.EquipmentDefinition definition = PersonaVisualLanguage.requireSlot(slot);
        List<Map<String, Object>> savedEquipment = mapper.selectEquipment(accountId);
        boolean unlocked = isUnlocked(accountId, definition);
        if (request.equipped() && !unlocked) throw new BaseException("请先完善对应画像维度，再穿戴这件装备");
        Map<String, Object> equipment = new LinkedHashMap<>();
        equipment.put("accountId", accountId);
        equipment.put("slotCode", slot);
        equipment.put("itemCode", item);
        equipment.put("variantCode", variant);
        equipment.put("colorHex", request.colorHex().toUpperCase(Locale.ROOT));
        equipment.put("equipped", request.equipped());
        Map<String, Object> saved = savedEquipment.stream()
                .filter(value -> slot.equals(value.get("slotCode"))).findFirst().orElse(null);
        Map<String, Double> current = PersonaValueSupport.config(
                saved == null ? null : saved.get("transformJson"), TRANSFORM_DEFAULTS);
        Map<String, Double> transform = PersonaValueSupport.validatedConfig(request.transform(), current,
                TRANSFORM_RANGES, "饰品调整参数无效");
        equipment.put("transformJson", PersonaValueSupport.json(transform, "DIY 参数保存失败"));
        mapper.upsertEquipment(equipment);
        equipment.put("transform", PersonaValueSupport.config(equipment.get("transformJson"), TRANSFORM_DEFAULTS));
        equipment.put("unlocked", unlocked);
        return equipment;
    }

    public Map<String, Object> updateBlueprint(Long accountId, String slotCode,
                                                PersonaAccessoryBlueprintRequest request) {
        String slot = PersonaValueSupport.normalizeCode(slotCode);
        PersonaVisualLanguage.EquipmentDefinition definition = PersonaVisualLanguage.requireSlot(slot);
        if (!isUnlocked(accountId, definition)) throw new BaseException("请先完善对应画像维度，再制作这件饰品");
        Map<String, Object> blueprint = blueprintValidator.validate(slot, definition, request);
        Map<String, Object> persistence = new LinkedHashMap<>();
        persistence.put("accountId", accountId);
        persistence.put("slotCode", slot);
        persistence.put("itemCode", definition.itemCode());
        persistence.put("colorHex", definition.defaultColor());
        persistence.put("blueprintJson", PersonaValueSupport.json(blueprint, "饰品蓝图保存失败"));
        mapper.upsertAccessoryBlueprint(persistence);
        return blueprint;
    }

    public List<Map<String, Object>> view(Long accountId, Set<PersonaDimension> completed) {
        Map<String, Map<String, Object>> saved = mapper.selectEquipment(accountId).stream()
                .collect(Collectors.toMap(item -> String.valueOf(item.get("slotCode")), item -> item));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PersonaVisualLanguage.EquipmentDefinition definition : PersonaVisualLanguage.CATALOG) {
            Map<String, Object> state = saved.get(definition.slotCode());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slotCode", definition.slotCode());
            view.put("dimension", definition.dimension().name());
            view.put("itemCode", definition.itemCode());
            view.put("name", definition.name());
            view.put("visualMeaning", definition.visualMeaning());
            view.put("focusCode", definition.focusCode());
            view.put("variants", definition.variants());
            view.put("unlocked", completed.contains(definition.dimension()));
            view.put("variantCode", state == null ? "CLASSIC" : state.get("variantCode"));
            view.put("colorHex", state == null ? definition.defaultColor() : state.get("colorHex"));
            view.put("equipped", state != null && completed.contains(definition.dimension())
                    && PersonaValueSupport.bool(state.get("equipped")));
            view.put("transform", PersonaValueSupport.config(
                    state == null ? null : state.get("transformJson"), TRANSFORM_DEFAULTS));
            view.put("blueprint", PersonaValueSupport.objectValue(state == null ? null : state.get("blueprintJson")));
            view.put("blueprintVersion", state == null ? 0 : state.getOrDefault("blueprintVersion", 0));
            result.add(view);
        }
        return result;
    }

    private boolean isUnlocked(Long accountId, PersonaVisualLanguage.EquipmentDefinition definition) {
        return mapper.selectEntries(accountId).stream()
                .anyMatch(item -> definition.dimension().name().equals(item.get("dimensionCode")));
    }
}
