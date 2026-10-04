package com.orangejd.server.ragb.service;

import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.retrieval.RagBSearchHit;
import com.orangejd.server.ragb.retrieval.RagBIndexRouter;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;
import com.orangejd.server.ragb.retrieval.RagBVectorSearchResult;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import com.orangejd.server.ragb.retrieval.RagBRetrievalMetadataResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RagBSearchService {
    private final RagBProperties properties;
    private final RagBMapper mapper;
    private final RagBIndexRouter indexes;
    private final RagBRelevanceGuard relevanceGuard;
    private final RagBConstraintMatcher constraintMatcher;
    private final RagBRetrievalMetadataResolver metadataResolver;

    public RagBSearchService(RagBProperties properties, RagBMapper mapper, RagBIndexRouter indexes,
                             RagBRelevanceGuard relevanceGuard, RagBConstraintMatcher constraintMatcher,
                             RagBRetrievalMetadataResolver metadataResolver) {
        this.properties = properties;
        this.mapper = mapper;
        this.indexes = indexes;
        this.relevanceGuard = relevanceGuard;
        this.constraintMatcher = constraintMatcher;
        this.metadataResolver = metadataResolver;
    }

    public Map<String, Object> search(String query, int topK, double threshold, Set<String> sourceTypes) {
        SearchResult searchResult = retrieve(query, topK, threshold, sourceTypes);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generation", searchResult.generation());
        result.put("query", query);
        result.put("scannedCount", searchResult.scannedCount());
        result.put("candidateCount", searchResult.candidateCount());
        result.put("selectedCount", searchResult.hits().size());
        result.put("excludedReasons", searchResult.excludedReasons());
        result.put("status", searchResult.status());
        result.put("answerPolicy", searchResult.answerPolicy());
        result.put("hits", searchResult.hits());
        result.put("evidenceGroups", searchResult.evidenceGroups());
        result.put("adapter", searchResult.adapter());
        return result;
    }

    public SearchResult retrieve(String query, int topK, double threshold, Set<String> sourceTypes) {
        return retrieve(query, topK, threshold, RagBSearchConstraints.sources(sourceTypes));
    }

    public SearchResult retrieve(String query, int topK, double threshold, RagBSearchConstraints constraints) {
        if (!properties.isEnabled()) throw new IllegalStateException("B 版 RAG 实验模块未启用");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("检索问题不能为空");
        if (query.length() > 2000) throw new IllegalArgumentException("检索问题过长");
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("TopK须为1至20");
        if (threshold < -1 || threshold > 1) throw new IllegalArgumentException("相似度阈值必须在 -1 到 1 之间");
        RagBIndexGeneration generation = com.orangejd.server.service.TrendLabObservation.measure("B.activeGeneration",mapper::selectActiveGeneration);
        if (generation == null) throw new IllegalStateException("没有可用的 B 版索引代际");
        RagBVectorSearchResult vectorResult = indexes.searchWithTrace(generation, query,
                Math.max(topK, properties.getFusionCandidateLimit()), threshold, constraints.indexScope());
        List<RagBSearchHit> candidates = vectorResult.hits();
        List<RagBSearchHit> eligible = new ArrayList<>();
        Map<String, Integer> excluded = new LinkedHashMap<>();
        excluded.putAll(vectorResult.filterReasons());
        Map<Long, RagBDocument> documentCache = new LinkedHashMap<>();
        Map<String, Boolean> activeSourceCache = new LinkedHashMap<>();
        long filterStarted=System.nanoTime();
        for (RagBSearchHit hit : candidates) {
            RagBDocument document = documentCache.computeIfAbsent(hit.documentId(), mapper::selectDocument);
            String reason = null;
            if (document == null) reason = "DOCUMENT_MISSING";
            else if (!"READY".equals(document.getStatus()) || document.getDeletedAt()!=null) reason = "DOCUMENT_NOT_READY";
            else if (!constraints.documentIds().isEmpty() && !constraints.documentIds().contains(hit.documentId())) reason = "DOCUMENT_SCOPE_MISMATCH";
            else if (!Long.valueOf(hit.versionId()).equals(document.getActiveVersionId())) reason = "VERSION_NOT_ACTIVE";
            else if (!activeSourceCache.computeIfAbsent(sourceKey(hit), ignored ->
                    businessSourceActive(hit.sourceType(), hit.sourceRef()))) reason = "SOURCE_INACTIVE";
            else reason = constraintMatcher.filterReason(metadataResolver.resolve(document, hit), constraints);
            if (reason != null) excluded.merge(reason, 1, Integer::sum);
            else eligible.add(withPreference(hit, constraints));
        }
        com.orangejd.server.service.TrendLabObservation.elapsed("B.databaseMetadataAndFilters",filterStarted);
        List<RagBSearchHit> immutableHits = com.orangejd.server.service.TrendLabObservation.measure("B.dedupAndSelect", () -> selectEvidence(constraints.documentIds().isEmpty()?query:"推荐 "+query, eligible, Math.max(1, Math.min(topK, 20))));
        long relevanceStarted=System.nanoTime();
        RagBRelevanceGuard.Decision relevance = relevanceGuard.assess(query, immutableHits);
        if (!relevance.relevant() && !immutableHits.isEmpty()) {
            excluded.put(relevance.reason(), immutableHits.size());
            immutableHits = List.of();
        }
        String answerPolicy = answerPolicy(immutableHits);
        com.orangejd.server.service.TrendLabObservation.elapsed("B.relevanceAndPolicy",relevanceStarted);
        return new SearchResult(generation.getGenerationKey(), indexes.adapterType(generation),
                vectorResult.scannedCount(), candidates.size(), Map.copyOf(excluded), immutableHits,
                groupEvidence(immutableHits), answerPolicy);
    }

    public record SearchResult(String generation, String adapter, int scannedCount, int candidateCount,
                               Map<String, Integer> excludedReasons, List<RagBSearchHit> hits,
                               List<EvidenceGroup> evidenceGroups, String answerPolicy) {
        public SearchResult(String generation, int scannedCount, int candidateCount,
                            Map<String, Integer> excludedReasons, List<RagBSearchHit> hits) {
            this(generation, "EXACT", scannedCount, candidateCount, excludedReasons, hits, List.of(),
                    hits.isEmpty() ? "REFUSE_NO_MATCH" : "ANSWER_WITH_CITATIONS");
        }

        public String status() { return hits.isEmpty() ? "NO_MATCH" : "REFUSE_INSUFFICIENT_EVIDENCE".equals(answerPolicy)
                ? "INSUFFICIENT_EVIDENCE" : "USED"; }
    }

    public record EvidenceGroup(String groupKey, long documentId, String title, String sourceType,
                                String sourceRef, double bestScore, List<RagBSearchHit> evidence) {}

    private List<EvidenceGroup> groupEvidence(List<RagBSearchHit> hits) {
        Map<String, List<RagBSearchHit>> groups = new LinkedHashMap<>();
        for (RagBSearchHit hit : hits) {
            String key = sourceKey(hit);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(hit);
        }
        return groups.entrySet().stream().map(entry -> {
            List<RagBSearchHit> evidence = entry.getValue().stream().limit(3).toList();
            RagBSearchHit first = evidence.getFirst();
            double best = evidence.stream().mapToDouble(RagBSearchHit::score).max().orElse(0);
            return new EvidenceGroup(entry.getKey(), first.documentId(), first.title(), first.sourceType(),
                    first.sourceRef(), best, evidence);
        }).toList();
    }

    private List<RagBSearchHit> selectEvidence(String query, List<RagBSearchHit> candidates, int limit) {
        List<RagBSearchHit> ranked = candidates.stream()
                .sorted(java.util.Comparator.comparingDouble(RagBSearchHit::score).reversed()).toList();
        // Remove repeated snapshots/identical passages, not distinct facts from one repository.
        Set<String> fingerprints = new java.util.LinkedHashSet<>();
        ranked = ranked.stream().filter(hit -> fingerprints.add(sourceKey(hit) + "\n"
                + (hit.content() == null ? "" : hit.content().replace("\r\n", "\n").trim()))).toList();
        ranked = com.orangejd.server.ragb.retrieval.RagBNearDuplicates.distinct(ranked);
        if (!recommendationQuery(query)) return ranked.stream().limit(limit).toList();
        List<RagBSearchHit> selected = new ArrayList<>();
        Set<String> represented = new java.util.LinkedHashSet<>();
        for (RagBSearchHit hit : ranked) {
            String key = sourceKey(hit);
            if (represented.add(key)) selected.add(hit);
            if (selected.size() >= limit) return List.copyOf(selected);
        }
        Set<Long> selectedChunks = selected.stream().map(RagBSearchHit::chunkId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (RagBSearchHit hit : ranked) {
            if (selectedChunks.add(hit.chunkId())) selected.add(hit);
            if (selected.size() >= limit) break;
        }
        return List.copyOf(selected);
    }

    private String sourceKey(RagBSearchHit hit) {
        return hit.sourceRef() == null || hit.sourceRef().isBlank()
                ? "document:" + hit.documentId() : hit.sourceType() + ":"
                + ("GITHUB".equals(hit.sourceType()) ? hit.sourceRef().split("\\|", 2)[0] : hit.sourceRef());
    }

    private boolean recommendationQuery(String query) {
        String value = query.toLowerCase(java.util.Locale.ROOT);
        return value.contains("推荐") || value.contains("找几个") || value.contains("项目清单")
                || value.contains("recommend") || value.contains("suggest projects")
                || value.matches(".*列出.{0,12}项目.*");
    }

    private RagBSearchHit withPreference(RagBSearchHit hit, RagBSearchConstraints constraints) {
        double boost = 0;
        String preference = null;
        if (constraints.beginnerPreferred() && "BEGINNER_PROJECT".equals(hit.sourceType())) preference = "BEGINNER";
        if (constraints.careerPreferred() && ("CAREER_PROJECT".equals(hit.sourceType())
                || "JOB_POSTING".equals(hit.sourceType()))) preference = "CAREER";
        if (preference != null) boost = hit.retrievalRoute().startsWith("HYBRID_RRF") ? 0.002 : 0.03;
        if (boost == 0) return hit;
        return new RagBSearchHit(hit.documentId(), hit.versionId(), hit.chunkId(), hit.chunkIndex(), hit.title(),
                hit.sourceType(), hit.sourceRef(), hit.sectionPath(), hit.startOffset(), hit.endOffset(), hit.content(),
                hit.score() + boost, hit.retrievalRoute() + "+PREFERENCE_" + preference);
    }

    private String answerPolicy(List<RagBSearchHit> hits) {
        if (hits.isEmpty()) return "REFUSE_NO_MATCH";
        long sections = hits.stream().map(hit -> hit.documentId() + ":" + String.valueOf(hit.sectionPath())).distinct().count();
        int characters = hits.stream().mapToInt(hit -> hit.content() == null ? 0 : hit.content().length()).sum();
        return sections >= 2 || characters >= 500 ? "ANSWER_WITH_CITATIONS" : "REFUSE_INSUFFICIENT_EVIDENCE";
    }

    private boolean businessSourceActive(String sourceType, String sourceRef) {
        try {
            if ("JOB_POSTING".equals(sourceType) && sourceRef != null && sourceRef.startsWith("job_")) {
                return mapper.countApprovedJob(Long.parseLong(sourceRef.substring(4))) > 0;
            }
            if ("BEGINNER_PROJECT".equals(sourceType) && sourceRef != null && sourceRef.startsWith("beginner:")) {
                return mapper.countPublishedBeginnerProject(Long.parseLong(sourceRef.substring(9))) > 0;
            }
            if ("CAREER_PROJECT".equals(sourceType) && sourceRef != null && sourceRef.startsWith("career:")) {
                return mapper.countPublishedCareerProject(Long.parseLong(sourceRef.substring(7))) > 0;
            }
            return !Set.of("JOB_POSTING", "BEGINNER_PROJECT", "CAREER_PROJECT").contains(sourceType);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
