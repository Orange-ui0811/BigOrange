package com.orangejd.server.service.impl;

import com.orangejd.server.service.KnowledgeGraphService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {
    private final KnowledgeGraphAssembler assembler;
    private final KnowledgeGraphComparisonService comparisonService;

    public KnowledgeGraphServiceImpl(KnowledgeGraphAssembler assembler,
                                     KnowledgeGraphComparisonService comparisonService) {
        this.assembler = assembler;
        this.comparisonService = comparisonService;
    }

    public Map<String, Object> buildGraph(String snapshotDate) {
        return assembler.buildGraph(snapshotDate);
    }

    public Map<String, Object> buildWeeklyGraph(String snapshotDate) {
        return comparisonService.buildWeeklyGraph(snapshotDate);
    }

    @Override
    public Map<String, Object> buildCompareGraph() {
        return comparisonService.buildCompareGraph();
    }
}
