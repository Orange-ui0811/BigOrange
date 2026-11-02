package com.orangejd.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.foundation.external.GithubApiClient;
import com.orangejd.server.mapper.OpenSourceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OpenSourceGithubService implements BeginnerGithubGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenSourceGithubService.class);
    private static final String API = "https://api.github.com";
    private static final Pattern REPOSITORY_NAME = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern CLAIM_PATTERN = Pattern.compile(
            "(?i)(i(?:'| a)?m (?:going to |currently )?work(?:ing)? on|i(?:'| a)?d like to (?:take|work)|can i (?:take|work)|assign (?:this )?to me|/assign|working on this|我来处理|我想认领|正在处理)");
    private static final Set<String> RISK_WORDS = Set.of(
            "docker", "kubernetes", "database", "migration", "aws", "azure", "gcp", "credential",
            "secret", ".env", "production", "security", "golang", "rust", "php", "breaking change", "rfc");

    private final OpenSourceMapper mapper;
    private final ObjectMapper objectMapper;
    private final GithubApiClient githubApiClient;
    private final CareerDiscoveryCandidateSelector careerCandidateSelector;

    public OpenSourceGithubService(OpenSourceMapper mapper, ObjectMapper objectMapper,
                                   GithubApiClient githubApiClient,
                                   CareerDiscoveryCandidateSelector careerCandidateSelector) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.githubApiClient = githubApiClient;
        this.careerCandidateSelector = careerCandidateSelector;
    }

    @Override
    public List<String> discoverRepositories(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String recent = LocalDate.now().minusDays(45).toString();
        String newlyCreated = LocalDate.now().minusDays(21).toString();
        List<String> queries = List.of(
                "stars:>20 archived:false created:>" + newlyCreated + " language:TypeScript",
                "stars:>20 archived:false created:>" + newlyCreated + " language:JavaScript",
                "stars:>20 archived:false created:>" + newlyCreated + " language:Python",
                "stars:>30 archived:false pushed:>" + recent + " language:TypeScript",
                "stars:>30 archived:false pushed:>" + recent + " language:JavaScript",
                "stars:>20 archived:false pushed:>" + recent + " topic:ai-tools",
                "stars:>20 archived:false pushed:>" + recent + " topic:vibe-coding",
                "stars:>20 archived:false pushed:>" + recent + " topic:creative-coding",
                "stars:>20 archived:false pushed:>" + recent + " topic:developer-tools");
        Map<String, DiscoveryCandidate> candidates = new LinkedHashMap<>();
        int perQuery = Math.max(10, Math.min(30, (safeLimit + queries.size() - 1) / queries.size() + 8));
        for (String query : queries) {
            String sort = query.contains("created:>") ? "stars" : "updated";
            JsonNode response = getJson(API + "/search/repositories?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&sort=" + sort + "&order=desc&per_page=" + perQuery);
            JsonNode items = response.path("items");
            log.debug("Beginner repository discovery query completed: total={}, items={}, first={}",
                    response.path("total_count").asInt(0), items.size(),
                    items.isArray() && !items.isEmpty() ? items.get(0).path("full_name").asText("") : "none");
            for (JsonNode item : items) {
                String fullName = item.path("full_name").asText("");
                if (!REPOSITORY_NAME.matcher(fullName).matches()) continue;
                DiscoveryCandidate candidate = discoveryCandidate(item);
                candidates.merge(fullName, candidate,
                        (left, right) -> left.discoveryScore() >= right.discoveryScore() ? left : right);
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingInt(DiscoveryCandidate::discoveryScore).reversed()
                        .thenComparing(DiscoveryCandidate::fullName))
                .limit(safeLimit)
                .map(DiscoveryCandidate::fullName)
                .toList();
    }

    @Override
    public List<String> discoverCareerRepositories(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 120));
        List<CareerSearchProfile> profiles = List.of(
                new CareerSearchProfile("AGENT_NATIVE_APP", "agent-native in:name,description archived:false"),
                new CareerSearchProfile("KNOWLEDGE_TO_SKILL", "book skill in:name,description archived:false"),
                new CareerSearchProfile("AI_DESIGN_ASSISTANCE", "design AI harness in:name,description archived:false"),
                new CareerSearchProfile("AGENT_APPLICATION", "agent app framework in:name,description archived:false"),
                new CareerSearchProfile("AGENT_APPLICATION", "mcp agent app in:name,description archived:false"),
                new CareerSearchProfile("KNOWLEDGE_TOOL", "document skill generator in:name,description archived:false"),
                new CareerSearchProfile("KNOWLEDGE_TOOL", "knowledge AI tool in:name,description archived:false"),
                new CareerSearchProfile("AI_DESIGN_TOOL", "frontend design skill in:name,description archived:false"),
                new CareerSearchProfile("AI_DESIGN_TOOL", "UI audit AI in:name,description archived:false"),
                new CareerSearchProfile("ENGINEERING_QUALITY", "AI testing tool in:name,description archived:false"),
                new CareerSearchProfile("ENGINEERING_QUALITY", "LLM observability in:name,description archived:false"),
                new CareerSearchProfile("FULLSTACK_PRODUCT", "AI starter template in:name,description archived:false"),
                new CareerSearchProfile("FULLSTACK_PRODUCT", "RAG starter app in:name,description archived:false"));
        List<CareerDiscoveryCandidateSelector.Candidate> candidates = new ArrayList<>();
        for (CareerSearchProfile profile : profiles) {
            JsonNode items = getJson(API + "/search/repositories?q="
                    + URLEncoder.encode(profile.query(), StandardCharsets.UTF_8)
                    + "&order=desc&per_page=30").path("items");
            int rank = 0;
            for (JsonNode item : items) {
                rank++;
                String fullName = item.path("full_name").asText("");
                if (!REPOSITORY_NAME.matcher(fullName).matches()) continue;
                candidates.add(new CareerDiscoveryCandidateSelector.Candidate(
                        fullName, profile.category(), careerDiscoveryScore(item, rank)));
            }
        }
        return careerCandidateSelector.select(candidates, safeLimit);
    }

    private int careerDiscoveryScore(JsonNode item, int relevanceRank) {
        int stars = item.path("stargazers_count").asInt(0);
        LocalDateTime updatedAt = parseDate(item.path("updated_at").asText(null));
        long updatedDays = updatedAt == null ? 999
                : java.time.temporal.ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now());
        int popularityEvidence = Math.min(18, (int) Math.round(Math.log10(Math.max(1, stars)) * 5));
        int maintenanceEvidence = updatedDays <= 90 ? 10 : updatedDays <= 365 ? 6 : 1;
        String searchable = (item.path("name").asText("") + " "
                + item.path("description").asText("")).toLowerCase(Locale.ROOT);
        int visibleOutcome = containsAny(searchable, "app", "template", "starter", "skill", "generator",
                "design", "visual", "chat", "assistant", "dashboard", "tool") ? 28 : 10;
        int engineeringValue = containsAny(searchable, "agent", "rag", "framework", "mcp", "a2a", "testing",
                "observability", "workflow", "automation", "fullstack", "sdk") ? 24 : 8;
        // GitHub's best-match order owns semantic recall. Metadata only breaks near ties;
        // the full README-based career scorer decides whether a repository is publishable.
        int relevance = Math.max(25, 1_000 - relevanceRank * 50);
        return relevance + visibleOutcome + engineeringValue + popularityEvidence + maintenanceEvidence;
    }

    private record CareerSearchProfile(String category, String query) {}

    private DiscoveryCandidate discoveryCandidate(JsonNode item) {
        int stars = item.path("stargazers_count").asInt(0);
        LocalDateTime createdAt = parseDate(item.path("created_at").asText(null));
        LocalDateTime updatedAt = parseDate(item.path("updated_at").asText(null));
        long createdDays = createdAt == null ? 999 : java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        long updatedDays = updatedAt == null ? 999 : java.time.temporal.ChronoUnit.DAYS.between(updatedAt, LocalDateTime.now());
        int popularity = Math.min(35, (int) Math.round(Math.log10(Math.max(1, stars)) * 10));
        int freshness = createdDays <= 7 ? 30 : createdDays <= 21 ? 22 : createdDays <= 90 ? 10 : 0;
        int activity = updatedDays <= 7 ? 20 : updatedDays <= 30 ? 12 : 4;
        String searchable = (item.path("name").asText("") + " "
                + item.path("description").asText("")).toLowerCase(Locale.ROOT);
        int vibeSignal = containsAny(searchable, "starter", "template", "skill", "visual", "design",
                "generator", "playground", "creative", "agent", "website", "app") ? 15 : 5;
        return new DiscoveryCandidate(item.path("full_name").asText(),
                popularity + freshness + activity + vibeSignal);
    }

    private record DiscoveryCandidate(String fullName, int discoveryScore) {}

    @Override
    public RepositorySnapshot inspectRepository(String fullName) {
        validateFullName(fullName);
        JsonNode repo = getJson(API + "/repos/" + fullName);
        JsonNode root = getOptionalJson(API + "/repos/" + fullName + "/contents");
        JsonNode packageResponse = getOptionalJson(API + "/repos/" + fullName + "/contents/package.json");
        JsonNode readmeResponse = getOptionalJson(API + "/repos/" + fullName + "/readme");
        JsonNode packageData = decodeContent(packageResponse);
        String readme = decodeText(readmeResponse);

        List<String> rootNames = new ArrayList<>();
        if (root != null && root.isArray()) root.forEach(item -> rootNames.add(item.path("name").asText("")));
        String packageManager = detectPackageManager(rootNames, packageData);
        String nodeVersion = packageData == null ? null : packageData.path("engines").path("node").asText(null);
        String techStack = detectTechStack(repo.path("language").asText(null), packageData);
        String inspectionText = (String.join(" ", rootNames) + " " + readme + " "
                + (packageData == null ? "" : packageData.toString())).toLowerCase(Locale.ROOT);

        List<String> topics = new ArrayList<>();
        repo.path("topics").forEach(topic -> topics.add(topic.asText()));
        return new RepositorySnapshot(
                repo.path("full_name").asText(fullName),
                repo.path("owner").path("login").asText(fullName.split("/")[0]),
                repo.path("name").asText(fullName.split("/")[1]),
                repo.path("html_url").asText("https://github.com/" + fullName),
                abbreviate(repo.path("description").asText(""), 1900),
                abbreviate(readme, 12000),
                nullable(repo.path("language").asText(null)),
                topics,
                techStack,
                packageManager,
                nodeVersion,
                nullable(repo.path("license").path("spdx_id").asText(null)),
                repo.path("stargazers_count").asInt(0),
                repo.path("forks_count").asInt(0),
                repo.path("open_issues_count").asInt(0),
                repo.path("archived").asBoolean(false),
                readmeResponse != null && readmeResponse.hasNonNull("content"),
                containsAny(inspectionText, "quick start", "quickstart", "getting started", "npm install", "npx ", "pip install", "uv sync"),
                detectSetupSteps(readme),
                containsAny(inspectionText, "dockerfile", "docker-compose", "kubernetes", "helm"),
                containsAny(inspectionText, "mysql", "postgres", "mongodb", "redis", "database_url"),
                containsAny(inspectionText, ".env", "api_key", "api key", "access token", "secret key"),
                containsAny(inspectionText, "aws", "azure", "google cloud", "cloudflare", "supabase", "firebase"),
                containsAny(inspectionText, "cuda", "requires gpu", "gpu required", "nvidia"),
                containsAny(inspectionText, "node-gyp", "cmake", "cargo build", "build-essential", "visual studio build tools"),
                codebaseSize(repo.path("size").asInt(0)),
                containsAny(inspectionText, "test", "spec", "pytest", "junit", "vitest", "jest", "playwright"),
                rootNames.stream().anyMatch(name -> name.equalsIgnoreCase(".github"))
                        || containsAny(inspectionText, "github actions", "gitlab-ci", "circleci"),
                containsAny(inspectionText, "deploy", "deployment", "vercel", "netlify", "railway", "render.com"),
                containsAny(inspectionText, "architecture", "design document", "system design", "adr"),
                containsAny(inspectionText, "release", "semantic-release", "changesets", "changelog"),
                parseDate(repo.path("created_at").asText(null)),
                parseDate(repo.path("updated_at").asText(null)));
    }

    private int detectSetupSteps(String readme) {
        if (readme == null || readme.isBlank()) return 0;
        String normalized = readme.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String command : List.of("npm install", "npm run", "npx ", "pnpm ", "yarn ", "pip install", "uv sync", "python ")) {
            if (normalized.contains(command)) count++;
        }
        return Math.min(count, 9);
    }

    private String codebaseSize(int githubSizeKb) {
        if (githubSizeKb <= 5_000) return "SMALL";
        if (githubSizeKb <= 30_000) return "MEDIUM";
        return "LARGE";
    }

    public int refreshRepository(Long repositoryId) {
        Map<String, Object> repository = mapper.selectRepositoryById(repositoryId);
        if (repository == null) throw new BaseException("仓库源不存在");
        String fullName = text(repository.get("fullName"));
        validateFullName(fullName);

        try {
            refreshRepositoryMetadata(repository, fullName);
            JsonNode issues = getJson(API + "/repos/" + fullName
                    + "/issues?state=open&sort=updated&direction=desc&per_page=12");
            int saved = 0;
            if (issues.isArray()) {
                for (JsonNode issue : issues) {
                    if (issue.hasNonNull("pull_request")) continue;
                    mapper.upsertIssue(toIssue(repositoryId, issue));
                    saved++;
                }
            }
            mapper.updateRepositorySync(repositoryId, "SUCCESS", null);
            return saved;
        } catch (Exception e) {
            mapper.updateRepositorySync(repositoryId, "FAILED", abbreviate(e.getMessage(), 900));
            throw e instanceof BaseException base ? base : new BaseException("GitHub 仓库刷新失败：" + e.getMessage());
        }
    }

    public Map<String, Object> verifyIssue(Long issueId) {
        Map<String, Object> cached = mapper.selectIssueById(issueId);
        if (cached == null) throw new BaseException("Issue 不存在");
        LocalDateTime lastVerified = localDateTime(cached.get("verifiedAt"));
        if (lastVerified != null && lastVerified.isAfter(LocalDateTime.now().minusMinutes(2))) {
            return cached;
        }
        String fullName = text(cached.get("repositoryFullName"));
        int number = intValue(cached.get("issueNumber"));

        JsonNode issue = getJson(API + "/repos/" + fullName + "/issues/" + number);
        JsonNode comments = getJson(API + "/repos/" + fullName + "/issues/" + number
                + "/comments?sort=updated&direction=desc&per_page=30");
        JsonNode timeline = getJson(API + "/repos/" + fullName + "/issues/" + number
                + "/timeline?per_page=100");

        int linkedPrCount = countLinkedPullRequests(timeline);
        String claimStatus = detectClaim(issue.path("assignees"), comments);
        List<String> riskFlags = riskFlags(issue.path("title").asText(), issue.path("body").asText(""));
        String participation = participationStatus(issue.path("assignees"), linkedPrCount, claimStatus, riskFlags);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", issueId);
        update.put("state", issue.path("state").asText("open"));
        update.put("commentsCount", issue.path("comments").asInt(0));
        update.put("assigneesJson", json(issue.path("assignees")));
        update.put("linkedPrCount", linkedPrCount);
        update.put("claimStatus", claimStatus);
        update.put("participationStatus", participation);
        update.put("difficulty", difficulty(issue));
        update.put("riskFlagsJson", json(riskFlags));
        update.put("githubUpdatedAt", parseDate(issue.path("updated_at").asText(null)));
        mapper.updateIssueVerification(update);
        return mapper.selectIssueById(issueId);
    }

    private void refreshRepositoryMetadata(Map<String, Object> repository, String fullName) {
        JsonNode repo = getJson(API + "/repos/" + fullName);
        JsonNode root = getOptionalJson(API + "/repos/" + fullName + "/contents");
        JsonNode packageJson = getOptionalJson(API + "/repos/" + fullName + "/contents/package.json");

        List<String> rootNames = new ArrayList<>();
        if (root != null && root.isArray()) {
            root.forEach(item -> rootNames.add(item.path("name").asText("")));
        }
        JsonNode packageData = decodeContent(packageJson);
        String packageManager = detectPackageManager(rootNames, packageData);
        String nodeVersion = packageData == null ? null : packageData.path("engines").path("node").asText(null);
        String techStack = detectTechStack(repo.path("language").asText(null), packageData);
        String contributionUrl = rootNames.stream().anyMatch(n -> n.equalsIgnoreCase("CONTRIBUTING.md"))
                ? "https://github.com/" + fullName + "/blob/HEAD/CONTRIBUTING.md" : null;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", repository.get("id"));
        metadata.put("displayName", nullable(repo.path("name").asText(null)));
        metadata.put("organizationName", nullable(repo.path("owner").path("login").asText(null)));
        metadata.put("htmlUrl", repo.path("html_url").asText("https://github.com/" + fullName));
        metadata.put("stars", repo.path("stargazers_count").asInt(0));
        metadata.put("primaryLanguage", nullable(repo.path("language").asText(null)));
        metadata.put("techStack", techStack);
        metadata.put("nodeVersion", nodeVersion);
        metadata.put("packageManager", packageManager);
        metadata.put("contributionUrl", contributionUrl);
        metadata.put("environmentNotes", environmentNotes(rootNames, packageManager));
        mapper.updateRepositoryMetadata(metadata);
    }

    private Map<String, Object> toIssue(Long repositoryId, JsonNode issue) {
        List<String> risks = riskFlags(issue.path("title").asText(), issue.path("body").asText(""));
        String claimStatus = issue.path("assignees").isEmpty() ? "NONE" : "CLAIMED";
        String participation = !risks.isEmpty() ? "NOT_RECOMMENDED" : "POSSIBLY_CLAIMED";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("repositoryId", repositoryId);
        value.put("githubIssueId", issue.path("id").asLong());
        value.put("issueNumber", issue.path("number").asInt());
        value.put("titleOriginal", abbreviate(issue.path("title").asText(""), 500));
        value.put("bodyOriginal", abbreviate(issue.path("body").asText(""), 60_000));
        value.put("htmlUrl", issue.path("html_url").asText());
        value.put("labelsJson", json(issue.path("labels")));
        value.put("state", issue.path("state").asText("open"));
        value.put("commentsCount", issue.path("comments").asInt(0));
        value.put("assigneesJson", json(issue.path("assignees")));
        value.put("linkedPrCount", 0);
        value.put("claimStatus", claimStatus);
        value.put("participationStatus", participation);
        value.put("difficulty", difficulty(issue));
        value.put("riskFlagsJson", json(risks));
        value.put("githubCreatedAt", parseDate(issue.path("created_at").asText(null)));
        value.put("githubUpdatedAt", parseDate(issue.path("updated_at").asText(null)));
        // New issues stay private until both verification and translation succeed.
        value.put("published", 0);
        return value;
    }

    private JsonNode getJson(String url) {
        return githubApiClient.getJson(url);
    }

    private JsonNode getOptionalJson(String url) {
        try { return getJson(url); }
        catch (Exception e) { log.debug("可选 GitHub 文件不存在: {}", url); return null; }
    }

    private JsonNode decodeContent(JsonNode response) {
        if (response == null || !response.hasNonNull("content")) return null;
        try {
            String encoded = response.path("content").asText().replaceAll("\\s", "");
            return objectMapper.readTree(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("package.json 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String decodeText(JsonNode response) {
        if (response == null || !response.hasNonNull("content")) return "";
        try {
            String encoded = response.path("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private String detectPackageManager(List<String> names, JsonNode packageData) {
        if (packageData != null && packageData.hasNonNull("packageManager")) {
            return packageData.path("packageManager").asText().split("@")[0];
        }
        if (names.contains("pnpm-lock.yaml")) return "pnpm";
        if (names.contains("yarn.lock")) return "yarn";
        if (names.contains("package-lock.json")) return "npm";
        return null;
    }

    private String detectTechStack(String language, JsonNode packageData) {
        List<String> stack = new ArrayList<>();
        if (language != null && !language.isBlank()) stack.add(language);
        if (packageData != null) {
            String deps = packageData.path("dependencies").toString().toLowerCase(Locale.ROOT)
                    + packageData.path("devDependencies").toString().toLowerCase(Locale.ROOT);
            if (deps.contains("typescript")) stack.add("TypeScript");
            if (deps.contains("react")) stack.add("React");
            if (deps.contains("next")) stack.add("Next.js");
            if (deps.contains("vue")) stack.add("Vue");
            if (deps.contains("playwright")) stack.add("Playwright");
            if (deps.contains("storybook")) stack.add("Storybook");
        }
        return String.join(",", stack.stream().distinct().limit(8).toList());
    }

    private String environmentNotes(List<String> names, String packageManager) {
        List<String> notes = new ArrayList<>();
        if (packageManager != null) notes.add("使用 " + packageManager);
        if (names.stream().anyMatch(n -> n.equalsIgnoreCase("Dockerfile") || n.equalsIgnoreCase("docker-compose.yml"))) {
            notes.add("仓库包含 Docker 配置，具体 Issue 是否需要请确认");
        }
        if (names.stream().anyMatch(n -> n.startsWith(".env"))) notes.add("仓库包含环境变量示例");
        return String.join("；", notes);
    }

    private int countLinkedPullRequests(JsonNode timeline) {
        if (timeline == null || !timeline.isArray()) return 0;
        int count = 0;
        for (JsonNode event : timeline) {
            if (!"cross-referenced".equals(event.path("event").asText())) continue;
            if (event.path("source").path("issue").hasNonNull("pull_request")) count++;
        }
        return count;
    }

    private String detectClaim(JsonNode assignees, JsonNode comments) {
        if (assignees != null && assignees.isArray() && !assignees.isEmpty()) return "CLAIMED";
        if (comments != null && comments.isArray()) {
            for (JsonNode comment : comments) {
                if (CLAIM_PATTERN.matcher(comment.path("body").asText("")).find()) return "POSSIBLE";
            }
        }
        return "NONE";
    }

    private String participationStatus(JsonNode assignees, int linkedPrCount, String claimStatus, List<String> risks) {
        if ((assignees != null && assignees.isArray() && !assignees.isEmpty())
                || linkedPrCount > 0 || "CLAIMED".equals(claimStatus) || !risks.isEmpty()) {
            return "NOT_RECOMMENDED";
        }
        return "POSSIBLE".equals(claimStatus) ? "POSSIBLY_CLAIMED" : "TRY";
    }

    private List<String> riskFlags(String title, String body) {
        String value = (title + " " + body).toLowerCase(Locale.ROOT);
        return RISK_WORDS.stream().filter(value::contains).sorted().limit(8).toList();
    }

    private String difficulty(JsonNode issue) {
        String labels = issue.path("labels").toString().toLowerCase(Locale.ROOT);
        String title = issue.path("title").asText("").toLowerCase(Locale.ROOT);
        if (labels.contains("good first") || labels.contains("beginner")
                || title.contains("typo") || title.contains("documentation")) return "BEGINNER";
        if (issue.path("comments").asInt(0) >= 8 || issue.path("body").asText("").length() > 6_000) return "MEDIUM";
        return "EASY";
    }

    private void validateFullName(String fullName) {
        if (!REPOSITORY_NAME.matcher(fullName).matches()) throw new BaseException("无效的 GitHub 仓库名称");
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (Exception ignored) { return null; }
    }
    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private int intValue(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(text(value)); }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private String abbreviate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
