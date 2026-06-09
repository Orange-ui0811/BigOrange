package com.orangejd.server.service;

import com.orangejd.pojo.entity.AuditRecord;

import java.util.List;

public interface AuditService {

    void auditWork(Long workId, boolean approved, String reason);

    void auditRequirement(Long requirementId, boolean approved, String reason);

    List<AuditRecord> getAuditHistory(String targetType, Long targetId);
}
