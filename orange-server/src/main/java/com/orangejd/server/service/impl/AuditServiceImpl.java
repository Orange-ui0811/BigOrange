package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import lombok.extern.slf4j.Slf4j;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.AuditRecord;
import com.orangejd.pojo.entity.Work;
import com.orangejd.pojo.entity.Requirement;
import com.orangejd.server.mapper.AuditRecordMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.service.AuditService;
import com.orangejd.server.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Autowired private WorkMapper workMapper;
    @Autowired private RequirementMapper requirementMapper;
    @Autowired private AuditRecordMapper auditRecordMapper;
    @Autowired private NotificationService notificationService;

    @Override
    @Transactional
    public void auditWork(Long workId, boolean approved, String reason) {
        Work work = workMapper.selectById(workId);
        if (work == null) throw new BaseException(MessageConstant.WORK_NOT_FOUND);
        if (!"PENDING_REVIEW".equals(work.getAuditStatus()))
            throw new BaseException(MessageConstant.WORK_AUDIT_DENIED);

        String auditStatus = approved ? "APPROVED" : "REJECTED";
        workMapper.updateAuditStatus(workId, auditStatus);
        log.info("审核作品 workId={} result={}", workId, auditStatus);

        AuditRecord record = new AuditRecord();
        record.setTargetType("WORK");
        record.setTargetId(workId);
        record.setAuditorId(BaseContext.getCurrentId());
        record.setAuditStatus(auditStatus);
        record.setReason(reason);
        auditRecordMapper.insert(record);
        notificationService.send(work.getOwnerId(), "AUDIT",
                approved ? "你的作品《" + work.getTitle() + "》审核通过" : "你的作品《" + work.getTitle() + "》审核未通过",
                workId);
    }

    @Override
    @Transactional
    public void auditRequirement(Long requirementId, boolean approved, String reason) {
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) throw new BaseException(MessageConstant.REQ_NOT_FOUND);
        if (!"PENDING_REVIEW".equals(req.getAuditStatus()))
            throw new BaseException(MessageConstant.REQ_AUDIT_DENIED);

        String auditStatus = approved ? "APPROVED" : "REJECTED";
        requirementMapper.updateAuditStatus(requirementId, auditStatus);

        AuditRecord record = new AuditRecord();
        record.setTargetType("REQUIREMENT");
        record.setTargetId(requirementId);
        record.setAuditorId(BaseContext.getCurrentId());
        record.setAuditStatus(auditStatus);
        record.setReason(reason);
        auditRecordMapper.insert(record);
        notificationService.send(req.getPublisherId(), "AUDIT",
                approved ? "你的需求《" + req.getTitle() + "》审核通过" : "你的需求《" + req.getTitle() + "》审核未通过",
                requirementId);
    }

    @Override
    public List<AuditRecord> getAuditHistory(String targetType, Long targetId) {
        return auditRecordMapper.selectByTarget(targetType, targetId);
    }
}
