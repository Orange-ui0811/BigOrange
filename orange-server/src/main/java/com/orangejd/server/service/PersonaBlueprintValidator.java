package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PersonaBlueprintValidator {
    private static final Map<String, Set<String>> SHAPES = Map.of(
            "GOAL_CROWN", Set.of("TORUS", "CONE", "STAR", "SPHERE", "CYLINDER"),
            "INTEREST_ORBIT", Set.of("TORUS", "SPHERE", "STAR", "OCTAHEDRON"),
            "SKILL_VISOR", Set.of("TORUS", "BOX", "CYLINDER", "SPHERE"),
            "RESOURCE_PACK", Set.of("BOX", "CYLINDER", "SPHERE", "OCTAHEDRON"),
            "GROWTH_TRAIL", Set.of("TORUS", "BOX", "STAR", "SPHERE"));
    private static final Map<String, double[]> PARAMETER_RANGES = Map.ofEntries(
            Map.entry("radius", new double[]{0.06, 1.8}), Map.entry("tube", new double[]{0.015, 0.35}),
            Map.entry("width", new double[]{0.05, 2.5}), Map.entry("height", new double[]{0.05, 2.5}),
            Map.entry("depth", new double[]{0.03, 1.5}), Map.entry("points", new double[]{3, 12}));
    private static final Map<String, double[]> TRANSFORM_RANGES = Map.ofEntries(
            Map.entry("x", new double[]{-2.5, 2.5}), Map.entry("y", new double[]{-2.5, 2.8}),
            Map.entry("z", new double[]{-2.0, 2.0}), Map.entry("scaleX", new double[]{0.2, 2.5}),
            Map.entry("scaleY", new double[]{0.2, 2.5}), Map.entry("scaleZ", new double[]{0.2, 2.5}),
            Map.entry("rotationX", new double[]{-180, 180}), Map.entry("rotationY", new double[]{-180, 180}),
            Map.entry("rotationZ", new double[]{-180, 180}));
    private static final Map<String, Double> TRANSFORM_DEFAULTS = Map.of(
            "x", 0.0, "y", 0.0, "z", 0.0, "scaleX", 1.0, "scaleY", 1.0, "scaleZ", 1.0,
            "rotationX", 0.0, "rotationY", 0.0, "rotationZ", 0.0);

    public Map<String, Object> validate(String slot, PersonaVisualLanguage.EquipmentDefinition definition,
                                         PersonaAccessoryBlueprintRequest request) {
        if (request == null || request.parts() == null) throw new BaseException("饰品蓝图不能为空");
        if (request.parts().size() > 15) throw new BaseException("每件饰品最多使用 15 个零件");
        String name = request.name() == null || request.name().isBlank() ? "我的" + definition.name()
                : request.name().replaceAll("\\s+", " ").trim();
        if (name.length() > 48) throw new BaseException("饰品名称最多 48 个字符");
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        for (PersonaAccessoryBlueprintRequest.Part part : request.parts()) {
            parts.add(validatePart(slot, definition.defaultColor(), part, ids));
        }
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("name", name);
        blueprint.put("anchor", definition.focusCode().toUpperCase(java.util.Locale.ROOT));
        blueprint.put("parts", parts);
        return blueprint;
    }

    private Map<String, Object> validatePart(String slot, String defaultColor,
                                              PersonaAccessoryBlueprintRequest.Part part, Set<String> ids) {
        if (part == null || part.id() == null || !part.id().matches("^[A-Za-z0-9_-]{1,40}$")
                || !ids.add(part.id())) throw new BaseException("饰品零件编号无效或重复");
        String shape = PersonaValueSupport.normalizeCode(part.shape());
        if (!SHAPES.getOrDefault(slot, Set.of()).contains(shape))
            throw new BaseException("该画像维度不支持这种零件形状");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("id", part.id());
        normalized.put("label", label(part.label(), shape));
        normalized.put("shape", shape);
        normalized.put("visible", part.visible() == null || part.visible());
        normalized.put("locked", part.locked() != null && part.locked());
        normalized.put("parameters", PersonaValueSupport.validatedConfig(part.parameters(), Map.of(),
                PARAMETER_RANGES, "饰品形状参数无效"));
        normalized.put("transform", PersonaValueSupport.validatedConfig(part.transform(), TRANSFORM_DEFAULTS,
                TRANSFORM_RANGES, "饰品零件位置参数无效"));
        normalized.put("material", material(part.material(), defaultColor));
        return normalized;
    }

    private String label(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested.replaceAll("\\s+", " ").trim();
        if (value.length() > 24) throw new BaseException("零件名称最多 24 个字符");
        return value;
    }

    private Map<String, Object> material(PersonaAccessoryBlueprintRequest.Material requested, String defaultColor) {
        String color = requested == null || requested.color() == null ? defaultColor
                : requested.color().toUpperCase(java.util.Locale.ROOT);
        if (!color.matches("^#[0-9A-F]{6}$")) throw new BaseException("零件颜色格式无效");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("color", color);
        result.put("opacity", bounded(requested == null ? null : requested.opacity(), .15, 1, 1, "零件透明度无效"));
        result.put("metalness", bounded(requested == null ? null : requested.metalness(), 0, 1, .1, "零件金属感无效"));
        result.put("roughness", bounded(requested == null ? null : requested.roughness(), .05, 1, .45, "零件粗糙度无效"));
        result.put("emissiveIntensity", bounded(requested == null ? null : requested.emissiveIntensity(), 0, .8, 0, "零件发光参数无效"));
        return result;
    }

    private double bounded(Double value, double min, double max, double fallback, String message) {
        if (value == null) return fallback;
        if (!Double.isFinite(value) || value < min || value > max) throw new BaseException(message);
        return value;
    }
}
