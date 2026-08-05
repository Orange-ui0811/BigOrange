package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;

import java.util.List;
import java.util.Map;

public final class PersonaVisualLanguage {
    private PersonaVisualLanguage() {
    }

    public record EquipmentDefinition(
            String slotCode,
            PersonaDimension dimension,
            String itemCode,
            String name,
            String visualMeaning,
            String focusCode,
            String defaultColor,
            List<String> variants) {
    }

    public static final List<EquipmentDefinition> CATALOG = List.of(
            new EquipmentDefinition("GOAL_CROWN", PersonaDimension.GOAL, "COMPASS_CROWN", "方向罗盘",
                    "位于头顶，表达此刻选择前进的方向", "goal", "#F59E0B",
                    List.of("CLASSIC", "ORBIT", "BEACON")),
            new EquipmentDefinition("INTEREST_ORBIT", PersonaDimension.INTEREST, "IDEA_ORBIT", "兴趣星环",
                    "环绕橙子，表达持续吸引注意力的主题", "interest", "#8B5CF6",
                    List.of("CLASSIC", "SPARK", "CONSTELLATION")),
            new EquipmentDefinition("SKILL_VISOR", PersonaDimension.SKILL, "CRAFT_VISOR", "能力目镜",
                    "位于眼部，表达已经能观察、理解并动手解决的问题", "skill", "#0EA5E9",
                    List.of("CLASSIC", "ROUND", "WIDE")),
            new EquipmentDefinition("RESOURCE_PACK", PersonaDimension.RESOURCE, "ENERGY_PACK", "资源能量包",
                    "位于背侧，表达可投入的时间、预算、设备与协作能力", "resource", "#10B981",
                    List.of("CLASSIC", "COMPACT", "TEAM")),
            new EquipmentDefinition("GROWTH_TRAIL", PersonaDimension.GROWTH, "MILESTONE_TRAIL", "成长足迹",
                    "位于脚下，表达已经发生且由用户确认的行动积累", "growth", "#F97316",
                    List.of("CLASSIC", "RINGS", "STEPS")),
            new EquipmentDefinition("LEFT_HAND_WEAPON", PersonaDimension.LEFT_HAND, "LEFT_WEAPON", "左手武器",
                    "握在左手，提醒系统避开不接受的难度、技术、成本与项目方式", "left_hand", "#64748B",
                    List.of("SWORD", "KNIFE", "SHIELD", "BLASTER")),
            new EquipmentDefinition("RIGHT_HAND_WEAPON", PersonaDimension.RIGHT_HAND, "RIGHT_WEAPON", "右手武器",
                    "握在右手，表达希望项目最终带来的作品、能力或职业成果", "right_hand", "#64748B",
                    List.of("SWORD", "KNIFE", "SHIELD", "BLASTER"))
    );

    private static final Map<String, EquipmentDefinition> BY_SLOT = CATALOG.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(EquipmentDefinition::slotCode, item -> item));

    public static EquipmentDefinition requireSlot(String slotCode) {
        EquipmentDefinition definition = BY_SLOT.get(slotCode == null ? "" : slotCode.trim().toUpperCase());
        if (definition == null) throw new BaseException("装备槽位无效");
        return definition;
    }

    public static void validate(String slotCode, String itemCode, String variantCode) {
        EquipmentDefinition definition = requireSlot(slotCode);
        if (!definition.itemCode().equals(itemCode)) throw new BaseException("装备与槽位不匹配");
        if (!definition.variants().contains(variantCode)) throw new BaseException("装备款式无效");
    }
}
