package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.AuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditRecordMapper {

    int insert(AuditRecord record);

    List<AuditRecord> selectByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    List<AuditRecord> selectByAuditor(@Param("auditorId") Long auditorId, @Param("offset") int offset, @Param("limit") int limit);
}
