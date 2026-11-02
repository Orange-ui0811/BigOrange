package com.orangejd.server.service.impl;

import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeGraphArchitectureTest {
    @Test
    void snapshotParserPreservesLegacyRecordFieldsAndMalformedFallback() {
        KnowledgeSnapshotParser parser = new KnowledgeSnapshotParser();

        List<Map<String, Object>> records = parser.parseRepos(
                "{\"records\":[{\"name\":\"demo\",\"stars\":12,\"language\":\"Java\"}]}");

        assertEquals(1, records.size());
        assertEquals("demo", records.getFirst().get("name"));
        assertEquals(12L, records.getFirst().get("stars"));
        assertTrue(parser.parseRepos("broken").isEmpty());
    }

    @Test
    void assemblerKeepsNodesThenEdgesResponseContractForEmptySnapshots() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        KnowledgeGraphAssembler assembler = new KnowledgeGraphAssembler(mapper, new KnowledgeSnapshotParser());

        Map<String, Object> graph = assembler.buildGraph("2026-08-05");

        assertEquals(List.of("nodes", "edges"), graph.keySet().stream().toList());
        assertEquals(KnowledgeGraphCatalog.TOPIC_NAMES.size(), ((List<?>) graph.get("nodes")).size());
        assertTrue(((List<?>) graph.get("edges")).isEmpty());
    }

    @Test
    void publicServiceRemainsAThinCompatibilityFacade() {
        KnowledgeGraphAssembler assembler = mock(KnowledgeGraphAssembler.class);
        KnowledgeGraphComparisonService comparison = mock(KnowledgeGraphComparisonService.class);
        Map<String, Object> expected = Map.of("thisWeek", Map.of());
        when(comparison.buildCompareGraph()).thenReturn(expected);

        KnowledgeGraphServiceImpl service = new KnowledgeGraphServiceImpl(assembler, comparison);

        assertEquals(expected, service.buildCompareGraph());
        service.buildGraph("today");
        service.buildWeeklyGraph("today");
        verify(assembler).buildGraph("today");
        verify(comparison).buildWeeklyGraph("today");
    }
}
