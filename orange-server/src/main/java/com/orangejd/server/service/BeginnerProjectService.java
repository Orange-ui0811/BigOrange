package com.orangejd.server.service;

import org.springframework.stereotype.Service;
import static com.orangejd.server.service.BeginnerProjectServiceValues.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.server.dto.BeginnerProjectView;
import com.orangejd.server.dto.BeginnerWeeklyPickView;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.external.TranslationCacheMapper;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class BeginnerProjectService {
    private static final Set<String> STATUSES = Set.of("CANDIDATE", "VERIFYING", "PUBLISHED", "HIDDEN", "INVALID");
    private static final String CACHE_PURPOSE = "BEGINNER_PROJECT_ENRICH_V3";
    private final BeginnerProjectMapper mapper;
    private final BeginnerGithubGateway github;
    private final BeginnerProjectScorer scorer;
    private final ObjectMapper objectMapper;
    private final ExternalAiClient aiClient;
    private final AiRuntimeConfigService configService;
    private final TranslationCacheMapper cacheMapper;
    private final BeginnerEditorialPolicy editorialPolicy;

    public BeginnerProjectService(BeginnerProjectMapper mapper,
                                  BeginnerGithubGateway github,
                                  BeginnerProjectScorer scorer,
                                  ObjectMapper objectMapper,
                                  ExternalAiClient aiClient,
                                  AiRuntimeConfigService configService,
                                  TranslationCacheMapper cacheMapper,
                                  BeginnerEditorialPolicy editorialPolicy) {
        this.mapper = mapper;
        this.github = github;
        this.scorer = scorer;
        this.objectMapper = objectMapper;
        this.aiClient = aiClient;
        this.configService = configService;
        this.cacheMapper = cacheMapper;
        this.editorialPolicy = editorialPolicy;
    }

    public List<String> discover(int limit) {
        return github.discoverRepositories(limit);
    }

    @Transactional
    public BeginnerProject inspectAndStore(String fullName, Long runId) {
        BeginnerGithubGateway.RepositorySnapshot snapshot = github.inspectRepository(fullName);
        return inspectAndStore(snapshot, runId);
    }

    @Transactional
    public BeginnerProject inspectAndStore(BeginnerGithubGateway.RepositorySnapshot snapshot, Long runId) {
        BeginnerProjectScorer.ScoreResult score = scorer.score(snapshot);
        BeginnerProject project = toEntity(snapshot, score, runId);
        mapper.upsertMetadata(project);
        BeginnerProject stored = mapper.selectByFullName(snapshot.fullName());
        mapper.deleteEvidence(stored.getId(), BeginnerProjectScorer.SCORE_VERSION);
        for (BeginnerProjectScorer.Evidence evidence : score.evidence()) {
            mapper.insertEvidence(stored.getId(), runId, BeginnerProjectScorer.SCORE_VERSION,
                    evidence.dimensionKey(), evidence.score(), evidence.maxScore(), evidence.evidenceText());
        }
        return mapper.selectById(stored.getId());
    }

    public EnrichmentResult enrichPending(int limit, String configVersion) {
        List<BeginnerProject> projects = mapper.selectPendingEnrichment(Math.max(1, Math.min(limit, 10)));
        if (projects.isEmpty()) return new EnrichmentResult(0, 0, List.of());
        AiRuntimeConfig config = configService.resolveText(configVersion);
        String requestJson = enrichmentInput(projects);
        String hash = sha256(requestJson);
        String response = cacheMapper.find(hash, CACHE_PURPOSE, config.model());
        if (response != null) {
            cacheMapper.touch(hash, CACHE_PURPOSE, config.model());
        } else {
            response = aiClient.call(config,
                    "You evaluate verified GitHub repositories for Chinese Vibe Coding beginners. "
                            + "Return strict JSON only. Never change ids, repository names, facts or URLs. "
                            + "A library or infrastructure package is not showcase-friendly merely because installation is easy. "
                            + "SDKs, protocol implementations, release automation, dependency utilities and headless libraries "
                            + "must score below 4 for showcaseValue unless the README proves a standalone visual demo. "
                            + "Prefer one-command visual tools, templates and projects with obvious before-and-after results. "
                            + "Judge whether a beginner can understand and modify this repository's source, not whether its product is easy to use. "
                            + "A score of 5 is rare and requires explicit repository evidence; do not assign 5 to every dimension. "
                            + "Compare projects in the batch and use integer scores 1-5 consistently.",
                    "为以下项目生成 JSON 数组。每项必须包含 id、descriptionZh、whatIsIt、beginnerReason、"
                            + "modificationIdeas、mainRisks、finalRecommendation、estimatedDuration，以及整数评分 "
                            + "vibeCodingFit、showcaseValue、extensionPotential、scopeClarity、contentPotential。\n"
                            + "评分量表：5=新手可快速做出明显、可演示且适合讲解的成果；4=较适合但有少量门槛；"
                            + "3=可学习但成品感或改造空间一般；2=偏底层/庞大/依赖较重；1=不建议新手上手。\n"
                            + "descriptionZh 必须用 12-30 个中文字符说清能做出的具体成果，禁止只写‘开源项目’‘开发工具’等空话；"
                            + "whatIsIt 用不超过两句话的通俗中文解释它是什么；beginnerReason 分 3-5 行说明具体上手条件和学习价值；"
                            + "modificationIdeas 分行给出 2-4 个能形成作品的改造方向；mainRisks 必须指出主要风险；"
                            + "finalRecommendation 给出明确结论；estimatedDuration 例如“几小时体验，2～5 天做出定制版本”。\n"
                            + requestJson,
                    0.2, "beginner-project-enrichment");
            cacheMapper.save(hash, CACHE_PURPOSE, config.model(), response);
        }
        Map<Long, Enrichment> values = parseEnrichment(response);
        int success = 0;
        List<Long> missing = new ArrayList<>();
        for (BeginnerProject project : projects) {
            Enrichment value = values.get(project.getId());
            if (value == null || !StringUtils.hasText(value.descriptionZh())
                    || !StringUtils.hasText(value.whatIsIt())
                    || !StringUtils.hasText(value.beginnerReason())
                    || !StringUtils.hasText(value.modificationIdeas())
                    || !StringUtils.hasText(value.mainRisks())
                    || !StringUtils.hasText(value.finalRecommendation())) {
                missing.add(project.getId());
                continue;
            }
            BeginnerEditorialPolicy.Ratings ratings = editorialPolicy.calibrate(
                    ratings(value), editorialSignals(project));
            int editorialScore = editorialPolicy.score(ratings);
            boolean publishable = editorialPolicy.publishable(project.getBeginnerScore(), ratings);
            mapper.updateEnrichment(project.getId(), abbreviate(value.descriptionZh(), 1900),
                    abbreviate(value.whatIsIt(), 2900), abbreviate(value.beginnerReason(), 2900),
                    abbreviate(value.modificationIdeas(), 2900), abbreviate(value.mainRisks(), 2900),
                    abbreviate(value.finalRecommendation(), 900), abbreviate(value.estimatedDuration(), 60),
                    ratings.vibeCodingFit(), ratings.showcaseValue(), ratings.extensionPotential(),
                    ratings.scopeClarity(), ratings.contentPotential(), editorialScore,
                    publishable ? "PUBLISHED" : "CANDIDATE");
            success++;
        }
        return new EnrichmentResult(success, projects.size() - success, missing);
    }

    public PageResult<BeginnerProjectView> publicList(String keyword, String language,
                                                       String difficulty, String topic,
                                                       int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        List<BeginnerProjectView> records = mapper.selectPublic(clean(keyword), clean(language),
                clean(difficulty), clean(topic), (safePage - 1) * safeSize, safeSize)
                .stream().map(this::view).toList();
        return new PageResult<>(mapper.countPublic(clean(keyword), clean(language), clean(difficulty), clean(topic)), records);
    }

    public PageResult<BeginnerProjectView> adminList(String keyword, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<BeginnerProjectView> records = mapper.selectAdmin(clean(keyword), clean(status),
                (safePage - 1) * safeSize, safeSize).stream().map(this::view).toList();
        return new PageResult<>(mapper.countAdmin(clean(keyword), clean(status)), records);
    }

    public Map<String, Object> detail(Long id, boolean admin) {
        BeginnerProject project = mapper.selectById(id);
        if (project == null || (!admin && !"PUBLISHED".equals(project.getStatus()))) {
            throw new BaseException("项目不存在或尚未公开");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", view(project));
        result.put("evidence", admin ? mapper.selectEvidence(id) : List.of());
        return result;
    }

    public void updateStatus(Long id, String status, String hiddenReason) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new BaseException("无效的项目状态");
        BeginnerProject project = mapper.selectById(id);
        if (project == null) throw new BaseException("项目不存在");
        mapper.updateStatus(id, normalized, "HIDDEN".equals(normalized) ? abbreviate(hiddenReason, 500) : null);
    }

    @Transactional
    public List<BeginnerProjectView> rebuildWeeklyPicks(String source) {
        LocalDate weekStart = currentWeekStart();
        if (!"ADMIN".equals(source) && "ADMIN".equals(mapper.selectWeeklyPickSource(weekStart))) {
            List<BeginnerProject> manualProjects = mapper.selectWeeklyPicks(weekStart);
            if (manualProjects.size() == 3) {
                return manualProjects.stream().map(this::view).toList();
            }
        }
        List<BeginnerProject> projects = mapper.selectTopForWeekly(3);
        mapper.deleteWeeklyPicks(weekStart);
        for (int i = 0; i < projects.size(); i++) {
            mapper.insertWeeklyPick(weekStart, projects.get(i).getId(), i + 1,
                    "ADMIN".equals(source) ? "ADMIN" : "ALGORITHM");
        }
        return projects.stream().map(this::view).toList();
    }

    @Transactional
    public BeginnerWeeklyPickView replaceWeeklyPicks(List<Long> projectIds) {
        if (projectIds == null || projectIds.size() != 3 || new HashSet<>(projectIds).size() != 3) {
            throw new BaseException("本周精选必须选择 3 个不重复的项目");
        }
        List<BeginnerProject> projects = new ArrayList<>();
        for (Long id : projectIds) {
            BeginnerProject project = mapper.selectById(id);
            if (project == null) throw new BaseException("精选项目不存在");
            if (!"PUBLISHED".equals(project.getStatus())) {
                throw new BaseException(project.getFullName() + " 尚未设为公开状态");
            }
            projects.add(project);
        }
        LocalDate weekStart = currentWeekStart();
        mapper.deleteWeeklyPicks(weekStart);
        for (int i = 0; i < projects.size(); i++) {
            mapper.insertWeeklyPick(weekStart, projects.get(i).getId(), i + 1, "ADMIN");
        }
        return new BeginnerWeeklyPickView(weekStart, "ADMIN",
                projects.stream().map(this::view).toList());
    }

    public BeginnerWeeklyPickView weeklyPickAdminView() {
        LocalDate weekStart = currentWeekStart();
        List<BeginnerProject> projects = mapper.selectWeeklyPicks(weekStart);
        String source = mapper.selectWeeklyPickSource(weekStart);
        return new BeginnerWeeklyPickView(weekStart, source == null ? "NONE" : source,
                projects.stream().map(this::view).toList());
    }

    @Transactional
    public BeginnerWeeklyPickView restoreAlgorithmWeeklyPicks() {
        LocalDate weekStart = currentWeekStart();
        mapper.deleteWeeklyPicks(weekStart);
        rebuildWeeklyPicks("ALGORITHM");
        return weeklyPickAdminView();
    }

    public List<BeginnerProjectView> weeklyPicks() {
        List<BeginnerProject> projects = mapper.selectWeeklyPicks(currentWeekStart());
        if (projects.isEmpty()) return rebuildWeeklyPicks("ALGORITHM");
        return projects.stream().map(this::view).toList();
    }

    public List<BeginnerProjectView> recommend(List<String> topics, List<String> languages, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        List<BeginnerProject> projects = mapper.selectRecommended(cleanList(topics), cleanList(languages), safeLimit);
        if (projects.isEmpty() && languages != null && !languages.isEmpty()) {
            projects = mapper.selectRecommended(cleanList(topics), List.of(), safeLimit);
        }
        if (projects.isEmpty()) projects = mapper.selectRecommended(List.of(), List.of(), safeLimit);
        return projects.stream().map(this::view).toList();
    }

    /** Strict project-recommendation path: never falls back to an unrelated popular project. */
    public List<BeginnerProjectView> recommendVerified(List<String> topics, List<String> languages, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        List<BeginnerProject> projects = mapper.selectAllPublished();
        if (projects == null) return List.of();
        return projects.stream()
                .map(project -> Map.entry(project, TrendCandidateMatcher.score(topics, languages,
                        String.join(" ", nullToEmpty(project.getFullName()), nullToEmpty(project.getDescriptionZh()),
                                nullToEmpty(project.getDescriptionOriginal()), nullToEmpty(project.getWhatIsIt()),
                                nullToEmpty(project.getTechStack()), nullToEmpty(project.getTopicsJson())),
                        project.getPrimaryLanguage())))
                .filter(entry -> entry.getValue() >= 0)
                .sorted(Comparator.<Map.Entry<BeginnerProject, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparingInt(entry -> -value(entry.getKey().getEditorialScore()))
                        .thenComparingInt(entry -> -value(entry.getKey().getBeginnerScore()))
                        .thenComparing(entry -> entry.getKey().getFullName()))
                .limit(safeLimit).map(entry -> view(entry.getKey())).toList();
    }

    private BeginnerProject toEntity(BeginnerGithubGateway.RepositorySnapshot value,
                                     BeginnerProjectScorer.ScoreResult score, Long runId) {
        BeginnerProject project = new BeginnerProject();
        project.setFullName(value.fullName());
        project.setOwnerName(value.ownerName());
        project.setRepositoryName(value.repositoryName());
        project.setHtmlUrl(value.htmlUrl());
        project.setDescriptionOriginal(value.description());
        project.setReadmeExcerpt(value.readmeExcerpt());
        project.setPrimaryLanguage(value.primaryLanguage());
        project.setTopicsJson(json(objectMapper, value.topics()));
        project.setTechStack(value.techStack());
        project.setPackageManager(value.packageManager());
        project.setNodeVersion(value.nodeVersion());
        project.setLicenseSpdx(value.licenseSpdx());
        project.setStars(value.stars());
        project.setForks(value.forks());
        project.setOpenIssues(value.openIssues());
        project.setArchived(value.archived());
        project.setHasReadme(value.hasReadme());
        project.setHasQuickStart(value.hasQuickStart());
        project.setSetupSteps(value.setupSteps());
        project.setRequiresDocker(value.requiresDocker());
        project.setRequiresDatabase(value.requiresDatabase());
        project.setRequiresSecrets(value.requiresSecrets());
        project.setRequiresExternalService(value.requiresExternalService());
        project.setRequiresGpu(value.requiresGpu());
        project.setRequiresNativeBuild(value.requiresNativeBuild());
        project.setCodebaseSize(value.codebaseSize());
        project.setDifficulty(score.difficulty());
        project.setBeginnerScore(score.score());
        project.setScoreVersion(BeginnerProjectScorer.SCORE_VERSION);
        project.setStatus(score.status());
        project.setContentHash(sha256(json(objectMapper, value)));
        project.setGithubCreatedAt(value.githubCreatedAt());
        project.setGithubUpdatedAt(value.githubUpdatedAt());
        project.setSourceRunId(runId);
        return project;
    }

    private Map<Long, Enrichment> parseEnrichment(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode array = root.isArray() ? root : root.path("projects");
            Map<Long, Enrichment> result = new LinkedHashMap<>();
            if (!array.isArray()) return result;
            for (JsonNode item : array) {
                long id = item.path("id").asLong(0);
                if (id <= 0) continue;
                result.put(id, new Enrichment(id, item.path("descriptionZh").asText(""),
                        item.path("whatIsIt").asText(""), item.path("beginnerReason").asText(""),
                        item.path("modificationIdeas").asText(""), item.path("mainRisks").asText(""),
                        item.path("finalRecommendation").asText(""), item.path("estimatedDuration").asText(""),
                        rating(item, "vibeCodingFit"), rating(item, "showcaseValue"),
                        rating(item, "extensionPotential"), rating(item, "scopeClarity"),
                        rating(item, "contentPotential")));
            }
            return result;
        } catch (Exception e) {
            throw new BaseException("AI 返回的新手项目说明格式无效");
        }
    }

    private String enrichmentInput(List<BeginnerProject> projects) {
        List<Map<String, Object>> input = projects.stream().map(this::enrichmentItem).toList();
        return json(objectMapper, input);
    }

    private Map<String, Object> enrichmentItem(BeginnerProject project) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", project.getId());
        item.put("fullName", project.getFullName());
        item.put("description", project.getDescriptionOriginal() == null ? "" : project.getDescriptionOriginal());
        item.put("language", project.getPrimaryLanguage() == null ? "" : project.getPrimaryLanguage());
        item.put("techStack", project.getTechStack() == null ? "" : project.getTechStack());
        item.put("score", project.getBeginnerScore());
        item.put("license", project.getLicenseSpdx() == null ? "" : project.getLicenseSpdx());
        item.put("quickStart", Boolean.TRUE.equals(project.getHasQuickStart()));
        item.put("setupSteps", project.getSetupSteps() == null ? 0 : project.getSetupSteps());
        item.put("codebaseSize", project.getCodebaseSize() == null ? "" : project.getCodebaseSize());
        item.put("requiresDocker", Boolean.TRUE.equals(project.getRequiresDocker()));
        item.put("requiresDatabase", Boolean.TRUE.equals(project.getRequiresDatabase()));
        item.put("requiresSecrets", Boolean.TRUE.equals(project.getRequiresSecrets()));
        item.put("requiresGpu", Boolean.TRUE.equals(project.getRequiresGpu()));
        item.put("readmeExcerpt", project.getReadmeExcerpt() == null ? "" : abbreviate(project.getReadmeExcerpt(), 6000));
        return item;
    }

    private BeginnerEditorialPolicy.Signals editorialSignals(BeginnerProject project) {
        String text = String.join(" ", nullToEmpty(project.getDescriptionOriginal()),
                nullToEmpty(project.getReadmeExcerpt()), nullToEmpty(project.getTechStack()),
                nullToEmpty(project.getTopicsJson()));
        return new BeginnerEditorialPolicy.Signals(value(project.getBeginnerScore()),
                Boolean.TRUE.equals(project.getHasQuickStart()), project.getCodebaseSize(),
                Boolean.TRUE.equals(project.getRequiresDocker()),
                Boolean.TRUE.equals(project.getRequiresDatabase()),
                Boolean.TRUE.equals(project.getRequiresSecrets()),
                Boolean.TRUE.equals(project.getRequiresExternalService()),
                Boolean.TRUE.equals(project.getRequiresGpu()),
                Boolean.TRUE.equals(project.getRequiresNativeBuild()), text);
    }

    private BeginnerProjectView view(BeginnerProject project) {
        try {
            List<String> topics = objectMapper.readValue(project.getTopicsJson() == null ? "[]" : project.getTopicsJson(),
                    new TypeReference<>() {});
            return BeginnerProjectView.from(project, topics);
        } catch (Exception ignored) {
            return BeginnerProjectView.from(project, List.of());
        }
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private int rating(JsonNode item, String field) { return Math.max(1, Math.min(5, item.path(field).asInt(1))); }
    private BeginnerEditorialPolicy.Ratings ratings(Enrichment value) {
        return new BeginnerEditorialPolicy.Ratings(value.vibeCodingFit(), value.showcaseValue(),
                value.extensionPotential(), value.scopeClarity(), value.contentPotential());
    }

    private record Enrichment(Long id, String descriptionZh, String whatIsIt, String beginnerReason,
                              String modificationIdeas, String mainRisks, String finalRecommendation,
                              String estimatedDuration, int vibeCodingFit, int showcaseValue,
                              int extensionPotential, int scopeClarity, int contentPotential) {}
    public record EnrichmentResult(int success, int failed, List<Long> missingIds) {}
}
