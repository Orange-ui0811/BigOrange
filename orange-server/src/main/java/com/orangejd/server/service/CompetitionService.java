package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.AiCompetition;
import com.orangejd.server.dto.CompetitionRequest;
import com.orangejd.server.mapper.AiCompetitionMapper;
import com.orangejd.server.service.impl.QuotaServiceImpl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class CompetitionService {

    public static final int COMPETITION_REWARD = 20;

    private final AiCompetitionMapper mapper;
    private final QuotaServiceImpl quotaService;

    public CompetitionService(AiCompetitionMapper mapper, QuotaServiceImpl quotaService) {
        this.mapper = mapper;
        this.quotaService = quotaService;
    }

    public PageResult<AiCompetition> publicList(boolean history, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        LocalDateTime now = LocalDateTime.now();
        return new PageResult<>(
                mapper.countPublic(history, now),
                mapper.selectPublic(history, now, (safePage - 1) * safeSize, safeSize)
        );
    }

    public PageResult<AiCompetition> adminList(String status, int page, int size) {
        String safeStatus = normalizeStatus(status);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new PageResult<>(
                mapper.countAdmin(safeStatus),
                mapper.selectAdmin(safeStatus, (safePage - 1) * safeSize, safeSize)
        );
    }

    public AiCompetition detail(Long id) {
        AiCompetition competition = mapper.selectById(id);
        if (competition == null) throw new BaseException("比赛不存在");
        return competition;
    }

    public Long submit(CompetitionRequest request, Long accountId, String ip) {
        if (accountId == null || accountId <= 0) throw new BaseException("请先登录");
        AiCompetition competition = fromRequest(request);
        competition.setAuditStatus("PENDING");
        competition.setSubmissionSource("USER");
        competition.setSubmitterId(accountId);
        competition.setSubmitterIp(trim(ip, 64));
        competition.setRewardGranted(false);
        competition.setRewardQuota(0);
        insert(competition);
        return competition.getId();
    }

    public Long createByAdmin(CompetitionRequest request, Long adminId) {
        AiCompetition competition = fromRequest(request);
        competition.setAuditStatus("APPROVED");
        competition.setSubmissionSource("ADMIN");
        competition.setReviewerId(adminId);
        competition.setApprovedAt(LocalDateTime.now());
        competition.setRewardGranted(false);
        competition.setRewardQuota(0);
        insert(competition);
        return competition.getId();
    }

    public void update(Long id, CompetitionRequest request) {
        AiCompetition current = detail(id);
        AiCompetition updated = fromRequest(request);
        updated.setId(current.getId());
        try {
            if (mapper.updateContent(updated) != 1) throw new BaseException("保存失败");
        } catch (DataIntegrityViolationException e) {
            throw new BaseException("该比赛网址已存在");
        }
    }

    @Transactional
    public void approve(Long id, Long adminId) {
        AiCompetition competition = mapper.selectByIdForUpdate(id);
        if (competition == null) throw new BaseException("比赛不存在");
        if (!"PENDING".equals(competition.getAuditStatus())) throw new BaseException("比赛状态已变化，请刷新后重试");

        boolean grantReward = "USER".equals(competition.getSubmissionSource())
                && competition.getSubmitterId() != null
                && !Boolean.TRUE.equals(competition.getRewardGranted());
        int reward = grantReward ? COMPETITION_REWARD : 0;
        if (grantReward) {
            quotaService.grantReward(competition.getSubmitterId(), reward, "AI大赛投稿审核通过");
        }
        if (mapper.approve(id, adminId, grantReward, reward) != 1) {
            throw new BaseException("比赛状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public void reject(Long id, Long adminId, String reason) {
        AiCompetition competition = mapper.selectByIdForUpdate(id);
        if (competition == null) throw new BaseException("比赛不存在");
        if (!"PENDING".equals(competition.getAuditStatus())) throw new BaseException("比赛状态已变化，请刷新后重试");
        if (mapper.reject(id, adminId, trim(reason, 500)) != 1) {
            throw new BaseException("比赛状态已变化，请刷新后重试");
        }
    }

    public void delete(Long id) {
        if (mapper.deleteById(id) != 1) throw new BaseException("比赛不存在");
    }

    private void insert(AiCompetition competition) {
        try {
            mapper.insert(competition);
        } catch (DataIntegrityViolationException e) {
            throw new BaseException("该比赛网址已存在，请勿重复投稿");
        }
    }

    private AiCompetition fromRequest(CompetitionRequest request) {
        if (request.endTime() != null && request.startTime() != null
                && !request.endTime().isAfter(request.startTime())) {
            throw new BaseException("结束时间必须晚于开始时间");
        }
        AiCompetition competition = new AiCompetition();
        competition.setName(required(request.name(), "请输入比赛名称", 200));
        competition.setSourceUrl(normalizeUrl(request.sourceUrl()));
        competition.setNormalizedUrl(competition.getSourceUrl());
        competition.setSummary(required(request.summary(), "请输入比赛简介", 1200));
        competition.setOrganizer(required(request.organizer(), "请输入举办方", 200));
        competition.setStartTime(request.startTime());
        competition.setEndTime(request.endTime());
        return competition;
    }

    public static String normalizeUrl(String value) {
        String raw = required(value, "请输入比赛官网", 768);
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new BaseException("比赛官网必须是有效的 HTTPS 地址");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort() == 443 ? -1 : uri.getPort();
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return new URI("https", null, host, port, path, uri.getQuery(), null).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            if (e instanceof BaseException baseException) throw baseException;
            throw new BaseException("比赛官网必须是有效的 HTTPS 地址");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PENDING") && !normalized.equals("APPROVED") && !normalized.equals("REJECTED")) {
            throw new BaseException("审核状态无效");
        }
        return normalized;
    }

    private static String required(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new BaseException(message);
        if (normalized.length() > maxLength) throw new BaseException(message.replace("请输入", "") + "过长");
        return normalized;
    }

    private static String trim(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
