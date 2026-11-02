package com.orangejd.server.customer;

import org.springframework.stereotype.Component;
import com.orangejd.pojo.entity.KnowledgeDocument;
import com.orangejd.server.mapper.KnowledgeDocumentMapper;
import com.orangejd.server.ragb.mapper.RagBMapper;
import com.orangejd.server.ragb.service.RagBDocumentService;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.foundation.task.*;
import org.aspectj.lang.annotation.*;
import java.util.*;

/** Durable system_task jobs follow every legacy write, including writes outside DocumentService. */
@Aspect
@Component
public class CustomerKnowledgeBridge implements SystemTaskHandler {
    private final CustomerProperties config;
    private final RagBProperties rag;
    private final SystemTaskService tasks;
    private final KnowledgeDocumentMapper legacy;
    private final RagBMapper indexes;
    private final RagBDocumentService documents;
    private final CustomerJson json;
    public CustomerKnowledgeBridge(CustomerProperties c,RagBProperties r,SystemTaskService t,KnowledgeDocumentMapper l,RagBMapper i,RagBDocumentService d,CustomerJson j) { config=c; rag=r; tasks=t; legacy=l; indexes=i; documents=d; json=j; }
    @AfterReturning("execution(* com.orangejd.server.mapper.KnowledgeDocumentMapper.insert(..)) && args(document)")
    public void inserted(KnowledgeDocument document) { changed(document); }
    @AfterReturning("execution(* com.orangejd.server.mapper.KnowledgeDocumentMapper.updatePipeline(..)) && args(document)")
    public void updated(KnowledgeDocument document) { changed(document); }
    private void changed(KnowledgeDocument value) {
        if(!config.isEnabled() || !rag.isEnabled() || value==null || value.getId()==null) return;
        KnowledgeDocument document=legacy.selectById(value.getId()); if(document==null) return;
        String content=document.getOriginalContent(); if(content==null || content.isBlank()) content=document.getContent();
        if(content==null || content.isBlank()) return;
        Map<String,Object> payload=Map.of("operation","UPSERT","legacyId",document.getId());
        String digest=CustomerStore.hash(content+"\n"+document.getTitle()+"\n"+document.getSourceRef()+"\n"+document.getSource());
        tasks.enqueue(taskType(),"customer-knowledge:"+document.getId()+":"+digest,payload,3);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${orange.customer.sync-reconcile-ms:60000}")
    public void reconcile() {
        if(config.isEnabled() && rag.isEnabled()) for(KnowledgeDocument document:legacy.selectAllWithContent()) changed(document);
    }
    @Around("execution(* com.orangejd.server.mapper.KnowledgeDocumentMapper.deleteById(..)) && args(id)")
    public Object deleted(org.aspectj.lang.ProceedingJoinPoint operation,Long id) throws Throwable {
        if(!config.isEnabled() || !rag.isEnabled()) return operation.proceed();
        KnowledgeDocument source=legacy.selectById(id); if(source==null) return operation.proceed();
        String ref=source.getSourceRef()==null || source.getSourceRef().isBlank()?"legacy-document:"+id:source.getSourceRef();
        var target=indexes.selectDocumentBySource(source.getSource(),ref); if(target==null) return operation.proceed();
        indexes.tombstone(target.getId());
        indexes.tombstoneChunks(target.getId());
        Object result=operation.proceed();
        tasks.enqueue(taskType(),"customer-knowledge-delete:"+target.getId(),Map.of("operation","DELETE","documentId",target.getId()),3);
        return result;
    }
    @Override public String taskType() { return "CUSTOMER_KNOWLEDGE_SYNC"; }
    @Override public Object execute(SystemTask task) {
        if(!config.isEnabled() || !rag.isEnabled()) throw new IllegalStateException("客服知识同步已关闭");
        Map<String,Object> payload=json.read(task.getPayloadJson());
        if("DELETE".equals(payload.get("operation"))) {
            long documentId=CustomerStore.positiveId(payload.get("documentId"));
            var current=indexes.selectDocument(documentId);
            if(current==null || !"DELETED".equals(current.getStatus())) return Map.of("status","DELETE_OBSOLETE");
            Map<String,Object> result=documents.delete(documentId);
            if(!Boolean.TRUE.equals(result.get("vectorCleanupCompleted"))) throw new IllegalStateException("向量清理待重试");
            return result;
        }
        long id=CustomerStore.positiveId(payload.get("legacyId"));
        if(legacy.selectById(id)==null) return Map.of("status","SOURCE_REMOVED");
        Object result=documents.importLegacy(id);
        // An import which raced a source deletion must not leave a resurrected B document.
        if(legacy.selectById(id)==null && result instanceof Map<?,?> imported && imported.get("documentId") instanceof Number documentId) {
            indexes.tombstone(documentId.longValue()); indexes.tombstoneChunks(documentId.longValue());
            tasks.enqueue(taskType(),"customer-knowledge-delete:"+documentId,Map.of("operation","DELETE","documentId",documentId),3);
            return Map.of("status","SOURCE_REMOVED");
        }
        return result;
    }
}
