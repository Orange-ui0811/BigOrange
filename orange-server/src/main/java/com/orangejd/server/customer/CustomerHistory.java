package com.orangejd.server.customer;

import java.util.*;

/** Extractive, source-addressable memory. Never turns a historical suggestion into a confirmed condition. */
public final class CustomerHistory {
    private CustomerHistory() {}
    public record View(List<Map<String,Object>> messages, String summary, long through) {}
    public static View build(List<Map<String,Object>> descending, String currentRun, int budget, CustomerJson json) {
        Map<String,List<Map<String,Object>>> turns=new LinkedHashMap<>();
        var rows=new ArrayList<>(descending);Collections.reverse(rows);
        for(var row:rows) {
            if(currentRun.equals(row.get("run_id")))continue;
            String key=Objects.toString(row.get("run_id"),"message:"+row.get("id"));
            turns.computeIfAbsent(key,k->new ArrayList<>()).add(row);
        }
        List<List<Map<String,Object>>> groups=new ArrayList<>(turns.values());
        List<Map<String,Object>> recent=new ArrayList<>(),omitted=new ArrayList<>();int left=budget;
        boolean full=false;
        for(int i=groups.size()-1;i>=0;i--) {
            var group=groups.get(i);List<Map<String,Object>> view=group.stream().map(row->view(row,json)).toList();
            int size=CustomerModel.estimate(json.write(view));
            if(full || size>left) { full=true;omitted.addAll(group); }
            else { recent.addAll(0,view);left-=size; }
        }
        List<Map<String,Object>> excerpts=new ArrayList<>();long through=0;
        // Store exact source IDs, not a range that misleadingly implies every message was summarized.
        for(var row:omitted) {
            if(!(row.get("id") instanceof Number id) || !"user".equals(row.get("role")))continue;
            if(excerpts.size()>=6)break;
            through=Math.max(through,id.longValue());
            excerpts.add(Map.of("messageId",id.longValue(),"runId",Objects.toString(row.get("run_id"),""),"excerpt",clip(Objects.toString(row.get("content"),""),100)));
        }
        String summary=excerpts.isEmpty()?"":json.write(Map.of("kind","USER_EXCERPTS_NOT_CONFIRMED_CONDITIONS","sources",excerpts));
        return new View(List.copyOf(recent),summary,through);
    }
    private static Map<String,Object> view(Map<String,Object> row,CustomerJson json) {
        String content=Objects.toString(row.get("content"),"");
        if("assistant".equals(row.get("role"))) {
            Map<String,Object> answer;
            try {answer=json.read(content);} catch(IllegalArgumentException ex) {answer=Map.of("status","UNREADABLE_HISTORY","answer","此条历史回答不可解析，不作为事实依据。");}
            Map<String,Object> view=new LinkedHashMap<>();
            view.put("status",answer.getOrDefault("status","UNKNOWN"));
            view.put("answer",clip(Objects.toString(answer.get("answer"),""),260));
            if(answer.get("pendingQuestion") instanceof String q)view.put("pendingQuestion",clip(q,180));
            if(answer.get("projects") instanceof List<?> cards)view.put("projects",cards.stream().limit(3).filter(Map.class::isInstance).map(raw->{var c=(Map<?,?>)raw;return Map.of("type",Objects.toString(c.get("type"),""),"id",Objects.toString(c.get("id"),""),"name",clip(Objects.toString(c.get("name"),""),80));}).toList());
            content=json.write(view);
        } else content=clip(content,400);
        return Map.of("role",row.get("role"),"content",content);
    }
    private static String clip(String value,int max) {
        int end=Math.min(max,value.length());if(end<value.length()&&end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
        return value.substring(0,end);
    }
}
