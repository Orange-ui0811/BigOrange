package com.orangejd.server.service;

import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KnowledgeDocumentQueryService {
    private final KnowledgeDocumentMapper mapper;
    public KnowledgeDocumentQueryService(KnowledgeDocumentMapper mapper) { this.mapper = mapper; }
    public List<KnowledgeDocument> selectAll() { return mapper.selectAll(); }
    public KnowledgeDocument selectById(Long id) { return mapper.selectById(id); }
}
