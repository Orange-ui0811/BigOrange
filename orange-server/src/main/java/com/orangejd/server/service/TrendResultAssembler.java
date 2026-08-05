package com.orangejd.server.service;

import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.CareerProjectView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TrendResultAssembler {
    private final TrendResponseParser parser;
    private final TrendRecommendationSanitizer sanitizer;

    public TrendResultAssembler(TrendResponseParser parser, TrendRecommendationSanitizer sanitizer) {
        this.parser = parser;
        this.sanitizer = sanitizer;
    }

    public Map<String, Object> assemble(String content, long balance,
                                        List<BeginnerProjectView> beginner,
                                        List<CareerProjectView> career,
                                        boolean sanitize) {
        Map<String, Object> result = parser.parse(content);
        if (!parser.complete(result)) throw new IllegalStateException("AI 返回的趋势分析结果不完整");
        result.put("balance", balance);
        result.put("beginnerCases", beginner);
        result.put("careerCases", career);
        if (sanitize) sanitizer.sanitize(result, beginner, career);
        return result;
    }
}
