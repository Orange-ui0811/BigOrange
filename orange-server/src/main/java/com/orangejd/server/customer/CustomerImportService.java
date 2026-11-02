package com.orangejd.server.customer;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Only a validated, owned run can produce a draft for the existing plan editor. */
@Service
public class CustomerImportService {
    private final CustomerStore store;
    private final CustomerProjects projects;
    private final CustomerJson json;
    private final com.orangejd.server.ragb.mapper.RagBMapper indexes;
    public CustomerImportService(CustomerStore store,CustomerProjects projects,CustomerJson json,com.orangejd.server.ragb.mapper.RagBMapper indexes) {
        this.store=store; this.projects=projects; this.json=json; this.indexes=indexes;
    }
    public Map<String,Object> draft(String runId,long owner,String type,long projectId) {
        if(type==null || projectId<=0) deny("项目标识无效");
        Map<String,Object> run=store.run(runId,owner);
        if(!"SUCCEEDED".equals(run.get("status"))) deny("本轮尚未完成校验");
        Map<String,Object> result=json.read(run.get("result_json"));
        Map<?,?> selected=null;
        if(result.get("projects") instanceof List<?> cards) for(Object raw:cards) {
            if(raw instanceof Map<?,?> card && type.equals(card.get("type"))
                && CustomerStore.positiveId(card.get("id"))==projectId) selected=card;
        }
        if(selected==null || !Boolean.TRUE.equals(selected.get("importable"))
            || !(selected.get("references") instanceof List<?> refs) || refs.isEmpty()) deny("项目缺少本轮已验证证据");
        for(Object raw:(List<?>)selected.get("references")) {
            if(!(raw instanceof Map<?,?> ref) || ref.get("documentId")==null || ref.get("versionId")==null) deny("证据版本缺失");
            Map<?,?> ref=(Map<?,?>)raw;
            var document=indexes.selectDocument(CustomerStore.positiveId(ref.get("documentId")));
            if(document==null || document.getDeletedAt()!=null || !"READY".equals(document.getStatus())
                || !Objects.equals(document.getActiveVersionId(),CustomerStore.positiveId(ref.get("versionId")))) deny("证据已更新或删除，请重新推荐");
        }
        Map<String,Object> state=json.read(store.session(CustomerStore.number(run,"session_id"),owner).get("state_json"));
        if(CustomerWorkflow.enabled(state)) {
            var task=CustomerWorkflow.task(state);
            if(!Objects.equals(selected.get("taskId"),task.get("id")) || !(selected.get("requirementVersion") instanceof Number revision) || revision.longValue()!=CustomerWorkflow.number(task,"requirementVersion"))deny("需求或任务已变化，请重新核对项目");
            if(Boolean.TRUE.equals(task.get("matchesStale"))||Boolean.TRUE.equals(CustomerWorkflow.workflow(state).get("migrationPending"))||"CANCELLED".equals(task.get("status")))deny("当前任务尚未完成有效核对");
            if(CustomerWorkflow.rows(selected.get("requirementMatches")).stream().anyMatch(m->"HARD".equals(m.get("strength"))&&!"SATISFIED".equals(m.get("verdict"))))deny("必要要求尚未全部核实");
            if(!CustomerRecommendationPolicy.requiredSatisfied(cast(selected),state))deny("具体用途或必要条件尚未取得本轮完整依据，请重新核对项目");
            for(var match:CustomerWorkflow.rows(selected.get("requirementMatches")))for(var ref:CustomerWorkflow.rows(match.get("evidenceRefs"))) {
                if("STRUCTURED".equals(ref.get("kind")))continue;
                if(ref.get("documentId")==null||ref.get("versionId")==null)deny("用途证据版本缺失");
                var document=indexes.selectDocument(CustomerStore.positiveId(ref.get("documentId")));
                if(document==null||document.getDeletedAt()!=null||!"READY".equals(document.getStatus())||!Objects.equals(document.getActiveVersionId(),CustomerStore.positiveId(ref.get("versionId"))))deny("用途核验资料已变化，请重新推荐");
            }
        }
        if(state.get("pendingConditions") instanceof List<?> pending && !pending.isEmpty()) deny("还有待确认条件，请先继续对话确认");
        Map<String,Object> fresh;
        try { fresh=projects.detail(type,projectId,state); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"项目已失效或不满足当前条件"); }
        // A changed repository identity invalidates evidence attached to the previous recommendation.
        if(!Objects.equals(selected.get("name"),fresh.get("name")) || !Objects.equals(selected.get("url"),fresh.get("url"))) deny("项目来源已变化，请重新推荐");
        String draft="参考仓库："+fresh.get("name")+"\n仓库链接："+fresh.get("url")
            +"\n项目说明："+fresh.getOrDefault("description","未知")
            +"\n主语言："+fresh.getOrDefault("language","未知")
            +"\n技术栈："+fresh.getOrDefault("techStack","未知")
            +"\n当前已确认条件："+json.write(state.entrySet().stream()
                .filter(e->!Set.of("lastCandidates","provenance",CustomerPurpose.STATE_KEY,"purposeFailure",CustomerPurposeMemory.KEY,"queryPlan",CustomerTaskProgress.KEY).contains(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)))
            +"\n请基于上述真实仓库设计个人项目方案，缺失信息明确标记为待确认。";
        return Map.of("runId",runId,"project",fresh,"draft",draft);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?> value){return (Map<String,Object>)value;}
    private void deny(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
