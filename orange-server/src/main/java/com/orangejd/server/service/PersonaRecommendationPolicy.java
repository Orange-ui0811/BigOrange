package com.orangejd.server.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PersonaRecommendationPolicy {
    public List<Map<String, Object>> recommendations(Map<PersonaDimension, List<Map<String, Object>>> grouped) {
        String text = grouped.entrySet().stream()
                .filter(entry -> entry.getKey() != PersonaDimension.LEFT_HAND)
                .map(Map.Entry::getValue).flatMap(Collection::stream)
                .map(item -> String.valueOf(item.get("content"))).collect(Collectors.joining(" "));
        String exclusions = grouped.getOrDefault(PersonaDimension.LEFT_HAND, List.of()).stream()
                .map(item -> String.valueOf(item.get("content"))).collect(Collectors.joining(" "));
        List<Map<String, Object>> result = new ArrayList<>();
        if (!grouped.containsKey(PersonaDimension.GOAL)) {
            result.add(item("先确定当前目标", "填写学习、求职、参赛、开源或接单目标，解锁方向罗盘。",
                    "/persona-workshop", "PROFILE"));
        }
        if (containsAny(text, "求职", "岗位", "简历") && !containsAny(exclusions, "求职", "岗位", "简历")) {
            result.add(item("查看职业成长项目", "用真实岗位证据选择能写进简历的项目。",
                    "/leaderboard?source=career", "CAREER"));
        } else if (containsAny(text, "开源", "Issue", "贡献") && !containsAny(exclusions, "开源", "Issue", "贡献")) {
            result.add(item("认领一个开源 Issue", "从可参与任务开始积累公开贡献证据。",
                    "/open-source?tab=discover", "OPEN_SOURCE"));
        } else if (containsAny(text, "比赛", "参赛", "竞赛") && !containsAny(exclusions, "比赛", "参赛", "竞赛")) {
            result.add(item("查看近期 AI 大赛", "用明确赛题推动一个可展示作品。",
                    "/ai/career", "COMPETITION"));
        } else if (!containsAny(exclusions, "新手", "入门", "简单项目")) {
            result.add(item("从新手项目开始", "选择一个已核验、能跑起来的仓库作为起点。",
                    "/leaderboard?source=beginner", "BEGINNER"));
        } else {
            result.add(item("探索平台项目", "已避开你明确排除的项目类型，从更多真实项目中继续筛选。",
                    "/market", "PROJECT"));
        }
        if (!grouped.containsKey(PersonaDimension.GROWTH)) {
            result.add(item("完成一次趋势分析", "让下一次真实行动进入素材箱，由你决定是否写入成长轨迹。",
                    "/trend", "AI_TOOL"));
        } else {
            result.add(item("生成下一阶段方案", "把已确认画像作为输入，形成更贴近当前状态的行动计划。",
                    "/plan", "PLAN"));
        }
        return result.stream().limit(3).toList();
    }

    private Map<String, Object> item(String title, String description, String route, String type) {
        return Map.of("title", title, "description", description, "route", route, "type", type);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
