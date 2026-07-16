package com.orangejd.server.service;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Requirement;
import com.orangejd.pojo.entity.Work;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.stereotype.Service;

/** Centralizes access checks for polymorphic WORK/REQUIREMENT references. */
@Service
public class ContentAccessService {
    private final WorkMapper workMapper;
    private final RequirementMapper requirementMapper;

    public ContentAccessService(WorkMapper workMapper, RequirementMapper requirementMapper) {
        this.workMapper = workMapper;
        this.requirementMapper = requirementMapper;
    }

    public Long requireAccessibleOwner(String targetType, Long targetId, Long viewerId) {
        if (targetId == null || viewerId == null) throw new BaseException("内容参数无效");
        if ("WORK".equals(targetType)) {
            Work work = workMapper.selectById(targetId);
            if (work == null) throw new BaseException("作品不存在");
            requirePublicOrOwner(work.getOwnerId(), work.getAuditStatus(), work.getVisibilityStatus(), viewerId);
            return work.getOwnerId();
        }
        if ("REQUIREMENT".equals(targetType)) {
            Requirement requirement = requirementMapper.selectById(targetId);
            if (requirement == null) throw new BaseException("需求不存在");
            requirePublicOrOwner(requirement.getPublisherId(), requirement.getAuditStatus(),
                    requirement.getVisibilityStatus(), viewerId);
            return requirement.getPublisherId();
        }
        throw new BaseException("内容类型仅支持 WORK 或 REQUIREMENT");
    }

    private void requirePublicOrOwner(Long ownerId, String auditStatus, String visibilityStatus, Long viewerId) {
        if (!viewerId.equals(ownerId)
                && (!("APPROVED".equals(auditStatus)) || !("PUBLIC".equals(visibilityStatus)))) {
            throw new BaseException(MessageConstant.NO_PERMISSION);
        }
    }
}
