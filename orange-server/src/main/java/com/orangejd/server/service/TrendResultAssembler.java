package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrendResultAssembler {
    private final TrendResponseParser parser;
    private final TrendRecommendationSanitizer sanitizer;
    private final TrendProjectMatchExplainer matchExplainer;
    private final ObjectMapper objectMapper;

    public TrendResultAssembler(TrendResponseParser parser, TrendRecommendationSanitizer sanitizer,
                                TrendProjectMatchExplainer matchExplainer, ObjectMapper objectMapper) {
        this.parser = parser;
        this.sanitizer = sanitizer;
        this.matchExplainer = matchExplainer;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> assemble(String content, long balance,
                                        List<BeginnerProjectView> beginner,
                                        List<CareerProjectView> career,
                                        boolean sanitize,
                                        TrendAnalysisInput input) {
        Map<String, Object> result = parser.parse(content);
        if (!parser.complete(result)) throw new IllegalStateException("AI 返回的趋势分析结果不完整");
        result.put("balance", balance);
        result.put("beginnerCases", beginner.stream().map(project -> withExplanation(
                project, matchExplainer.beginner(input, project))).toList());
        result.put("careerCases", career.stream().map(project -> withExplanation(
                project, matchExplainer.career(input, project))).toList());
        if (sanitize) sanitizer.sanitize(result, beginner, career);
        return result;
    }

    private Map<String, Object> withExplanation(Object project, String explanation) {
        Map<String, Object> result = objectMapper.convertValue(project,
                new TypeReference<LinkedHashMap<String, Object>>() {});
        result.put("matchExplanation", explanation);
        return result;
    }
}
