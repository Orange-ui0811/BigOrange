package com.orangejd.server.service;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.pojo.entity.ProjectKnowledgeIndexStatus;
import com.orangejd.server.mapper.ProjectKnowledgeIndexStatusMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KnowledgeDocumentQueryService {
    private final KnowledgeDocumentMapper mapper;
    private final ProjectKnowledgeIndexStatusMapper projectIndexStatusMapper;
    public KnowledgeDocumentQueryService(KnowledgeDocumentMapper mapper,
                                         ProjectKnowledgeIndexStatusMapper projectIndexStatusMapper) {
        this.mapper = mapper;
        this.projectIndexStatusMapper = projectIndexStatusMapper;
    }
    public List<KnowledgeDocument> selectAll() { return mapper.selectAll(); }
    public KnowledgeDocument selectById(Long id) { return mapper.selectById(id); }
    public List<ProjectKnowledgeIndexStatus> selectProjectIndexStatuses() {
        return projectIndexStatusMapper.selectAll();
    }
}
