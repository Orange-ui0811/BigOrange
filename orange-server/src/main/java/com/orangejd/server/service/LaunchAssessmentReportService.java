package com.orangejd.server.service;

import com.orangejd.server.mapper.LaunchAssessmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LaunchAssessmentReportService {

    private final LaunchAssessmentMapper mapper;

    public LaunchAssessmentReportService(LaunchAssessmentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void saveStage(Long accountId, Long assessmentId, int stageNo,
                          String stageTitle, String content, int segmentCost) {
        mapper.deleteStagesAfter(assessmentId, stageNo);
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("assessmentId", assessmentId);
        stage.put("stageNo", stageNo);
        stage.put("stageTitle", stageTitle);
        stage.put("content", content);
        stage.put("segmentCost", segmentCost);
        mapper.upsertStage(stage);
        mapper.updateProgress(assessmentId, accountId, stageNo);
    }
}
