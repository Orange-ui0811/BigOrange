package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.UserProfile;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.dto.PersonaEntryRequest;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.dto.PersonaSettingsRequest;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonaWorkshopService {
    private static final int MAX_ENTRIES_PER_DIMENSION = 8;
    private static final Set<String> MATERIAL_STATUSES = Set.of("PENDING", "ACCEPTED", "REJECTED");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Double> FACE_DEFAULTS = Map.ofEntries(
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
    private static final Map<String, Set<String>> BLUEPRINT_SHAPES = Map.of(
            "GOAL_CROWN", Set.of("TORUS", "CONE", "STAR", "SPHERE", "CYLINDER"),
            "INTEREST_ORBIT", Set.of("TORUS", "SPHERE", "STAR", "OCTAHEDRON"),
            "SKILL_VISOR", Set.of("TORUS", "BOX", "CYLINDER", "SPHERE"),
            "RESOURCE_PACK", Set.of("BOX", "CYLINDER", "SPHERE", "OCTAHEDRON"),
            "GROWTH_TRAIL", Set.of("TORUS", "BOX", "STAR", "SPHERE"));
    private static final Map<String, double[]> PART_PARAMETER_RANGES = Map.ofEntries(
            Map.entry("radius", new double[]{0.06, 1.8}), Map.entry("tube", new double[]{0.015, 0.35}),
            Map.entry("width", new double[]{0.05, 2.5}), Map.entry("height", new double[]{0.05, 2.5}),
            Map.entry("depth", new double[]{0.03, 1.5}), Map.entry("points", new double[]{3, 12}));
    private static final Map<String, double[]> PART_TRANSFORM_RANGES = Map.ofEntries(
            Map.entry("x", new double[]{-2.5, 2.5}), Map.entry("y", new double[]{-2.5, 2.8}),
            Map.entry("z", new double[]{-2.0, 2.0}), Map.entry("scaleX", new double[]{0.2, 2.5}),
            Map.entry("scaleY", new double[]{0.2, 2.5}), Map.entry("scaleZ", new double[]{0.2, 2.5}),
            Map.entry("rotationX", new double[]{-180, 180}), Map.entry("rotationY", new double[]{-180, 180}),
            Map.entry("rotationZ", new double[]{-180, 180}));

    private final PersonaWorkshopMapper mapper;
    private final PersonaMaterialCollector materialCollector;
    private final UserProfileMapper userProfileMapper;
    private final WorkMapper workMapper;
    private final AiResultRecordMapper aiResultRecordMapper;

    public PersonaWorkshopService(PersonaWorkshopMapper mapper,
                                  PersonaMaterialCollector materialCollector,
                                  UserProfileMapper userProfileMapper,
                                  WorkMapper workMapper,
                                  AiResultRecordMapper aiResultRecordMapper) {
        this.mapper = mapper;
        this.materialCollector = materialCollector;
        this.userProfileMapper = userProfileMapper;
        this.workMapper = workMapper;
        this.aiResultRecordMapper = aiResultRecordMapper;
    }

    public Map<String, Object> snapshot(Long accountId) {
        bootstrapMaterials(accountId);
        List<Map<String, Object>> entries = mapper.selectEntries(accountId);
        List<Map<String, Object>> materials = mapper.selectMaterials(accountId, "PENDING", 50);
        Map<String, Object> settings = settings(accountId);
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(entries);
        List<Map<String, Object>> equipment = equipmentView(accountId, grouped.keySet());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimensions", dimensionViews(grouped));
        result.put("materials", materials);
        result.put("materialCount", materials.size());
        result.put("equipment", equipment);
        result.put("settings", settings);
        result.put("completion", completion(grouped));
        result.put("recommendations", recommendations(grouped));
        return result;
    }

    public Map<String, Object> publicSnapshot(Long accountId) {
        Map<String, Object> settings = settings(accountId);
        boolean enabled = bool(settings.get("showcaseEnabled"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("avatarEnabled", enabled && bool(settings.get("avatarEnabled")));
        result.put("appearance", appearanceView(settings));
        if (!enabled) return result;
        List<Map<String, Object>> entries = mapper.selectEntries(accountId);
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(entries);
        result.put("dimensions", dimensionViews(grouped));
        result.put("equipment", equipmentView(accountId, grouped.keySet()));
        result.put("completion", completion(grouped));
        return result;
    }

    public Map<String, Object> avatarSnapshot(Long accountId) {
        Map<String, Object> settings = settings(accountId);
        boolean enabled = bool(settings.get("showcaseEnabled")) && bool(settings.get("avatarEnabled"));
        if (!enabled) return Map.of("enabled", false, "equipment", List.of(),
                "appearance", appearanceView(settings));
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(mapper.selectEntries(accountId));
        return Map.of("enabled", true, "equipment", equipmentView(accountId, grouped.keySet()),
                "appearance", appearanceView(settings));
    }

    @Transactional
    public Map<String, Object> createEntry(Long accountId, PersonaEntryRequest request) {
        PersonaDimension dimension = PersonaDimension.parse(request.dimension());
        String content = requiredText(request.content());
        List<Map<String, Object>> entries = mapper.selectEntries(accountId);
        long count = entries.stream().filter(item -> dimension.name().equals(item.get("dimensionCode"))).count();
        if (count >= MAX_ENTRIES_PER_DIMENSION) throw new BaseException("每个画像维度最多保留 8 条内容");
        Map<String, Object> entry = entry(accountId, dimension, content, "MANUAL", null, (int) count);
        mapper.insertEntry(entry);
        return mapper.selectEntryOwned(((Number) entry.get("id")).longValue(), accountId);
    }

    @Transactional
    public Map<String, Object> updateEntry(Long accountId, Long id, PersonaEntryRequest request) {
        PersonaDimension dimension = PersonaDimension.parse(request.dimension());
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", id);
        update.put("accountId", accountId);
        update.put("dimensionCode", dimension.name());
        update.put("content", requiredText(request.content()));
        if (mapper.updateEntry(update) == 0) throw new BaseException("画像内容不存在或无权修改");
        return mapper.selectEntryOwned(id, accountId);
    }

    public void archiveEntry(Long accountId, Long id) {
        if (mapper.archiveEntry(id, accountId) == 0) throw new BaseException("画像内容不存在或无权删除");
    }

    public List<Map<String, Object>> materials(Long accountId, String status) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !MATERIAL_STATUSES.contains(normalized)) throw new BaseException("素材状态无效");
        return mapper.selectMaterials(accountId, normalized, 100);
    }

    @Transactional
    public Map<String, Object> acceptMaterial(Long accountId, Long id, String editedContent) {
        Map<String, Object> material = mapper.selectMaterialOwned(id, accountId);
        if (material == null) throw new BaseException("画像素材不存在或无权访问");
        Map<String, Object> existing = mapper.selectEntryByMaterial(accountId, id);
        if (existing != null) return existing;
        if (!"PENDING".equals(material.get("status"))) throw new BaseException("该画像素材已经处理");
        PersonaDimension dimension = PersonaDimension.parse(String.valueOf(material.get("dimensionCode")));
        String content = editedContent == null || editedContent.isBlank()
                ? String.valueOf(material.get("suggestedContent")) : editedContent;
        long position = mapper.selectEntries(accountId).stream()
                .filter(item -> dimension.name().equals(item.get("dimensionCode"))).count();
        if (position >= MAX_ENTRIES_PER_DIMENSION) throw new BaseException("该画像维度已经达到 8 条上限");
        Map<String, Object> entry = entry(accountId, dimension, requiredText(content), "MATERIAL", id, (int) position);
        mapper.insertEntry(entry);
        if (mapper.acceptMaterial(id, accountId) == 0) throw new BaseException("该画像素材已经处理");
        return mapper.selectEntryOwned(((Number) entry.get("id")).longValue(), accountId);
    }

    public void rejectMaterial(Long accountId, Long id) {
        Map<String, Object> material = mapper.selectMaterialOwned(id, accountId);
        if (material == null) throw new BaseException("画像素材不存在或无权访问");
        if (mapper.rejectMaterial(id, accountId) == 0) throw new BaseException("该画像素材已经处理");
    }

    public Map<String, Object> updateEquipment(Long accountId, String slotCode, PersonaEquipmentRequest request) {
        String normalizedSlot = normalizeCode(slotCode);
        String itemCode = normalizeCode(request.itemCode());
        String variantCode = normalizeCode(request.variantCode());
        PersonaVisualLanguage.validate(normalizedSlot, itemCode, variantCode);
        PersonaVisualLanguage.EquipmentDefinition definition = PersonaVisualLanguage.requireSlot(normalizedSlot);
        List<Map<String, Object>> savedEquipment = mapper.selectEquipment(accountId);
        boolean unlocked = mapper.selectEntries(accountId).stream()
                .anyMatch(item -> definition.dimension().name().equals(item.get("dimensionCode")));
        if (request.equipped() && !unlocked) throw new BaseException("请先完善对应画像维度，再穿戴这件装备");
        Map<String, Object> equipment = new LinkedHashMap<>();
        equipment.put("accountId", accountId);
        equipment.put("slotCode", normalizedSlot);
        equipment.put("itemCode", itemCode);
        equipment.put("variantCode", variantCode);
        equipment.put("colorHex", request.colorHex().toUpperCase(Locale.ROOT));
        equipment.put("equipped", request.equipped());
        Map<String, Object> saved = savedEquipment.stream()
                .filter(item -> normalizedSlot.equals(item.get("slotCode"))).findFirst().orElse(null);
        Map<String, Double> currentTransform = config(saved == null ? null : saved.get("transformJson"),
                TRANSFORM_DEFAULTS);
        equipment.put("transformJson", json(validatedConfig(request.transform(), currentTransform,
                TRANSFORM_RANGES, "饰品调整参数无效")));
        mapper.upsertEquipment(equipment);
        equipment.put("transform", config(equipment.get("transformJson"), TRANSFORM_DEFAULTS));
        equipment.put("unlocked", unlocked);
        return equipment;
    }

    public Map<String, Object> updateAccessoryBlueprint(Long accountId, String slotCode,
                                                         PersonaAccessoryBlueprintRequest request) {
        String normalizedSlot = normalizeCode(slotCode);
        PersonaVisualLanguage.EquipmentDefinition definition = PersonaVisualLanguage.requireSlot(normalizedSlot);
        boolean unlocked = mapper.selectEntries(accountId).stream()
                .anyMatch(item -> definition.dimension().name().equals(item.get("dimensionCode")));
        if (!unlocked) throw new BaseException("请先完善对应画像维度，再制作这件饰品");
        if (request == null || request.parts() == null) throw new BaseException("饰品蓝图不能为空");
        if (request.parts().size() > 15) throw new BaseException("每件饰品最多使用 15 个零件");
        String name = request.name() == null || request.name().isBlank() ? "我的" + definition.name()
                : request.name().replaceAll("\\s+", " ").trim();
        if (name.length() > 48) throw new BaseException("饰品名称最多 48 个字符");

        Set<String> ids = new java.util.HashSet<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        for (PersonaAccessoryBlueprintRequest.Part part : request.parts()) {
            if (part == null || part.id() == null || !part.id().matches("^[A-Za-z0-9_-]{1,40}$")
                    || !ids.add(part.id())) throw new BaseException("饰品零件编号无效或重复");
            String shape = normalizeCode(part.shape());
            if (!BLUEPRINT_SHAPES.getOrDefault(normalizedSlot, Set.of()).contains(shape))
                throw new BaseException("该画像维度不支持这种零件形状");
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", part.id());
            normalized.put("label", safePartLabel(part.label(), shape));
            normalized.put("shape", shape);
            normalized.put("visible", part.visible() == null || part.visible());
            normalized.put("locked", part.locked() != null && part.locked());
            normalized.put("parameters", validatedConfig(part.parameters(), Map.of(),
                    PART_PARAMETER_RANGES, "饰品形状参数无效"));
            normalized.put("transform", validatedConfig(part.transform(), Map.of(
                            "x", 0.0, "y", 0.0, "z", 0.0,
                            "scaleX", 1.0, "scaleY", 1.0, "scaleZ", 1.0,
                            "rotationX", 0.0, "rotationY", 0.0, "rotationZ", 0.0),
                    PART_TRANSFORM_RANGES, "饰品零件位置参数无效"));
            normalized.put("material", material(part.material(), definition.defaultColor()));
            parts.add(normalized);
        }
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("name", name);
        blueprint.put("anchor", definition.focusCode().toUpperCase(Locale.ROOT));
        blueprint.put("parts", parts);
        Map<String, Object> persistence = new LinkedHashMap<>();
        persistence.put("accountId", accountId);
        persistence.put("slotCode", normalizedSlot);
        persistence.put("itemCode", definition.itemCode());
        persistence.put("colorHex", definition.defaultColor());
        persistence.put("blueprintJson", objectJson(blueprint));
        mapper.upsertAccessoryBlueprint(persistence);
        return blueprint;
    }

    public Map<String, Object> updateSettings(Long accountId, PersonaSettingsRequest request) {
        Map<String, Object> settings = new LinkedHashMap<>();
        boolean showcaseEnabled = request.showcaseEnabled();
        settings.put("accountId", accountId);
        settings.put("showcaseEnabled", showcaseEnabled);
        settings.put("avatarEnabled", showcaseEnabled && request.avatarEnabled());
        Map<String, Object> current = settings(accountId);
        settings.put("bodyColor", settingValue(request.bodyColor(), current, "bodyColor", "#F37021"));
        settings.put("faceStyle", settingValue(request.faceStyle(), current, "faceStyle", "FRIENDLY"));
        settings.put("leafStyle", settingValue(request.leafStyle(), current, "leafStyle", "CLASSIC"));
        settings.put("surfaceStyle", settingValue(request.surfaceStyle(), current, "surfaceStyle", "NATURAL"));
        Map<String, Double> currentFaceConfig = config(current.get("faceConfig"), FACE_DEFAULTS);
        settings.put("faceConfigJson", json(validatedConfig(request.faceConfig(), currentFaceConfig,
                FACE_RANGES, "五官调整参数无效")));
        mapper.upsertSettings(settings);
        return settings(accountId);
    }

    private void bootstrapMaterials(Long accountId) {
        UserProfile profile = userProfileMapper.selectByAccountId(accountId);
        if (profile != null && profile.getSkills() != null && !profile.getSkills().isBlank()) {
            materialCollector.capture(accountId, PersonaDimension.SKILL, "PROFILE_SKILLS",
                    "USER_PROFILE", "skills", "账号资料中填写了技能信息",
                    "我掌握或正在使用的技能包括：" + profile.getSkills());
        }
        for (Work work : workMapper.selectByOwnerId(accountId, 0, 5)) {
            boolean approved = "APPROVED".equals(work.getAuditStatus());
            materialCollector.capture(accountId, approved ? PersonaDimension.GROWTH : PersonaDimension.SKILL,
                    approved ? "WORK_APPROVED" : "WORK_CREATED", "WORK", String.valueOf(work.getId()),
                    (approved ? "已通过审核的作品" : "已创建的作品草稿") + "《" + work.getTitle() + "》",
                    approved ? "我完成并发布了作品《" + work.getTitle() + "》"
                            : "我正在制作作品《" + work.getTitle() + "》");
        }
        for (Map<String, Object> record : aiResultRecordMapper.selectRecentForPersona(accountId, 5)) {
            String title = String.valueOf(record.get("title"));
            materialCollector.capture(accountId, PersonaDimension.GROWTH, "AI_RESULT_COMPLETED",
                    "AI_RESULT", String.valueOf(record.get("requestId")), "完成 AI 工具任务：" + title,
                    "我使用 OrangeVC 的「" + title + "」完成了一次分析或方案生成");
        }
    }

    private List<Map<String, Object>> dimensionViews(Map<PersonaDimension, List<Map<String, Object>>> grouped) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PersonaDimension dimension : PersonaDimension.values()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", dimension.name());
            view.put("label", dimension.label());
            view.put("entries", grouped.getOrDefault(dimension, List.of()));
            view.put("completed", grouped.containsKey(dimension));
            result.add(view);
        }
        return result;
    }

    private List<Map<String, Object>> equipmentView(Long accountId, Set<PersonaDimension> completed) {
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
            view.put("equipped", state != null && completed.contains(definition.dimension()) && bool(state.get("equipped")));
            view.put("transform", config(state == null ? null : state.get("transformJson"), TRANSFORM_DEFAULTS));
            view.put("blueprint", objectValue(state == null ? null : state.get("blueprintJson")));
            view.put("blueprintVersion", state == null ? 0 : state.getOrDefault("blueprintVersion", 0));
            result.add(view);
        }
        return result;
    }

    private Map<PersonaDimension, List<Map<String, Object>>> groupEntries(List<Map<String, Object>> entries) {
        Map<PersonaDimension, List<Map<String, Object>>> grouped = new EnumMap<>(PersonaDimension.class);
        for (Map<String, Object> entry : entries) {
            PersonaDimension dimension = PersonaDimension.parse(String.valueOf(entry.get("dimensionCode")));
            grouped.computeIfAbsent(dimension, key -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private Map<String, Object> completion(Map<PersonaDimension, List<Map<String, Object>>> grouped) {
        int completed = grouped.size();
        return Map.of("completedDimensions", completed, "totalDimensions", PersonaDimension.values().length,
                "percent", completed * 100 / PersonaDimension.values().length);
    }

    private List<Map<String, Object>> recommendations(Map<PersonaDimension, List<Map<String, Object>>> grouped) {
        String text = grouped.values().stream().flatMap(Collection::stream)
                .map(item -> String.valueOf(item.get("content"))).collect(Collectors.joining(" "));
        List<Map<String, Object>> recommendations = new ArrayList<>();
        if (!grouped.containsKey(PersonaDimension.GOAL)) {
            recommendations.add(recommendation("先确定当前目标", "填写学习、求职、参赛、开源或接单目标，解锁方向罗盘。", "/persona-workshop", "PROFILE"));
        }
        if (containsAny(text, "求职", "岗位", "简历")) {
            recommendations.add(recommendation("查看职业成长项目", "用真实岗位证据选择能写进简历的项目。", "/leaderboard?source=career", "CAREER"));
        } else if (containsAny(text, "开源", "Issue", "贡献")) {
            recommendations.add(recommendation("认领一个开源 Issue", "从可参与任务开始积累公开贡献证据。", "/open-source?tab=discover", "OPEN_SOURCE"));
        } else if (containsAny(text, "比赛", "参赛", "竞赛")) {
            recommendations.add(recommendation("查看近期 AI 大赛", "用明确赛题推动一个可展示作品。", "/ai/career", "COMPETITION"));
        } else {
            recommendations.add(recommendation("从新手项目开始", "选择一个已核验、能跑起来的仓库作为起点。", "/leaderboard?source=beginner", "BEGINNER"));
        }
        if (!grouped.containsKey(PersonaDimension.GROWTH)) {
            recommendations.add(recommendation("完成一次趋势分析", "让下一次真实行动进入素材箱，由你决定是否写入成长轨迹。", "/trend", "AI_TOOL"));
        } else {
            recommendations.add(recommendation("生成下一阶段方案", "把已确认画像作为输入，形成更贴近当前状态的行动计划。", "/plan", "PLAN"));
        }
        return recommendations.stream().limit(3).toList();
    }

    private Map<String, Object> recommendation(String title, String description, String route, String type) {
        return Map.of("title", title, "description", description, "route", route, "type", type);
    }

    private Map<String, Object> settings(Long accountId) {
        Map<String, Object> savedSettings = mapper.selectSettings(accountId);
        if (savedSettings == null) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("showcaseEnabled", false);
            defaults.put("avatarEnabled", false);
            defaults.put("bodyColor", "#F37021");
            defaults.put("faceStyle", "FRIENDLY");
            defaults.put("leafStyle", "CLASSIC");
            defaults.put("surfaceStyle", "NATURAL");
            defaults.put("faceConfig", new LinkedHashMap<>(FACE_DEFAULTS));
            return defaults;
        }
        Map<String, Object> settings = new LinkedHashMap<>(savedSettings);
        settings.put("faceConfig", config(settings.get("faceConfig"), FACE_DEFAULTS));
        return settings;
    }

    private Map<String, Object> appearanceView(Map<String, Object> settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bodyColor", settingValue(null, settings, "bodyColor", "#F37021"));
        result.put("faceStyle", settingValue(null, settings, "faceStyle", "FRIENDLY"));
        result.put("leafStyle", settingValue(null, settings, "leafStyle", "CLASSIC"));
        result.put("surfaceStyle", settingValue(null, settings, "surfaceStyle", "NATURAL"));
        result.put("faceConfig", config(settings.get("faceConfig"), FACE_DEFAULTS));
        return result;
    }

    private Map<String, Double> config(Object raw, Map<String, Double> defaults) {
        Map<String, Double> result = new LinkedHashMap<>(defaults);
        if (raw == null) return result;
        try {
            Map<String, Double> saved = raw instanceof Map<?, ?> map
                    ? JSON.convertValue(map, new TypeReference<>() {})
                    : JSON.readValue(String.valueOf(raw), new TypeReference<>() {});
            saved.forEach((key, value) -> { if (defaults.containsKey(key) && value != null) result.put(key, value); });
        } catch (Exception ignored) {
            // Invalid legacy JSON falls back to safe defaults.
        }
        return result;
    }

    private Map<String, Double> validatedConfig(Map<String, Double> requested,
                                                 Map<String, Double> current,
                                                 Map<String, double[]> ranges,
                                                 String message) {
        Map<String, Double> result = new LinkedHashMap<>(current);
        if (requested == null) return result;
        for (Map.Entry<String, Double> entry : requested.entrySet()) {
            double[] range = ranges.get(entry.getKey());
            Double value = entry.getValue();
            if (range == null || value == null || !Double.isFinite(value)
                    || value < range[0] || value > range[1]) throw new BaseException(message);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private String json(Map<String, Double> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception error) {
            throw new BaseException("DIY 参数保存失败");
        }
    }

    private String objectJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception error) {
            throw new BaseException("饰品蓝图保存失败");
        }
    }

    private Map<String, Object> objectValue(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            if (raw instanceof Map<?, ?> map) return JSON.convertValue(map, new TypeReference<>() {});
            return JSON.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safePartLabel(String label, String fallback) {
        String value = label == null || label.isBlank() ? fallback : label.replaceAll("\\s+", " ").trim();
        if (value.length() > 24) throw new BaseException("零件名称最多 24 个字符");
        return value;
    }

    private Map<String, Object> material(PersonaAccessoryBlueprintRequest.Material requested,
                                         String defaultColor) {
        String color = requested == null || requested.color() == null ? defaultColor : requested.color().toUpperCase(Locale.ROOT);
        if (!color.matches("^#[0-9A-F]{6}$")) throw new BaseException("零件颜色格式无效");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("color", color);
        result.put("opacity", bounded(requested == null ? null : requested.opacity(), 0.15, 1, 1, "零件透明度无效"));
        result.put("metalness", bounded(requested == null ? null : requested.metalness(), 0, 1, 0.1, "零件金属感无效"));
        result.put("roughness", bounded(requested == null ? null : requested.roughness(), 0.05, 1, 0.45, "零件粗糙度无效"));
        result.put("emissiveIntensity", bounded(requested == null ? null : requested.emissiveIntensity(), 0, 0.8, 0, "零件发光参数无效"));
        return result;
    }

    private double bounded(Double value, double min, double max, double fallback, String message) {
        if (value == null) return fallback;
        if (!Double.isFinite(value) || value < min || value > max) throw new BaseException(message);
        return value;
    }

    private String settingValue(String requested, Map<String, Object> current, String key, String fallback) {
        if (requested != null && !requested.isBlank()) return requested.trim().toUpperCase(Locale.ROOT);
        Object saved = current.get(key);
        return saved == null || String.valueOf(saved).isBlank() ? fallback : String.valueOf(saved);
    }

    private Map<String, Object> entry(Long accountId, PersonaDimension dimension, String content,
                                      String sourceType, Long sourceMaterialId, int positionNo) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("accountId", accountId);
        entry.put("dimensionCode", dimension.name());
        entry.put("content", content);
        entry.put("sourceType", sourceType);
        entry.put("sourceMaterialId", sourceMaterialId);
        entry.put("positionNo", positionNo);
        return entry;
    }

    private String requiredText(String value) {
        if (value == null || value.isBlank()) throw new BaseException("请输入画像内容");
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 500 ? cleaned : cleaned.substring(0, 500);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
