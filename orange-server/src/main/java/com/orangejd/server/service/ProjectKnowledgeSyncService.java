package com.orangejd.server.service;

import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.pojo.entity.ProjectKnowledgeIndexStatus;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import com.orangejd.server.mapper.CareerProjectMapper;
import com.orangejd.server.mapper.ProjectKnowledgeIndexStatusMapper;
import com.orangejd.server.service.impl.ProjectKnowledgeDocumentWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Service
public class ProjectKnowledgeSyncService {
    public static final String BEGINNER = "BEGINNER_PROJECT";
    public static final String CAREER = "CAREER_PROJECT";
    private static final Logger log = LoggerFactory.getLogger(ProjectKnowledgeSyncService.class);

    private final BeginnerProjectMapper beginnerMapper;
    private final CareerProjectMapper careerMapper;
    private final ProjectKnowledgeIndexStatusMapper statusMapper;
    private final ProjectKnowledgeDocumentWriter documentWriter;

    public ProjectKnowledgeSyncService(BeginnerProjectMapper beginnerMapper,
                                       CareerProjectMapper careerMapper,
                                       ProjectKnowledgeIndexStatusMapper statusMapper,
                                       ProjectKnowledgeDocumentWriter documentWriter) {
        this.beginnerMapper = beginnerMapper;
        this.careerMapper = careerMapper;
        this.statusMapper = statusMapper;
        this.documentWriter = documentWriter;
    }

    public void syncBeginner(Long projectId) {
        BeginnerProject project = beginnerMapper.selectById(projectId);
        if (project == null || !"PUBLISHED".equals(project.getStatus())) {
            remove(BEGINNER, projectId, beginnerRef(projectId), project == null ? null : project.getContentHash());
            return;
        }
        String content = beginnerDocument(project);
        sync(BEGINNER, projectId, beginnerRef(projectId), project.getFullName(), content);
    }

    public void syncCareer(Long projectId) {
        CareerProjectProfile project = careerMapper.selectById(projectId);
        if (project == null || !"PUBLISHED".equals(project.getStatus())) {
            remove(CAREER, projectId, careerRef(projectId), project == null ? null : project.getContentHash());
            return;
        }
        BeginnerProject base = beginnerMapper.selectById(projectId);
        String content = careerDocument(project, base);
        sync(CAREER, projectId, careerRef(projectId), project.getFullName(), content);
    }

    public SyncSummary syncAllPublished() {
        int succeeded = 0;
        int failed = 0;
        Set<String> active = new HashSet<>();
        for (BeginnerProject project : beginnerMapper.selectAllPublished()) {
            active.add(beginnerRef(project.getId()));
            try { syncBeginner(project.getId()); succeeded++; }
            catch (RuntimeException error) { failed++; log.warn("[RAG] 新手项目同步失败: id={}", project.getId(), error); }
        }
        for (CareerProjectProfile project : careerMapper.selectAllPublished()) {
            active.add(careerRef(project.getProjectId()));
            try { syncCareer(project.getProjectId()); succeeded++; }
            catch (RuntimeException error) { failed++; log.warn("[RAG] 职业项目同步失败: id={}", project.getProjectId(), error); }
        }
        int removed = 0;
        for (ProjectKnowledgeIndexStatus status : statusMapper.selectAll()) {
            if (!active.contains(status.getSourceRef()) && !"REMOVED".equals(status.getIndexStatus())) {
                remove(status.getProjectType(), status.getProjectId(), status.getSourceRef(), status.getContentHash());
                removed++;
            }
        }
        return new SyncSummary(succeeded, failed, removed);
    }

    @Scheduled(fixedDelayString = "${orange.knowledge.project-sync-delay-ms:300000}",
            initialDelayString = "${orange.knowledge.project-sync-initial-delay-ms:60000}")
    public void scheduledReconcile() {
        try {
            SyncSummary summary = syncAllPublished();
            if (summary.failed() > 0 || summary.removed() > 0) {
                log.info("[RAG] 项目知识同步完成: {}", summary);
            }
        } catch (RuntimeException error) {
            log.warn("[RAG] 项目知识定时同步失败", error);
        }
    }

    private void sync(String type, Long projectId, String sourceRef, String title, String content) {
        String contentHash = sha256(content);
        ProjectKnowledgeIndexStatus current = statusMapper.selectBySourceRef(sourceRef);
        if (current != null && "DONE".equals(current.getIndexStatus())
                && contentHash.equals(current.getContentHash()) && current.getKnowledgeDocumentId() != null) return;
        statusMapper.markPending(type, projectId, sourceRef, contentHash);
        statusMapper.markIndexing(type, projectId);
        try {
            KnowledgeDocumentVO document = documentWriter.upsert(type, sourceRef, title, content);
            statusMapper.markDone(type, projectId, document.getId(), contentHash, document.getChunkCount());
        } catch (RuntimeException error) {
            statusMapper.markFailed(type, projectId, safeError(error));
            throw error;
        }
    }

    private void remove(String type, Long projectId, String sourceRef, String contentHash) {
        if (statusMapper.selectBySourceRef(sourceRef) == null) {
            statusMapper.markPending(type, projectId, sourceRef, contentHash);
        }
        documentWriter.delete(sourceRef);
        statusMapper.markRemoved(type, projectId);
    }

    private String beginnerDocument(BeginnerProject value) {
        StringBuilder text = new StringBuilder();
        heading(text, value.getFullName());
        field(text, "知识分类", "新手友好项目");
        field(text, "仓库地址", value.getHtmlUrl());
        field(text, "中文简介", value.getDescriptionZh());
        field(text, "项目是什么", value.getWhatIsIt());
        field(text, "适合新手的原因", value.getBeginnerReason());
        field(text, "可改造方向", value.getModificationIdeas());
        field(text, "最终建议", value.getFinalRecommendation());
        field(text, "主要风险", value.getMainRisks());
        field(text, "技术栈", value.getTechStack());
        field(text, "主要语言", value.getPrimaryLanguage());
        field(text, "主题", value.getTopicsJson());
        field(text, "难度", value.getDifficulty());
        field(text, "预计周期", value.getEstimatedDuration());
        field(text, "新手评分", number(value.getBeginnerScore()));
        field(text, "编辑评分", number(value.getEditorialScore()));
        field(text, "Stars", number(value.getStars()));
        field(text, "许可证", value.getLicenseSpdx());
        field(text, "环境要求", requirements(value));
        field(text, "README 摘要", value.getReadmeExcerpt());
        return text.toString();
    }

    private String careerDocument(CareerProjectProfile value, BeginnerProject base) {
        StringBuilder text = new StringBuilder();
        heading(text, value.getFullName());
        field(text, "知识分类", "职业成长项目");
        field(text, "仓库地址", value.getHtmlUrl());
        field(text, "项目摘要", value.getSummaryZh());
        field(text, "职业价值", value.getCareerValue());
        field(text, "可证明能力", value.getProofPoints());
        field(text, "面试讨论点", value.getInterviewTopics());
        field(text, "差异化改造方案", value.getModificationPlan());
        field(text, "主要风险", value.getMainRisks());
        field(text, "最匹配岗位", value.getBestRoleCode());
        field(text, "技术栈", value.getTechStack());
        field(text, "主要语言", value.getPrimaryLanguage());
        field(text, "难度", value.getDifficulty());
        field(text, "预计周期", value.getEstimatedDuration());
        field(text, "职业综合评分", number(value.getOverallScore()));
        field(text, "Stars", number(value.getStars()));
        field(text, "许可证", value.getLicenseSpdx());
        if (base != null) field(text, "README 摘要", base.getReadmeExcerpt());
        return text.toString();
    }

    private void heading(StringBuilder text, String value) { text.append("# ").append(safe(value)).append("\n\n"); }
    private void field(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) text.append("## ").append(label).append("\n").append(value.trim()).append("\n\n");
    }
    private String requirements(BeginnerProject value) {
        StringBuilder result = new StringBuilder();
        appendRequirement(result, value.getRequiresDocker(), "Docker");
        appendRequirement(result, value.getRequiresDatabase(), "数据库");
        appendRequirement(result, value.getRequiresSecrets(), "API Key/密钥");
        appendRequirement(result, value.getRequiresExternalService(), "外部服务");
        appendRequirement(result, value.getRequiresGpu(), "GPU");
        appendRequirement(result, value.getRequiresNativeBuild(), "本地编译环境");
        return result.length() == 0 ? "无明显额外要求" : result.toString();
    }
    private void appendRequirement(StringBuilder text, Boolean required, String label) {
        if (Boolean.TRUE.equals(required)) { if (text.length() > 0) text.append('、'); text.append(label); }
    }
    private String number(Integer value) { return value == null ? "" : String.valueOf(value); }
    private String safe(String value) { return value == null ? "" : value; }
    private String beginnerRef(Long id) { return "beginner:" + id; }
    private String careerRef(Long id) { return "career:" + id; }
    private String safeError(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        return value.length() > 900 ? value.substring(0, 900) : value;
    }
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public record SyncSummary(int succeeded, int failed, int removed) {}
}
