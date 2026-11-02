package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.ProjectKnowledgeIndexStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectKnowledgeIndexStatusMapper {
    int markPending(@Param("projectType") String projectType, @Param("projectId") Long projectId,
                    @Param("sourceRef") String sourceRef, @Param("contentHash") String contentHash);
    int markIndexing(@Param("projectType") String projectType, @Param("projectId") Long projectId);
    int markDone(@Param("projectType") String projectType, @Param("projectId") Long projectId,
                 @Param("documentId") Long documentId, @Param("contentHash") String contentHash,
                 @Param("chunkCount") int chunkCount);
    int markFailed(@Param("projectType") String projectType, @Param("projectId") Long projectId,
                   @Param("error") String error);
    int markRemoved(@Param("projectType") String projectType, @Param("projectId") Long projectId);
    ProjectKnowledgeIndexStatus selectBySourceRef(@Param("sourceRef") String sourceRef);
    List<ProjectKnowledgeIndexStatus> selectAll();
}
