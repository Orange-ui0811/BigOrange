package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.WorkCreateDTO;
import com.orangejd.pojo.dto.WorkEditDTO;
import com.orangejd.pojo.dto.WorkQueryDTO;
import com.orangejd.pojo.entity.Work;
import com.orangejd.pojo.entity.WorkSaleOption;
import com.orangejd.pojo.vo.WorkDetailVO;
import com.orangejd.pojo.vo.WorkVO;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.mapper.WorkSaleOptionMapper;
import com.orangejd.server.foundation.security.ExternalUrlPolicy;
import com.orangejd.server.service.NotificationService;
import com.orangejd.server.service.WorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class WorkServiceImpl implements WorkService {

    private final WorkMapper workMapper;
    private final WorkSaleOptionMapper optionMapper;
    private final @org.springframework.lang.Nullable com.orangejd.server.service.AiAuditService aiAuditService;
    private final ManagedFileStorageService managedFileStorageService;
    private final ExternalUrlPolicy externalUrlPolicy;
    private final @org.springframework.lang.Nullable com.orangejd.server.service.PersonaMaterialCollector personaMaterialCollector;

    @Override
    @Transactional
    public WorkDetailVO create(WorkCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            throw new BaseException(MessageConstant.WORK_TITLE_EMPTY);

        Long currentId = BaseContext.getCurrentId();
        Work work = new Work();
        work.setOwnerId(currentId);
        work.setTitle(dto.getTitle().trim());
        work.setSummary(dto.getSummary());
        work.setDetail(dto.getDetail());
        work.setCategoryId(dto.getCategoryId());
        work.setTags(dto.getTags());
        work.setCoverUrl(dto.getCoverUrl());
        work.setDemoUrl(externalUrlPolicy.requireSafeOptional("演示链接", dto.getDemoUrl()));
        work.setVideoUrl(normalizeVideoUrl(dto.getVideoUrl()));
        work.setAuditStatus("DRAFT");
        work.setVisibilityStatus("PUBLIC");
        work.setPublishStatus("UNPUBLISHED");
        workMapper.insert(work);
        log.info("作品发布 id={} title={}", work.getId(), work.getTitle());

        // 创建售卖项
        if (dto.getSaleOptions() != null && !dto.getSaleOptions().isEmpty()) {
            List<WorkSaleOption> options = dto.getSaleOptions().stream().map(o -> {
                WorkSaleOption opt = new WorkSaleOption();
                opt.setWorkId(work.getId());
                opt.setSaleType(o.getSaleType());
                opt.setPrice(o.getPrice());
                opt.setDescription(o.getDescription());
                return opt;
            }).collect(Collectors.toList());
            optionMapper.insertBatch(options);
        }

        return getDetail(work.getId());
    }

    @Override
    @Transactional
    public WorkDetailVO edit(Long id, WorkEditDTO dto) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!work.getOwnerId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.WORK_EDIT_DENIED);

        String previousCover = work.getCoverUrl();
        String previousVideo = work.getVideoUrl();
        String previousDetail = work.getDetail();
        work.setTitle(dto.getTitle() != null ? dto.getTitle() : work.getTitle());
        work.setSummary(dto.getSummary() != null ? dto.getSummary() : work.getSummary());
        work.setDetail(dto.getDetail() != null ? dto.getDetail() : work.getDetail());
        work.setCategoryId(dto.getCategoryId() != null ? dto.getCategoryId() : work.getCategoryId());
        work.setTags(dto.getTags() != null ? dto.getTags() : work.getTags());
        work.setCoverUrl(dto.getCoverUrl() != null ? dto.getCoverUrl() : work.getCoverUrl());
        work.setDemoUrl(dto.getDemoUrl() != null
                ? externalUrlPolicy.requireSafeOptional("演示链接", dto.getDemoUrl())
                : work.getDemoUrl());
        if (dto.getVideoUrl() != null) work.setVideoUrl(normalizeVideoUrl(dto.getVideoUrl()));
        workMapper.update(work);

        if (dto.getSaleOptions() != null) {
            optionMapper.deleteByWorkId(id);
            if (!dto.getSaleOptions().isEmpty()) {
                List<WorkSaleOption> options = dto.getSaleOptions().stream().map(o -> {
                    WorkSaleOption opt = new WorkSaleOption();
                    opt.setWorkId(id);
                    opt.setSaleType(o.getSaleType());
                    opt.setPrice(o.getPrice());
                    opt.setDescription(o.getDescription());
                    return opt;
                }).collect(Collectors.toList());
                optionMapper.insertBatch(options);
            }
        }

        Set<String> removedMedia = new LinkedHashSet<>();
        if (!Objects.equals(previousCover, work.getCoverUrl()) && previousCover != null) removedMedia.add(previousCover);
        if (!Objects.equals(previousVideo, work.getVideoUrl()) && previousVideo != null) removedMedia.add(previousVideo);
        Set<String> removedDetailMedia = managedFileStorageService.findOwnedMediaUrls(work.getOwnerId(), previousDetail);
        removedDetailMedia.removeAll(managedFileStorageService.findOwnedMediaUrls(work.getOwnerId(), work.getDetail()));
        removedMedia.addAll(removedDetailMedia);
        managedFileStorageService.deleteOwnedMediaAfterCommit(work.getOwnerId(), removedMedia);

        // 已审核通过的作品被编辑后需重新提交审核
        if ("APPROVED".equals(work.getAuditStatus()) || "PENDING_REVIEW".equals(work.getAuditStatus())) {
            workMapper.updateAuditStatus(id, "DRAFT");
        }

        return getDetail(id);
    }

    @Override
    public void submitForReview(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!work.getOwnerId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!"DRAFT".equals(work.getAuditStatus()) && !"REJECTED".equals(work.getAuditStatus()))
            throw new BaseException(MessageConstant.WORK_SUBMIT_DENIED);
        workMapper.updateAuditStatus(id, "PENDING_REVIEW");
        capturePersonaMaterial(work.getOwnerId(), com.orangejd.server.service.PersonaDimension.SKILL,
                "WORK_SUBMITTED", id, "提交作品《" + work.getTitle() + "》进入审核",
                "我完成了作品《" + work.getTitle() + "》的整理并提交审核");
        if (aiAuditService != null) aiAuditService.auditWorkAsync(id);
    }

    @Override
    @Transactional
    public void withdraw(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!work.getOwnerId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!"PENDING_REVIEW".equals(work.getAuditStatus()) && !"APPROVED".equals(work.getAuditStatus()))
            throw new BaseException(MessageConstant.WORK_WITHDRAW_DENIED);
        workMapper.updateAuditStatus(id, "DRAFT");
    }

    private void capturePersonaMaterial(Long accountId, com.orangejd.server.service.PersonaDimension dimension,
                                        String eventType, Long workId, String evidence, String suggestion) {
        if (personaMaterialCollector == null) return;
        try {
            personaMaterialCollector.capture(accountId, dimension, eventType, "WORK",
                    String.valueOf(workId), evidence, suggestion);
        } catch (Exception exception) {
            log.warn("PERSONA_MATERIAL_CAPTURE_FAILED workId={} accountId={}", workId, accountId, exception);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!work.getOwnerId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.WORK_DELETE_DENIED);
        optionMapper.deleteByWorkId(id);
        workMapper.deleteById(id);
        Set<String> media = managedFileStorageService.findOwnedMediaUrls(work.getOwnerId(), work.getDetail());
        if (work.getCoverUrl() != null) media.add(work.getCoverUrl());
        if (work.getVideoUrl() != null) media.add(work.getVideoUrl());
        managedFileStorageService.deleteOwnedMediaAfterCommit(work.getOwnerId(), media);
    }

    private final com.orangejd.server.mapper.AuditRecordMapper auditRecordMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void offline(Long id, String reason) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        workMapper.updateAuditStatus(id, "REJECTED");
        workMapper.updateVisibility(id, "PRIVATE");
        // 记录下架审核
        com.orangejd.pojo.entity.AuditRecord record = new com.orangejd.pojo.entity.AuditRecord();
        record.setTargetType("WORK");
        record.setTargetId(id);
        record.setAuditorId(com.orangejd.common.context.BaseContext.getCurrentId());
        record.setAuditStatus("REJECTED");
        record.setReason(reason != null ? reason : "管理员下架");
        auditRecordMapper.insert(record);
        notificationService.send(work.getOwnerId(), "AUDIT", "你的作品《" + work.getTitle() + "》已被管理员下架", id);
    }

    @Override
    public void toggleVisibility(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!work.getOwnerId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        String newStatus = "PUBLIC".equals(work.getVisibilityStatus()) ? "PRIVATE" : "PUBLIC";
        workMapper.updateVisibility(id, newStatus);
    }

    @Override
    public WorkDetailVO getDetail(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        Long currentId = BaseContext.getCurrentId();
        if (!work.getOwnerId().equals(currentId)
                && (!("APPROVED".equals(work.getAuditStatus())) || !("PUBLIC".equals(work.getVisibilityStatus()))))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        return toDetailVO(work);
    }

    @Override
    public WorkDetailVO getAdminDetail(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        return toDetailVO(work);
    }

    @Override
    public PageResult<WorkVO> listMyWorks(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int total = workMapper.countByOwnerId(currentId);
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        List<WorkVO> records = workMapper.selectByOwnerId(currentId, offset, size)
                .stream().map(WorkViewAssembler::toListItem).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<WorkVO> listPublic(WorkQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = com.orangejd.common.util.PageUtil.offset(query.getPage(), query.getSize());
        String orderBy = query.getKeyword() != null && query.getKeyword().startsWith("hot:") ? "favorites" : null;
        int total = workMapper.countPublic(query.getCategoryId(), query.getKeyword());
        List<WorkVO> records = workMapper.selectPublic(query.getCategoryId(), query.getKeyword(), orderBy, offset, query.getSize())
                .stream().map(WorkViewAssembler::toListItem).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<WorkVO> listAll(WorkQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = com.orangejd.common.util.PageUtil.offset(query.getPage(), query.getSize());
        int total = workMapper.countAll(query.getAuditStatus());
        List<WorkVO> records = workMapper.selectAll(query.getAuditStatus(), offset, query.getSize())
                .stream().map(WorkViewAssembler::toListItem).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    private WorkDetailVO toDetailVO(Work w) {
        return WorkViewAssembler.toDetail(w, optionMapper.selectByWorkId(w.getId()));
    }

    private String normalizeVideoUrl(String value) {
        if (value == null) return null;
        String url = value.trim();
        if (url.isEmpty()) return "";
        if (!url.matches("^/(?:uploads/video|api/media/video)/(?:[1-9][0-9]*/)?[0-9a-fA-F-]{36}\\.(mp4|mov|m4v|webm)$")) {
            throw new BaseException("请通过作品发布页上传视频文件");
        }
        return url;
    }
}
