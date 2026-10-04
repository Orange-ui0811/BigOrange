package com.orangejd.server.customer;

import java.util.*;

/** Bound the model view; original messages and full confirmed conditions remain persisted. */
public final class CustomerDialogue {
    private CustomerDialogue() {}
    public static boolean isGreeting(String message) {
        return message.strip().matches("(?iu)(你好|您好|嗨|哈喽|hello|hi|早上好|下午好|晚上好|早安|晚安)[\\s！!。.,，？?～~]*");
    }
    public static Map<String,Object> input(String message,Map<String,Object> state,String profile,List<Map<String,Object>> history,String summary,int budget,CustomerJson json) {
        Map<String,Object> visible=new LinkedHashMap<>();
        for(String field:CustomerState.FIELDS) if(state.containsKey(field)) visible.put(field,state.get(field));
        if(state.containsKey("lastCandidates")) visible.put("lastCandidates",state.get("lastCandidates"));
        if(state.containsKey("focusedProject")) visible.put("focusedProject",state.get("focusedProject"));
        if(state.get("excludedProjects") instanceof List<?> excluded)visible.put("excludedProjects",excluded.stream().skip(Math.max(0,excluded.size()-3)).toList());
        if(state.containsKey("lastQuestion")) visible.put("lastQuestion",state.get("lastQuestion"));
        if(state.containsKey("clearedFields")) visible.put("clearedFields",state.get("clearedFields"));
        if(state.containsKey("lastChangeSummary")) visible.put("lastChangeSummary",state.get("lastChangeSummary"));
        if(state.containsKey("preferences")) visible.put("preferences",state.get("preferences"));
        if(state.get("pendingConditions") instanceof List<?> pending) visible.put("pendingConditions",pending.stream().limit(3).filter(Map.class::isInstance).map(raw->{ Map<?,?> p=(Map<?,?>)raw; return Map.of("field",Objects.toString(p.get("field"),"other"),"question",clip(Objects.toString(p.get("question"),""),250)); }).toList());
        visible.put("reviewStatus",CustomerPurpose.status(state));
        visible.put("recommendationTask",CustomerTaskProgress.visible(state));
        List<Map<String,Object>> recent=new ArrayList<>(history);
        Map<String,Object> input=new LinkedHashMap<>(Map.of("message",message,"state",visible,"profile",profile,"history",recent,"summary",summary));
        input.put("dialogueOnly",isGreeting(message));
        input.put("referenceDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
        while(size(input,json)>budget && !recent.isEmpty()) {
            recent.removeFirst(); if(!recent.isEmpty() && "assistant".equals(recent.getFirst().get("role"))) recent.removeFirst();
        }
        if(size(input,json)>budget) input.put("summary","");
        if(size(input,json)>budget) input.put("profile",clip(profile,600));
        if(size(input,json)>budget) throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        return input;
    }
    private static int size(Map<String,Object> input,CustomerJson json) { return CustomerModel.estimate(CustomerState.PROMPT+json.write(input)); }
    private static String clip(String value,int max) { int end=Math.min(max,value.length()); if(end>0 && end<value.length() && Character.isHighSurrogate(value.charAt(end-1))) end--; return value.substring(0,end); }
    public static String reply(Map<String,Object> action) {
        if(!(action.get("reply") instanceof String text) || text.isBlank() || text.length()>6000) throw new IllegalArgumentException("INVALID_CHAT_REPLY");
        // Plain chat has no authoritative citations or project cards. Remove invented citation markers.
        text=text.replaceAll("\\[E\\d+(?:\\.S\\d+)?\\]","").replaceAll("(?i)(?:https?://|www\\.)[^\\s<>]+","（链接尚未核实）");
        return CustomerText.paragraphs(text).strip();
    }
}
