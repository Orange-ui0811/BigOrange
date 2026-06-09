package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
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
import com.orangejd.server.service.WorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkServiceImpl implements WorkService {

    @Autowired private WorkMapper workMapper;
    @Autowired private WorkSaleOptionMapper optionMapper;

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
        work.setDemoUrl(dto.getDemoUrl());
        work.setAuditStatus("DRAFT");
        work.setVisibilityStatus("PUBLIC");
        work.setPublishStatus("UNPUBLISHED");
        workMapper.insert(work);

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

        work.setTitle(dto.getTitle() != null ? dto.getTitle() : work.getTitle());
        work.setSummary(dto.getSummary() != null ? dto.getSummary() : work.getSummary());
        work.setDetail(dto.getDetail() != null ? dto.getDetail() : work.getDetail());
        work.setCategoryId(dto.getCategoryId() != null ? dto.getCategoryId() : work.getCategoryId());
        work.setTags(dto.getTags() != null ? dto.getTags() : work.getTags());
        work.setCoverUrl(dto.getCoverUrl() != null ? dto.getCoverUrl() : work.getCoverUrl());
        work.setDemoUrl(dto.getDemoUrl() != null ? dto.getDemoUrl() : work.getDemoUrl());
        workMapper.update(work);

        // 已审核通过的作品被编辑后需重新提交审核
        if ("APPROVED".equals(work.getAuditStatus())) {
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
    }

    @Autowired private com.orangejd.server.mapper.AuditRecordMapper auditRecordMapper;

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
        return toDetailVO(work);
    }

    @Override
    public PageResult<WorkVO> listMyWorks(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int total = workMapper.countByOwnerId(currentId);
        int offset = (page - 1) * size;
        List<WorkVO> records = workMapper.selectByOwnerId(currentId, offset, size)
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<WorkVO> listPublic(WorkQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = (query.getPage() - 1) * query.getSize();
        String orderBy = query.getKeyword() != null && query.getKeyword().startsWith("hot:") ? "favorites" : null;
        int total = workMapper.countPublic(query.getCategoryId(), query.getKeyword());
        List<WorkVO> records = workMapper.selectPublic(query.getCategoryId(), query.getKeyword(), orderBy, offset, query.getSize())
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<WorkVO> listAll(WorkQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = (query.getPage() - 1) * query.getSize();
        int total = workMapper.countAll(query.getAuditStatus());
        List<WorkVO> records = workMapper.selectAll(query.getAuditStatus(), offset, query.getSize())
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    private WorkVO toVO(Work w) {
        WorkVO vo = new WorkVO();
        vo.setId(w.getId()); vo.setOwnerId(w.getOwnerId()); vo.setTitle(w.getTitle());
        vo.setSummary(w.getSummary()); vo.setDetail(w.getDetail()); vo.setCategoryId(w.getCategoryId());
        vo.setTags(w.getTags()); vo.setCoverUrl(w.getCoverUrl()); vo.setDemoUrl(w.getDemoUrl());
        vo.setAuditStatus(w.getAuditStatus()); vo.setVisibilityStatus(w.getVisibilityStatus());
        vo.setPublishStatus(w.getPublishStatus());
        vo.setCreateTime(w.getCreateTime()); vo.setUpdateTime(w.getUpdateTime());
        vo.setOwnerName(w.getOwnerName()); vo.setOwnerAvatar(w.getOwnerAvatar());
        return vo;
    }

    private WorkDetailVO toDetailVO(Work w) {
        WorkDetailVO vo = new WorkDetailVO();
        vo.setId(w.getId()); vo.setOwnerId(w.getOwnerId()); vo.setTitle(w.getTitle());
        vo.setSummary(w.getSummary()); vo.setDetail(w.getDetail()); vo.setCategoryId(w.getCategoryId());
        vo.setTags(w.getTags()); vo.setCoverUrl(w.getCoverUrl()); vo.setDemoUrl(w.getDemoUrl());
        vo.setAuditStatus(w.getAuditStatus()); vo.setVisibilityStatus(w.getVisibilityStatus());
        vo.setPublishStatus(w.getPublishStatus());
        vo.setCreateTime(w.getCreateTime()); vo.setUpdateTime(w.getUpdateTime());

        List<WorkSaleOption> options = optionMapper.selectByWorkId(w.getId());
        vo.setSaleOptions(options.stream().map(o -> {
            WorkDetailVO.SaleOptionVO s = new WorkDetailVO.SaleOptionVO();
            s.setId(o.getId()); s.setSaleType(o.getSaleType()); s.setPrice(o.getPrice()); s.setDescription(o.getDescription());
            return s;
        }).collect(Collectors.toList()));
        return vo;
    }
}
