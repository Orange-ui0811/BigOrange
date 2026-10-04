package com.orangejd.server.controller.admin;

import com.orangejd.common.result.Result;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.service.RagBDocumentService;
import com.orangejd.server.ragb.service.RagBSearchService;
import com.orangejd.server.ragb.service.RagBComparisonService;
import com.orangejd.server.ragb.service.RagBGenerationService;
import com.orangejd.server.ragb.service.RagBGithubReadmeService;
import com.orangejd.server.ragb.service.RagBAnswerService;
import com.orangejd.server.ragb.service.RagBEvaluationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import com.orangejd.server.ragb.retrieval.RagBSearchConstraints;

@RestController
@RequestMapping("/admin/rag-lab")
public class AdminRagLabController {
    private final RagBProperties properties;
    private final RagBDocumentService documents;
    private final RagBSearchService search;
    private final RagBComparisonService comparison;
    private final RagBGenerationService generations;
    private final RagBGithubReadmeService githubReadmes;
    private final RagBAnswerService answers;
    private final RagBEvaluationService evaluations;
    private final com.orangejd.server.ragb.service.RagBPlatformComparisonService platformComparison;

    @PostMapping("/compare/platform-retrieval")
    public Result<?> platformRetrieval(@RequestBody SearchRequest request) {
        try { return Result.success(platformComparison.retrieve(request.query())); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    public AdminRagLabController(RagBProperties properties, RagBDocumentService documents,
                                 RagBSearchService search, RagBComparisonService comparison,
                                 RagBGenerationService generations, RagBGithubReadmeService githubReadmes,
                                 RagBAnswerService answers, RagBEvaluationService evaluations) {
        this(properties,documents,search,comparison,generations,githubReadmes,answers,evaluations,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public AdminRagLabController(RagBProperties properties, RagBDocumentService documents,
                                 RagBSearchService search, RagBComparisonService comparison,
                                 RagBGenerationService generations, RagBGithubReadmeService githubReadmes,
                                 RagBAnswerService answers, RagBEvaluationService evaluations,
                                 com.orangejd.server.ragb.service.RagBPlatformComparisonService platformComparison) {
        this.platformComparison=platformComparison;
        this.properties = properties;
        this.documents = documents;
        this.search = search;
        this.comparison = comparison;
        this.generations = generations;
        this.githubReadmes = githubReadmes;
        this.answers = answers;
        this.evaluations = evaluations;
    }

    @GetMapping("/status")
    public Result<?> status() {
        return Result.success(Map.of("enabled", properties.isEnabled(), "namespace", properties.getRedisPrefix(),
                "defaultUserRoutesChanged", false, "availableAdapters", Set.of("EXACT", "HNSW_HYBRID"),
                "defaultRebuildAdapter", "HNSW_HYBRID", "activeIndex", generations.activeConfiguration()));
    }

    @PostMapping("/documents/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "title", required = false) String title) {
        try { return Result.success(documents.upload(file, title)); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/imports/legacy/{legacyDocumentId}")
    public Result<?> importLegacy(@PathVariable Long legacyDocumentId) {
        try { return Result.success(documents.importLegacy(legacyDocumentId)); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/imports/github")
    public Result<?> importGithub(@RequestBody GithubImportRequest request) {
        try { return Result.success(githubReadmes.sync(request.repository())); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @GetMapping("/documents")
    public Result<?> list(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(documents.list(limit));
    }

    @GetMapping("/documents/{id}")
    public Result<?> detail(@PathVariable Long id) {
        try { return Result.success(documents.detail(id)); }
        catch (IllegalArgumentException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/documents/refresh-metadata")
    public Result<?> refreshMetadata() {
        try { return Result.success(documents.refreshMetadata()); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/documents/{id}/retry")
    public Result<?> retry(@PathVariable Long id) {
        try { return Result.success(documents.retry(id)); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @DeleteMapping("/documents/{id}")
    public Result<?> delete(@PathVariable Long id) {
        try { return Result.success(documents.delete(id)); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/search")
    public Result<?> search(@RequestBody SearchRequest request) {
        try {
            RagBSearchConstraints constraints = constraints(request);
            RagBSearchService.SearchResult found = search.retrieve(request.query(), request.topK() == null ? 8 : request.topK(),
                    request.threshold() == null ? 0.55 : request.threshold(), constraints);
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("generation", found.generation());
            response.put("query", request.query());
            response.put("scannedCount", found.scannedCount());
            response.put("candidateCount", found.candidateCount());
            response.put("selectedCount", found.hits().size());
            response.put("excludedReasons", found.excludedReasons());
            response.put("status", found.status());
            response.put("answerPolicy", found.answerPolicy());
            response.put("hits", found.hits());
            response.put("evidenceGroups", found.evidenceGroups());
            response.put("adapter", found.adapter());
            return Result.success(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/answer-preview")
    public Result<?> answerPreview(@RequestBody SearchRequest request) {
        try {
            return Result.success(answers.preview(request.query(), request.topK() == null ? 8 : request.topK(),
                    request.threshold() == null ? 0.55 : request.threshold(), constraints(request)));
        } catch (RuntimeException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @PostMapping("/compare/retrieval")
    public Result<?> compareRetrieval(@RequestBody SearchRequest request) {
        try {
            return Result.success(comparison.compareRetrieval(request.query(),
                    request.topK() == null ? 8 : request.topK(), request.threshold() == null ? 0.55 : request.threshold()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }
    }

    @GetMapping("/experiments")
    public Result<?> experiments(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(comparison.list(limit));
    }

    @GetMapping("/evaluation/dataset")
    public Result<?> evaluationDataset() {
        return Result.success(evaluations.dataset());
    }

    @GetMapping("/evaluation/benchmark")
    public Result<?> evaluationBenchmark() {
        return Result.success(evaluations.benchmarkDataset());
    }

    @PostMapping("/evaluation/run")
    public Result<?> runEvaluation(@RequestParam(defaultValue = "8") int topK,
                                   @RequestParam(defaultValue = "0.55") double threshold) {
        try { return Result.success(evaluations.run(topK, threshold)); }
        catch (RuntimeException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/evaluation/benchmark/run")
    public Result<?> runBenchmark(@RequestParam(defaultValue = "8") int topK,
                                  @RequestParam(defaultValue = "0.55") double threshold) {
        try { return Result.success(evaluations.runBenchmark(topK, threshold)); }
        catch (RuntimeException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/generations/rebuild")
    public Result<?> rebuild(@RequestParam(defaultValue = "HNSW_HYBRID") String adapter) {
        try { return Result.success(generations.startRebuild(adapter)); }
        catch (IllegalArgumentException | IllegalStateException ex) { return Result.error(ex.getMessage()); }
    }

    @GetMapping("/generations")
    public Result<?> generations(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(generations.list(limit));
    }

    public record SearchRequest(String query, Integer topK, Double threshold, Set<String> sourceTypes,
                                Set<String> requiredLanguages, Integer maxDurationDays, String difficulty,
                                Boolean allowDocker, Boolean allowDatabase, Boolean allowSecrets,
                                Boolean beginnerPreferred, Boolean careerPreferred) {}

    public record GithubImportRequest(String repository) {}

    private RagBSearchConstraints constraints(SearchRequest request) {
        return new RagBSearchConstraints(request.sourceTypes(), request.requiredLanguages(),
                request.maxDurationDays(), request.difficulty(), request.allowDocker(), request.allowDatabase(),
                request.allowSecrets(), Boolean.TRUE.equals(request.beginnerPreferred()),
                Boolean.TRUE.equals(request.careerPreferred()));
    }
}
