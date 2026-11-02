package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper {

    int insert(KnowledgeDocument record);

    List<KnowledgeDocument> selectAll();

    List<KnowledgeDocument> selectAllWithContent();

    KnowledgeDocument selectById(@Param("id") Long id);

    KnowledgeDocument selectBySourceRef(@Param("sourceRef") String sourceRef);

    int deleteById(@Param("id") Long id);

    /** 查 30 天前的 weekly GitHub 文档（用于清理） */
    List<KnowledgeDocument> selectOldGithubWeekly(@Param("beforeDate") String beforeDate);

    int updatePipeline(KnowledgeDocument record);

    List<KnowledgeDocument> selectPipelinePending(@Param("limit") int limit);
}
