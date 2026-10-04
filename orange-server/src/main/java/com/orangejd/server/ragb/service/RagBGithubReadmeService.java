package com.orangejd.server.ragb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangejd.server.foundation.external.GithubApiClient;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.mapper.RagBMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RagBGithubReadmeService {
    private static final Logger log = LoggerFactory.getLogger(RagBGithubReadmeService.class);
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}");
    private final GithubApiClient github;
    private final RagBDocumentService documents;
    private final RagBMapper mapper;
    private final RagBProperties properties;
    private final AtomicBoolean syncing = new AtomicBoolean();

    public RagBGithubReadmeService(GithubApiClient github, RagBDocumentService documents,
                                   RagBMapper mapper, RagBProperties properties) {
        this.github = github;
        this.documents = documents;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Scheduled(cron = "${orange.rag-b.github-sync-cron}",
            zone = "${orange.leaderboard.refresh-zone:Asia/Shanghai}")
    public void scheduledSync() {
        if (!properties.isEnabled() || !properties.isGithubSyncEnabled() || !syncing.compareAndSet(false, true)) return;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        try {
            for (var document : mapper.selectGithubSyncDocuments(properties.getGithubSyncLimit())) {
                try {
                    Map<String, Object> result = sync(document.getSourceRef());
                    if ("UNCHANGED".equals(result.get("status"))) unchanged++; else updated++;
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("RAG B GitHub README sync failed sourceRef={}: {}", document.getSourceRef(), ex.getMessage());
                }
            }
            log.info("RAG B GitHub README sync completed updated={} unchanged={} failed={}", updated, unchanged, failed);
        } finally {
            syncing.set(false);
        }
    }

    public Map<String, Object> sync(String suppliedRepository) {
        String repository = normalize(suppliedRepository);
        JsonNode readme = github.getJson("https://api.github.com/repos/" + repository + "/readme");
        if (!"base64".equalsIgnoreCase(readme.path("encoding").asText()) || readme.path("content").asText().isBlank()) {
            throw new IllegalStateException("GitHub README 响应缺少可解码内容");
        }
        byte[] decoded;
        try { decoded = Base64.getMimeDecoder().decode(readme.path("content").asText()); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("GitHub README 内容编码无效", ex); }
        String content = new String(decoded, StandardCharsets.UTF_8);
        Map<String, Object> result = new LinkedHashMap<>(documents.importSource(
                "GITHUB", repository, repository + " README", content));
        result.put("repository", repository);
        result.put("githubSha", readme.path("sha").asText(""));
        result.put("repositoryUrl", "https://github.com/" + repository);
        return result;
    }

    private String normalize(String supplied) {
        if (supplied == null) throw new IllegalArgumentException("GitHub 仓库不能为空");
        String value = supplied.trim();
        value = value.replaceFirst("^https://github\\.com/", "").replaceFirst("\\.git$", "");
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!REPOSITORY.matcher(value).matches()) throw new IllegalArgumentException("请输入 owner/repository 或标准 GitHub 仓库地址");
        return value;
    }
}
