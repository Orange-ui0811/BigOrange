package com.orangejd.server.service;

import org.springframework.stereotype.Service;
import static com.orangejd.server.service.CareerProjectServiceValues.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.pojo.entity.CareerRoleScore;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.dto.CareerProjectView;
import com.orangejd.server.dto.CareerRoleScoreView;
import com.orangejd.server.dto.CareerWeeklyPickView;
import com.orangejd.server.foundation.external.AiRuntimeConfig;
import com.orangejd.server.foundation.external.AiRuntimeConfigService;
import com.orangejd.server.foundation.external.ExternalAiClient;
import com.orangejd.server.foundation.external.TranslationCacheMapper;
import com.orangejd.server.mapper.CareerProjectMapper;
import com.orangejd.server.mapper.JobPostingMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CareerProjectService {
    private static final Set<String> STATUSES = Set.of("CANDIDATE", "VERIFYING", "PUBLISHED", "HIDDEN", "INVALID");
    private static final String CACHE_PURPOSE = "CAREER_PROJECT_ENRICH_V1";

    private final CareerProjectMapper mapper;
    private final BeginnerProjectService beginnerService;
    private final BeginnerGithubGateway github;
    private final CareerProjectScorer scorer;
    private final CareerRoleTaxonomy taxonomy;
    private final JobPostingMapper jobMapper;
    private final ObjectMapper objectMapper;
    private final ExternalAiClient aiClient;
    private final AiRuntimeConfigService configService;
    private final TranslationCacheMapper cacheMapper;
    private final JobMatchService jobMatchService;

    public CareerProjectService(CareerProjectMapper mapper,
                                BeginnerProjectService beginnerService,
                                BeginnerGithubGateway github,
                                CareerProjectScorer scorer,
                                CareerRoleTaxonomy taxonomy,
                                JobPostingMapper jobMapper,
                                ObjectMapper objectMapper,
                                 ExternalAiClient aiClient,
                                 AiRuntimeConfigService configService,
                                 TranslationCacheMapper cacheMapper,
                                 JobMatchService jobMatchService) {
        this.mapper = mapper;
        this.beginnerService = beginnerService;
        this.github = github;
        this.scorer = scorer;
        this.taxonomy = taxonomy;
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
        this.aiClient = aiClient;
        this.configService = configService;
        this.cacheMapper = cacheMapper;
        this.jobMatchService = jobMatchService;
    }

    public List<String> discover(int limit) {
        return github.discoverCareerRepositories(limit);
    }

    @Transactional
    public CareerProjectProfile inspectAndStore(String fullName, Long runId) {
        BeginnerGithubGateway.RepositorySnapshot snapshot = github.inspectRepository(fullName);
        BeginnerProject shared = beginnerService.inspectAndStore(snapshot, runId);
        CareerProjectScorer.ScoreResult score = scorer.score(snapshot, jobSampleCounts());
        CareerProjectProfile profile = new CareerProjectProfile();
        profile.setProjectId(shared.getId());
        profile.setOverallScore(score.overallScore());
        profile.setBestRoleCode(score.bestRoleCode());
        profile.setDifficulty(score.difficulty());
        profile.setStatus(score.status());
        profile.setScoreVersion(CareerProjectScorer.SCORE_VERSION);
        profile.setTaxonomyVersion(CareerRoleTaxonomy.VERSION);
        profile.setContentHash(shared.getContentHash());
        profile.setSourceRunId(runId);
        mapper.upsertProfile(profile);
        mapper.deleteRoleScores(shared.getId());
        for (CareerProjectScorer.RoleScore value : score.roleScores()) {
            CareerRoleScore role = new CareerRoleScore();
            role.setProjectId(shared.getId());
            role.setRoleCode(value.roleCode());
            role.setRoleRelevance(value.roleRelevance());
            role.setProvableAbility(value.provableAbility());
            role.setKnowledgeCoverage(value.knowledgeCoverage());
            role.setPortfolioCompleteness(value.portfolioCompleteness());
            role.setInterviewValue(value.interviewValue());
            role.setTotalScore(value.totalScore());
            role.setJobSampleCount(value.jobSampleCount());
            role.setEvidenceJson(json(objectMapper, value.evidence()));
            role.setScoreVersion(CareerProjectScorer.SCORE_VERSION);
            mapper.insertRoleScore(role);
        }
        return mapper.selectById(shared.getId());
    }

    public EnrichmentResult enrichPending(int limit, String configVersion) {
        List<CareerProjectProfile> projects = mapper.selectPendingEnrichment(Math.max(1, Math.min(limit, 10)));
        if (projects.isEmpty()) return new EnrichmentResult(0, 0, List.of());
        AiRuntimeConfig config = configService.resolveText(configVersion);
        String input = json(objectMapper, projects.stream().map(this::enrichmentInput).toList());
        String hash = sha256(input);
        String response = cacheMapper.find(hash, CACHE_PURPOSE, config.model());
        if (response == null) {
            response = aiClient.call(config,
                    "You are a senior engineering career mentor. Explain verified GitHub repositories for Chinese job seekers. "
                            + "Use only supplied facts. Return strict JSON. Do not invent capabilities, commands, employers or URLs.",
                    "为每个项目生成 JSON 数组。必须保留 id，并包含 summaryZh、careerValue、proofPoints、"
                            + "interviewTopics、modificationPlan、mainRisks、estimatedDuration。"
                            + "说明用户做完后能证明什么能力、面试时能讨论什么，以及如何做出差异化作品。\n" + input,
                    0.2, "career-project-enrichment");
            cacheMapper.save(hash, CACHE_PURPOSE, config.model(), response);
        } else {
            cacheMapper.touch(hash, CACHE_PURPOSE, config.model());
        }
        Map<Long, Enrichment> values = parseEnrichment(response);
        int success = 0;
        List<Long> missing = new ArrayList<>();
        for (CareerProjectProfile project : projects) {
            Enrichment value = values.get(project.getProjectId());
            if (value == null || !value.complete()) {
                missing.add(project.getProjectId());
                continue;
            }
            String status = project.getOverallScore() != null && project.getOverallScore() >= 65
                    && !Boolean.TRUE.equals(project.getArchived()) && Boolean.TRUE.equals(project.getHasReadme())
                    ? "PUBLISHED" : "CANDIDATE";
            mapper.updateEnrichment(project.getProjectId(), abbreviate(value.summaryZh(), 1800),
                    abbreviate(value.careerValue(), 4000), abbreviate(value.proofPoints(), 4000),
                    abbreviate(value.interviewTopics(), 4000), abbreviate(value.modificationPlan(), 5000),
                    abbreviate(value.mainRisks(), 3000), abbreviate(value.estimatedDuration(), 80), status);
            success++;
        }
        return new EnrichmentResult(success, projects.size() - success, missing);
    }

    public PageResult<CareerProjectView> publicList(String keyword, String language, String difficulty,
                                                     String roleCode, int page, int size) {
        validateRoleOptional(roleCode);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        List<CareerProjectView> values = mapper.selectPublic(clean(keyword), clean(language), clean(difficulty),
                clean(roleCode), (safePage - 1) * safeSize, safeSize).stream().map(this::view).toList();
        return new PageResult<>(mapper.countPublic(clean(keyword), clean(language), clean(difficulty), clean(roleCode)), values);
    }

    public PageResult<CareerProjectView> adminList(String keyword, String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<CareerProjectView> values = mapper.selectAdmin(clean(keyword), clean(status),
                (safePage - 1) * safeSize, safeSize).stream().map(this::view).toList();
        return new PageResult<>(mapper.countAdmin(clean(keyword), clean(status)), values);
    }

    public CareerProjectView detail(Long id, boolean admin) {
        CareerProjectProfile profile = mapper.selectById(id);
        if (profile == null || (!admin && !"PUBLISHED".equals(profile.getStatus()))) {
            throw new BaseException("项目不存在或尚未公开");
        }
        return view(profile);
    }

    public List<CareerRoleTaxonomy.RoleCategory> roleCategories() {
        return taxonomy.categories();
    }

    public List<JobPosting> jobs(String roleCode) {
        validateRole(roleCode);
        List<JobPosting> result = new ArrayList<>();
        for (String direction : jobMapper.selectAllDirections()) {
            if (roleCode.equals(taxonomy.normalizeDirection(direction))) {
                result.addAll(jobMapper.selectApprovedByDirection(direction));
            }
        }
        result.sort(Comparator.comparing(JobPosting::getId).reversed());
        return result;
    }

    public Map<String, Object> assessJob(Long projectId, String roleCode, Long jobId) {
        CareerProjectView project = detail(projectId, false);
        validateRole(roleCode);
        StringBuilder content = new StringBuilder()
                .append("项目：").append(project.fullName()).append('\n')
                .append("项目说明：").append(safe(project.summaryZh())).append('\n')
                .append("技术栈：").append(safe(project.techStack())).append('\n')
                .append("职业价值：").append(safe(project.careerValue())).append('\n')
                .append("目标岗位：").append(roleName(taxonomy, roleCode)).append('\n');
        if (jobId != null) {
            JobPosting job = jobMapper.selectById(jobId);
            if (job == null || !"APPROVED".equals(job.getAuditStatus())
                    || !roleCode.equals(taxonomy.normalizeDirection(job.getDirection()))) {
                throw new BaseException("所选岗位不存在或不属于目标岗位类别");
            }
            content.append("具体岗位：").append(job.getPosition()).append('\n')
                    .append("岗位要求：").append(safe(job.getDisplayContent())).append('\n');
        }
        return jobMatchService.match(content.toString(), "ai:match", "职业项目岗位评估");
    }

    public void updateStatus(Long id, String status, String hiddenReason) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new BaseException("无效的项目状态");
        if (mapper.selectById(id) == null) throw new BaseException("项目不存在");
        mapper.updateStatus(id, normalized, "HIDDEN".equals(normalized) ? abbreviate(hiddenReason, 500) : null);
    }

    @Transactional
    public List<CareerProjectView> rebuildWeeklyPicks(String source) {
        LocalDate weekStart = currentWeekStart();
        if (!"ADMIN".equals(source) && "ADMIN".equals(mapper.selectWeeklyPickSource(weekStart))) {
            List<CareerProjectProfile> manual = mapper.selectWeeklyPicks(weekStart);
            if (manual.size() == 3) return manual.stream().map(this::view).toList();
        }
        List<CareerProjectProfile> projects = mapper.selectTopForWeekly(3);
        mapper.deleteWeeklyPicks(weekStart);
        for (int i = 0; i < projects.size(); i++) {
            mapper.insertWeeklyPick(weekStart, projects.get(i).getProjectId(), i + 1,
                    "ADMIN".equals(source) ? "ADMIN" : "ALGORITHM");
        }
        return projects.stream().map(this::view).toList();
    }

    @Transactional
    public CareerWeeklyPickView replaceWeeklyPicks(List<Long> ids) {
        if (ids == null || ids.size() != 3 || new HashSet<>(ids).size() != 3) {
            throw new BaseException("本周精选必须选择 3 个不重复的项目");
        }
        List<CareerProjectProfile> projects = new ArrayList<>();
        for (Long id : ids) {
            CareerProjectProfile project = mapper.selectById(id);
            if (project == null || !"PUBLISHED".equals(project.getStatus())) {
                throw new BaseException("精选项目不存在或尚未公开");
            }
            projects.add(project);
        }
        LocalDate weekStart = currentWeekStart();
        mapper.deleteWeeklyPicks(weekStart);
        for (int i = 0; i < projects.size(); i++) {
            mapper.insertWeeklyPick(weekStart, projects.get(i).getProjectId(), i + 1, "ADMIN");
        }
        return weeklyAdminView();
    }

    public CareerWeeklyPickView weeklyAdminView() {
        LocalDate weekStart = currentWeekStart();
        String source = mapper.selectWeeklyPickSource(weekStart);
        return new CareerWeeklyPickView(weekStart, source == null ? "NONE" : source,
                mapper.selectWeeklyPicks(weekStart).stream().map(this::view).toList());
    }

    @Transactional
    public CareerWeeklyPickView restoreAlgorithmWeeklyPicks() {
        mapper.deleteWeeklyPicks(currentWeekStart());
        rebuildWeeklyPicks("ALGORITHM");
        return weeklyAdminView();
    }

    public List<CareerProjectView> weeklyPicks() {
        List<CareerProjectProfile> projects = mapper.selectWeeklyPicks(currentWeekStart());
        if (projects.isEmpty()) return rebuildWeeklyPicks("ALGORITHM");
        return projects.stream().map(this::view).toList();
    }

    public List<CareerProjectView> recommend(List<String> topics, List<String> languages, String purpose, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        Set<String> terms = new HashSet<>();
        if (topics != null) topics.stream().filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(terms::add);
        if (languages != null) languages.stream().filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(terms::add);
        if (StringUtils.hasText(purpose)) terms.add(purpose.toLowerCase(Locale.ROOT));
        return mapper.selectPublic(null, null, null, null, 0, 100).stream()
                .sorted(Comparator
                        .comparingInt((CareerProjectProfile project) -> recommendationFit(project, terms)).reversed()
                        .thenComparing(project -> value(project.getOverallScore()), Comparator.reverseOrder())
                        .thenComparing(CareerProjectProfile::getFullName))
                .limit(safeLimit)
                .map(this::view)
                .toList();
    }

    /** Strict project-recommendation path: a topic match is required when topics were provided. */
    public List<CareerProjectView> recommendVerified(List<String> topics, List<String> languages,
                                                     String purpose, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        List<CareerProjectProfile> projects = mapper.selectPublic(null, null, null, null, 0, 200);
        if (projects == null) return List.of();
        return projects.stream()
                .map(project -> Map.entry(project, TrendCandidateMatcher.score(topics, languages,
                        String.join(" ", safe(project.getFullName()), safe(project.getPrimaryLanguage()),
                                safe(project.getTechStack()), safe(project.getDescriptionOriginal()),
                                safe(project.getDescriptionZh()), safe(project.getSummaryZh()),
                                safe(project.getCareerValue()), safe(project.getProofPoints())),
                        project.getPrimaryLanguage())))
                .filter(entry -> entry.getValue() >= 0)
                .sorted(Comparator.<Map.Entry<CareerProjectProfile, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparingInt(entry -> -value(entry.getKey().getOverallScore()))
                        .thenComparing(entry -> entry.getKey().getFullName()))
                .limit(safeLimit).map(entry -> view(entry.getKey())).toList();
    }

    private int recommendationFit(CareerProjectProfile project, Set<String> terms) {
        String text = String.join(" ", safe(project.getFullName()), safe(project.getPrimaryLanguage()),
                safe(project.getTechStack()), safe(project.getDescriptionOriginal()), safe(project.getCareerValue()))
                .toLowerCase(Locale.ROOT);
        int matches = (int) terms.stream().filter(text::contains).count();
        int fit = matches * 18 + value(project.getOverallScore());
        if (text.contains("agent") && terms.stream().anyMatch(term -> term.contains("智能体") || term.contains("ai"))) fit += 12;
        if (text.contains("react") && terms.stream().anyMatch(term -> term.contains("前端") || term.contains("web"))) fit += 10;
        return fit;
    }

    private CareerProjectView view(CareerProjectProfile profile) {
        List<CareerRoleScoreView> roles = mapper.selectRoleScores(profile.getProjectId()).stream()
                .map(score -> new CareerRoleScoreView(score.getRoleCode(), roleName(taxonomy, score.getRoleCode()),
                        value(score.getRoleRelevance()), value(score.getProvableAbility()),
                        value(score.getKnowledgeCoverage()), value(score.getPortfolioCompleteness()),
                        value(score.getInterviewValue()), value(score.getTotalScore()),
                        value(score.getJobSampleCount()), score.getEvidenceJson()))
                .toList();
        return new CareerProjectView(profile.getProjectId(), profile.getFullName(), profile.getOwnerName(),
                profile.getRepositoryName(), profile.getHtmlUrl(), profile.getDescriptionOriginal(),
                profile.getDescriptionZh(), profile.getPrimaryLanguage(), profile.getTechStack(),
                profile.getLicenseSpdx(), value(profile.getStars()), Boolean.TRUE.equals(profile.getHasQuickStart()),
                Boolean.TRUE.equals(profile.getRequiresDocker()), Boolean.TRUE.equals(profile.getRequiresDatabase()),
                Boolean.TRUE.equals(profile.getRequiresSecrets()), Boolean.TRUE.equals(profile.getRequiresExternalService()),
                profile.getCodebaseSize(), value(profile.getOverallScore()), profile.getBestRoleCode(),
                roleName(taxonomy, profile.getBestRoleCode()), profile.getDifficulty(), profile.getSummaryZh(),
                profile.getCareerValue(), profile.getProofPoints(), profile.getInterviewTopics(),
                profile.getModificationPlan(), profile.getMainRisks(), profile.getEstimatedDuration(),
                profile.getStatus(), profile.getGithubUpdatedAt(), profile.getLastVerifiedAt(), roles);
    }

    private Map<String, Integer> jobSampleCounts() {
        Map<String, Integer> directions = new LinkedHashMap<>();
        for (String direction : jobMapper.selectAllDirections()) {
            directions.put(direction, jobMapper.selectApprovedByDirection(direction).size());
        }
        return taxonomy.aggregateSamples(directions);
    }

    private Map<String, Object> enrichmentInput(CareerProjectProfile project) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", project.getProjectId());
        input.put("fullName", project.getFullName());
        input.put("description", safe(project.getDescriptionOriginal()));
        input.put("language", safe(project.getPrimaryLanguage()));
        input.put("techStack", safe(project.getTechStack()));
        input.put("difficulty", project.getDifficulty());
        input.put("overallScore", project.getOverallScore());
        input.put("bestRole", roleName(taxonomy, project.getBestRoleCode()));
        input.put("roleScores", mapper.selectRoleScores(project.getProjectId()));
        return input;
    }

    private Map<Long, Enrichment> parseEnrichment(String response) {
        try {
            JsonNode root = objectMapper.readTree(response.replaceAll("```json\\s*", "").replaceAll("```", "").trim());
            JsonNode items = root.isArray() ? root : root.path("projects");
            Map<Long, Enrichment> result = new LinkedHashMap<>();
            if (!items.isArray()) return result;
            for (JsonNode item : items) {
                long id = item.path("id").asLong(0);
                if (id <= 0) continue;
                result.put(id, new Enrichment(id, content(item, "summaryZh"), content(item, "careerValue"),
                        content(item, "proofPoints"), content(item, "interviewTopics"),
                        content(item, "modificationPlan"), content(item, "mainRisks"),
                        content(item, "estimatedDuration")));
            }
            return result;
        } catch (Exception e) {
            throw new BaseException("AI 返回的职业项目说明格式无效");
        }
    }

    static String content(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isTextual() || value.isNumber() || value.isBoolean()) return value.asText("").trim();
        if (value.isArray()) {
            List<String> lines = new ArrayList<>();
            value.forEach(item -> {
                String line = item.asText("").trim();
                if (StringUtils.hasText(line)) lines.add("- " + line);
            });
            return String.join("\n", lines);
        }
        return "";
    }

    private void validateRoleOptional(String roleCode) { if (StringUtils.hasText(roleCode)) validateRole(roleCode); }
    private void validateRole(String roleCode) {
        if (taxonomy.categories().stream().noneMatch(item -> item.code().equals(roleCode))) {
            throw new BaseException("无效的岗位类别");
        }
    }

    private record Enrichment(Long id, String summaryZh, String careerValue, String proofPoints,
                              String interviewTopics, String modificationPlan, String mainRisks,
                              String estimatedDuration) {
        boolean complete() {
            return StringUtils.hasText(summaryZh) && StringUtils.hasText(careerValue)
                    && StringUtils.hasText(proofPoints) && StringUtils.hasText(interviewTopics)
                    && StringUtils.hasText(modificationPlan) && StringUtils.hasText(mainRisks);
        }
    }
    public record EnrichmentResult(int success, int failed, List<Long> missingIds) {}
}
