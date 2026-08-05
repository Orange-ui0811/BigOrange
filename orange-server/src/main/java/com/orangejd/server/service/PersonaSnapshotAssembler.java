package com.orangejd.server.service;

import com.orangejd.pojo.entity.UserProfile;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonaSnapshotAssembler {
    private final PersonaWorkshopMapper mapper;
    private final PersonaMaterialCollector materialCollector;
    private final UserProfileMapper userProfileMapper;
    private final WorkMapper workMapper;
    private final AiResultRecordMapper aiResultRecordMapper;
    private final PersonaSettingsService settingsService;
    private final PersonaEquipmentService equipmentService;
    private final PersonaRecommendationPolicy recommendationPolicy;

    public PersonaSnapshotAssembler(PersonaWorkshopMapper mapper,
                                    PersonaMaterialCollector materialCollector,
                                    UserProfileMapper userProfileMapper,
                                    WorkMapper workMapper,
                                    AiResultRecordMapper aiResultRecordMapper,
                                    PersonaSettingsService settingsService,
                                    PersonaEquipmentService equipmentService,
                                    PersonaRecommendationPolicy recommendationPolicy) {
        this.mapper = mapper;
        this.materialCollector = materialCollector;
        this.userProfileMapper = userProfileMapper;
        this.workMapper = workMapper;
        this.aiResultRecordMapper = aiResultRecordMapper;
        this.settingsService = settingsService;
        this.equipmentService = equipmentService;
        this.recommendationPolicy = recommendationPolicy;
    }

    public Map<String, Object> snapshot(Long accountId) {
        bootstrapMaterials(accountId);
        List<Map<String, Object>> entries = mapper.selectEntries(accountId);
        List<Map<String, Object>> materials = mapper.selectMaterials(accountId, "PENDING", 50);
        Map<String, Object> settings = settingsService.load(accountId);
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(entries);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimensions", dimensionViews(grouped));
        result.put("materials", materials);
        result.put("materialCount", materials.size());
        result.put("equipment", equipmentService.view(accountId, grouped.keySet()));
        result.put("settings", settings);
        result.put("completion", completion(grouped));
        result.put("recommendations", recommendationPolicy.recommendations(grouped));
        return result;
    }

    public Map<String, Object> publicSnapshot(Long accountId) {
        Map<String, Object> settings = settingsService.load(accountId);
        boolean enabled = PersonaValueSupport.bool(settings.get("showcaseEnabled"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("avatarEnabled", enabled && PersonaValueSupport.bool(settings.get("avatarEnabled")));
        result.put("appearance", settingsService.appearance(settings));
        if (!enabled) return result;
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(mapper.selectEntries(accountId));
        result.put("dimensions", dimensionViews(grouped));
        result.put("equipment", equipmentService.view(accountId, grouped.keySet()));
        result.put("completion", completion(grouped));
        return result;
    }

    public Map<String, Object> avatarSnapshot(Long accountId) {
        Map<String, Object> settings = settingsService.load(accountId);
        boolean enabled = PersonaValueSupport.bool(settings.get("showcaseEnabled"))
                && PersonaValueSupport.bool(settings.get("avatarEnabled"));
        if (!enabled) return Map.of("enabled", false, "equipment", List.of(),
                "appearance", settingsService.appearance(settings));
        Map<PersonaDimension, List<Map<String, Object>>> grouped = groupEntries(mapper.selectEntries(accountId));
        return Map.of("enabled", true, "equipment", equipmentService.view(accountId, grouped.keySet()),
                "appearance", settingsService.appearance(settings));
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
}
