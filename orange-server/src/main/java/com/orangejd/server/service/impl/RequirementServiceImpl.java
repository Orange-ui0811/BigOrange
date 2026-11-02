package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.RequirementCreateDTO;
import com.orangejd.pojo.dto.RequirementEditDTO;
import com.orangejd.pojo.dto.RequirementQueryDTO;
import com.orangejd.pojo.entity.Requirement;
import com.orangejd.pojo.vo.RequirementVO;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.service.NotificationService;
import com.orangejd.server.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class RequirementServiceImpl implements RequirementService {

    private final RequirementMapper requirementMapper;
    private final @org.springframework.lang.Nullable com.orangejd.server.service.AiAuditService aiAuditService;

    @Override
    public RequirementVO create(RequirementCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty())
            throw new BaseException(MessageConstant.REQ_TITLE_EMPTY);

        Long currentId = BaseContext.getCurrentId();
        Requirement r = new Requirement();
        r.setPublisherId(currentId);
        r.setTitle(dto.getTitle().trim());
        r.setDetail(dto.getDetail());
        r.setCategoryId(dto.getCategoryId());
        r.setBudgetMin(dto.getBudgetMin());
        r.setBudgetMax(dto.getBudgetMax());
        r.setAuditStatus("DRAFT");
        r.setRequirementStatus("OPEN");
        r.setVisibilityStatus("PUBLIC");
        requirementMapper.insert(r);

        return toVO(requirementMapper.selectById(r.getId()));
    }

    @Override
    @Transactional
    public RequirementVO edit(Long id, RequirementEditDTO dto) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        if (!r.getPublisherId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.REQ_EDIT_DENIED);

        r.setTitle(dto.getTitle() != null ? dto.getTitle() : r.getTitle());
        r.setDetail(dto.getDetail() != null ? dto.getDetail() : r.getDetail());
        r.setCategoryId(dto.getCategoryId() != null ? dto.getCategoryId() : r.getCategoryId());
        r.setBudgetMin(dto.getBudgetMin() != null ? dto.getBudgetMin() : r.getBudgetMin());
        r.setBudgetMax(dto.getBudgetMax() != null ? dto.getBudgetMax() : r.getBudgetMax());
        requirementMapper.update(r);

        if ("APPROVED".equals(r.getAuditStatus()) || "PENDING_REVIEW".equals(r.getAuditStatus())) {
            requirementMapper.updateAuditStatus(id, "DRAFT");
        }

        return toVO(requirementMapper.selectById(id));
    }

    @Override
    public void submitForReview(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        if (!r.getPublisherId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        if (!"DRAFT".equals(r.getAuditStatus()) && !"REJECTED".equals(r.getAuditStatus()))
            throw new BaseException(MessageConstant.REQ_SUBMIT_DENIED);
        requirementMapper.updateAuditStatus(id, "PENDING_REVIEW");
        if (aiAuditService != null) aiAuditService.auditRequirementAsync(id);
    }

    @Override
    public void delete(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        if (!r.getPublisherId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.REQ_DELETE_DENIED);
        requirementMapper.deleteById(id);
    }

    private final com.orangejd.server.mapper.AuditRecordMapper auditRecordMapper;
    private final NotificationService notificationService;

    @Override
    public void offline(Long id, String reason) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        requirementMapper.updateAuditStatus(id, "REJECTED");
        requirementMapper.updateVisibility(id, "PRIVATE");
        com.orangejd.pojo.entity.AuditRecord record = new com.orangejd.pojo.entity.AuditRecord();
        record.setTargetType("REQUIREMENT");
        record.setTargetId(id);
        record.setAuditorId(com.orangejd.common.context.BaseContext.getCurrentId());
        record.setAuditStatus("REJECTED");
        record.setReason(reason != null ? reason : "管理员下架");
        auditRecordMapper.insert(record);
        notificationService.send(r.getPublisherId(), "AUDIT", "你的需求《" + r.getTitle() + "》已被管理员下架", id);
    }

    @Override
    public void toggleVisibility(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        if (!r.getPublisherId().equals(BaseContext.getCurrentId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        String newStatus = "PUBLIC".equals(r.getVisibilityStatus()) ? "PRIVATE" : "PUBLIC";
        requirementMapper.updateVisibility(id, newStatus);
    }

    @Override
    public RequirementVO getDetail(Long id) {
        Requirement r = requirementMapper.selectById(id);
        if (r == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        Long currentId = BaseContext.getCurrentId();
        if (!r.getPublisherId().equals(currentId)
                && (!("APPROVED".equals(r.getAuditStatus())) || !("PUBLIC".equals(r.getVisibilityStatus()))))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        return toVO(r);
    }

    @Override
    public RequirementVO getAdminDetail(Long id) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        return toVO(requirement);
    }

    @Override
    public PageResult<RequirementVO> listMyRequirements(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int total = requirementMapper.countByPublisherId(currentId);
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        List<RequirementVO> records = requirementMapper.selectByPublisherId(currentId, offset, size)
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<RequirementVO> listPublic(RequirementQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = com.orangejd.common.util.PageUtil.offset(query.getPage(), query.getSize());
        int total = requirementMapper.countPublic(query.getCategoryId(), query.getBudgetMin(), query.getBudgetMax(), query.getKeyword());
        List<RequirementVO> records = requirementMapper.selectPublic(query.getCategoryId(), query.getBudgetMin(), query.getBudgetMax(), query.getKeyword(), offset, query.getSize())
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<RequirementVO> listAll(RequirementQueryDTO query) {
        query.setSize(com.orangejd.common.util.PageUtil.capSize(query.getSize()));
        int offset = com.orangejd.common.util.PageUtil.offset(query.getPage(), query.getSize());
        int total = requirementMapper.countAll(query.getAuditStatus());
        List<RequirementVO> records = requirementMapper.selectAll(query.getAuditStatus(), offset, query.getSize())
                .stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(total, records);
    }

    private RequirementVO toVO(Requirement r) {
        RequirementVO vo = new RequirementVO();
        vo.setId(r.getId()); vo.setPublisherId(r.getPublisherId()); vo.setTitle(r.getTitle());
        vo.setDetail(r.getDetail()); vo.setCategoryId(r.getCategoryId());
        vo.setBudgetMin(r.getBudgetMin()); vo.setBudgetMax(r.getBudgetMax());
        vo.setAuditStatus(r.getAuditStatus()); vo.setRequirementStatus(r.getRequirementStatus());
        vo.setVisibilityStatus(r.getVisibilityStatus());
        vo.setCreateTime(r.getCreateTime()); vo.setUpdateTime(r.getUpdateTime());
        vo.setPublisherName(r.getPublisherName());
        return vo;
    }
}
