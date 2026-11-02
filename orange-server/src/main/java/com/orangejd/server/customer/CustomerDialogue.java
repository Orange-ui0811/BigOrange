package com.orangejd.server.customer;

import java.util.*;

/** Bound the model view; original messages and full confirmed conditions remain persisted. */
public final class CustomerDialogue {
    private CustomerDialogue() {}
    public static boolean isGreeting(String message) {
        return message.strip().matches("(?iu)(你好|您好|嗨|哈喽|hello|hi|早上好|下午好|晚上好|早安|晚安)[\\s！!。.,，？?～~]*");
    }
    /** A narrow evidence boundary, not a replacement for semantic intent extraction. */
    public static boolean explicitlyRequestsEvidence(String message) {
        if(message.matches("(?s).*(不用|不要|无需|不必|别查|不查).*")) return false;
        return message.matches("(?is).*(知识库[中里内]|根据.{0,8}(知识库|项目资料|项目文档|README)|查(?:一下|下)?知识库).*" )
            && message.matches("(?s).*(什么|哪些|如何|怎么|解释|说明|介绍|归纳|总结|功能|出处).*" );
    }
    public static Map<String,Object> input(String message,Map<String,Object> state,String profile,List<Map<String,Object>> history,String summary,int budget,CustomerJson json) {
        return input(message,state,profile,history,summary,budget,json,Map.of());
    }
    public static Map<String,Object> input(String message,Map<String,Object> state,String profile,List<Map<String,Object>> history,String summary,int budget,CustomerJson json,Map<String,Object> resolution) {
        return CustomerContextAssembler.understanding(message,state,profile,history,summary,budget,json,resolution,
            CustomerContextPacket.Scope.from(0,state,0),List.of(),List.of()).requireInput();
    }
    /** Protected task projection. Audit records and other-task requirements are not active input. */
    static Map<String,Object> stateInput(Map<String,Object> state) {
        Map<String,Object> visible=new LinkedHashMap<>();
        for(String field:CustomerState.FIELDS) if(state.containsKey(field)) visible.put(field,state.get(field));
        if(state.containsKey("lastCandidates")) visible.put("lastCandidates",CustomerWorkflow.rows(state.get("lastCandidates")).stream().map(CustomerProjectFeedback::identity).toList());
        if(state.get("focusedProject") instanceof Map<?,?> focus) visible.put("focusedProject",CustomerProjectFeedback.identity(focus));
        if(state.get("excludedProjects") instanceof List<?> excluded)visible.put("excludedProjects",excluded.stream().skip(Math.max(0,excluded.size()-3)).toList());
        if(state.containsKey("lastQuestion")) visible.put("lastQuestion",state.get("lastQuestion"));
        if(state.containsKey("clearedFields")) visible.put("clearedFields",state.get("clearedFields"));
        if(state.containsKey("lastChangeSummary")) visible.put("lastChangeSummary",state.get("lastChangeSummary"));
        if(state.containsKey("preferences")) visible.put("preferences",state.get("preferences"));
        if(state.get("pendingConditions") instanceof List<?> pending) visible.put("pendingConditions",pending.stream().limit(3).filter(Map.class::isInstance).map(raw->{ Map<?,?> p=(Map<?,?>)raw; return Map.of("field",Objects.toString(p.get("field"),"other"),"question",clip(Objects.toString(p.get("question"),""),250)); }).toList());
        visible.put("reviewStatus",CustomerPurpose.status(state));
        visible.put("recommendationTask",CustomerTaskProgress.visible(state));
        if(CustomerWorkflow.enabled(state))visible.put("workflowTask",taskInput(state));
        return visible;
    }
    /** Model routing needs semantic identities, not audit history, evidence copies or question dependency maps. */
    static Map<String,Object> taskInput(Map<String,Object> state) {
        var task=CustomerWorkflow.task(state);
        var view=pick(task,"id","status","requirementVersion","currentListId");
        view.put("requirements",CustomerWorkflow.requirements(state).stream().filter(r->!"REMOVED".equals(r.get("status")))
            .map(r->pick(r,"id","revision","field","kind","text","strength","assertion","status")).toList());
        view.put("question",pick(CustomerWorkflow.question(state),"id","kind","text","options"));
        view.put("migrationPending",Boolean.TRUE.equals(CustomerWorkflow.workflow(state).get("migrationPending")));
        if(task.get("selectedProject") instanceof Map<?,?> selected)view.put("selectedProject",CustomerProjectFeedback.identity(selected));
        var paused=CustomerWorkflow.rows(CustomerWorkflow.workflow(state).get("archivedTasks")).stream().filter(t->"PAUSED".equals(t.get("status"))).toList();
        view.put("pausedTasks",paused.stream().skip(Math.max(0,paused.size()-6)).map(t->pick(t,"id","status","createdRunId")).toList());
        view.put("pausedTaskCount",paused.size());view.put("otherTasksRequireHistoryRecovery",!paused.isEmpty());
        return view;
    }
    private static Map<String,Object> pick(Map<String,Object> source,String... keys) {
        var result=new LinkedHashMap<String,Object>();for(String key:keys)if(source.containsKey(key))result.put(key,source.get(key));return result;
    }
    private static String clip(String value,int max) { int end=Math.min(max,value.length()); if(end>0 && end<value.length() && Character.isHighSurrogate(value.charAt(end-1))) end--; return value.substring(0,end); }
    public static String reply(Map<String,Object> action) {
        if(!(action.get("reply") instanceof String text) || text.isBlank() || text.length()>6000) throw new IllegalArgumentException("INVALID_CHAT_REPLY");
        // Plain chat has no authoritative citations or project cards. Remove invented citation markers.
        text=text.replaceAll("\\[E\\d+(?:\\.S\\d+)?\\]","").replaceAll("(?i)(?:https?://|www\\.)[^\\s<>]+","（链接尚未核实）");
        return CustomerText.paragraphs(text).strip();
    }
}
