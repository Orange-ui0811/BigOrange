package com.orangejd.server.service;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.pojo.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    /**
     * 上传文档：切片 → 向量化 → 存 MySQL + Redis Stack
     */
    KnowledgeDocumentVO upload(MultipartFile file);

    /**
     * 从 GitHub 导入 README（自动采集，幂等），翻译为中文后向量化
     * @param period "all"（永久）或 "week"（30 天过期）
     */
    KnowledgeDocumentVO importFromGithub(String fullName, String content, String period);

    /** 从岗位投稿导入 JD 到知识库 */
    void importJobPosting(Long jobId, String position, String direction, String displayContent);

    /**
     * 按 source_ref 查是否已存在（幂等校验）
     */
    boolean existsBySourceRef(String sourceRef);

    /**
     * 文档列表
     */
    List<KnowledgeDocumentVO> listAll();

    /**
     * 删除文档：删 MySQL + 删 Redis 向量
     */
    void delete(Long id);
}
