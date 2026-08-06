package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TrendProjectMatchExplainer {

    public String beginner(TrendAnalysisInput input, BeginnerProjectView project) {
        List<String> reasons = new ArrayList<>();
        List<String> topicMatches = matches(input.topics(), searchable(
                project.fullName(), project.descriptionZh(), project.techStack(), project.beginnerReason()));
        if (!topicMatches.isEmpty()) reasons.add("与你关注的“" + joined(topicMatches) + "”方向相关");
        if (matchesLanguage(input.languages(), project.primaryLanguage(), project.techStack())) {
            reasons.add("主要技术与已选语言“" + project.primaryLanguage() + "”相符");
        }
        if (contains(input.experience(), "新手") || contains(input.purpose(), "学习")) {
            reasons.add("项目难度为" + fallback(project.difficulty(), "入门")
                    + "，适合先做出可运行成果再逐步改造");
        }
        if (StringUtils.hasText(input.timeCommitment())) {
            reasons.add("可按“" + input.timeCommitment() + "”拆分推进"
                    + duration(project.estimatedDuration()));
        }
        if (reasons.isEmpty()) reasons.add(shortText(project.beginnerReason(),
                "项目已通过新手友好核验，适合作为本次分析的真实参考仓库"));
        return String.join("；", reasons) + "。";
    }

    public String career(TrendAnalysisInput input, CareerProjectView project) {
        List<String> reasons = new ArrayList<>();
        if (StringUtils.hasText(project.bestRoleName())) {
            reasons.add("可重点证明“" + project.bestRoleName() + "”相关能力");
        }
        List<String> topicMatches = matches(input.topics(), searchable(
                project.fullName(), project.summaryZh(), project.techStack(), project.careerValue()));
        if (!topicMatches.isEmpty()) reasons.add("与你关注的“" + joined(topicMatches) + "”方向相符");
        if (matchesLanguage(input.languages(), project.primaryLanguage(), project.techStack())) {
            reasons.add("技术栈包含“" + project.primaryLanguage() + "”，便于形成可展示的代码证据");
        }
        if (contains(input.purpose(), "求职") || contains(input.customPurpose(), "求职")) {
            reasons.add("适合围绕作品证明和面试讨论点组织项目经历");
        }
        if (StringUtils.hasText(input.timeCommitment())) {
            reasons.add("可以按照“" + input.timeCommitment() + "”安排差异化改造"
                    + duration(project.estimatedDuration()));
        }
        if (reasons.isEmpty()) reasons.add(shortText(project.careerValue(),
                "项目具备可证明的职业成长价值，适合作为本次分析的真实参考仓库"));
        return String.join("；", reasons) + "。";
    }

    private List<String> matches(List<String> values, String haystack) {
        if (values == null || values.isEmpty()) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value) && haystack.contains(value.trim().toLowerCase(Locale.ROOT))) {
                result.add(value.trim());
            }
        }
        return result.stream().limit(2).toList();
    }

    private boolean matchesLanguage(List<String> languages, String primaryLanguage, String techStack) {
        if (languages == null || languages.isEmpty()) return false;
        String target = searchable(primaryLanguage, techStack);
        return languages.stream().filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).anyMatch(target::contains);
    }

    private String searchable(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) if (value != null) result.append(' ').append(value);
        return result.toString().toLowerCase(Locale.ROOT);
    }

    private String duration(String value) {
        return StringUtils.hasText(value) ? "，参考周期为" + value : "";
    }

    private String joined(List<String> values) { return String.join("、", values); }
    private boolean contains(String value, String expected) {
        return value != null && value.contains(expected);
    }
    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
    private String shortText(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100) + "…";
    }
}
