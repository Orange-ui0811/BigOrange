package com.orangejd.server.customer;

import java.util.*;

/** Explicit project exclusions are session memory, never inferred changes to a user's long-term profile. */
public final class CustomerProjectFeedback {
    private CustomerProjectFeedback() {}
    public static void apply(Map<String,Object> state,Object raw,String message,String run) {
        if(!(raw instanceof List<?> feedback))return;
        List<Map<String,Object>> excluded=new ArrayList<>();
        if(state.get("excludedProjects") instanceof List<?> previous)for(Object p:previous)if(p instanceof Map<?,?> m)excluded.add(copy(m));
        List<?> candidates=state.get("lastCandidates") instanceof List<?> c?c:List.of();
        for(Object entry:feedback.stream().limit(3).toList()) {
            if(!(entry instanceof Map<?,?> f) || !(f.get("quote") instanceof String quote) || quote.isBlank() || quote.length()>200 || !message.contains(quote))continue;
            String operation=Objects.toString(f.get("operation"),"");
            if("RESET".equals(operation) && quote.matches("(?s).*(清除排除|恢复全部|重新考虑所有).*")) {excluded.clear();continue;}
            if(!(f.get("position") instanceof Number n) || n.doubleValue()!=n.intValue() || n.intValue()<1 || n.intValue()>candidates.size())continue;
            var card=(Map<?,?>)candidates.get(n.intValue()-1);String key=key(card);
            if("DISMISS".equals(operation) && quote.matches("(?s).*(不要|不考虑|不喜欢|排除|换一批|换一个|看过).*")) {
                excluded.removeIf(p->key.equals(p.get("key")));
                excluded.add(Map.of("key",key,"name",Objects.toString(card.get("name"),""),"quote",quote,"runId",run));
            } else if("RESTORE".equals(operation) && quote.matches("(?s).*(重新考虑|恢复|再看|取消排除).*"))excluded.removeIf(p->key.equals(p.get("key")));
        }
        while(excluded.size()>24)excluded.removeFirst();
        state.put("excludedProjects",excluded);
    }
    public static boolean excluded(Map<String,Object> project,Map<String,Object> state) {
        if(!(state.get("excludedProjects") instanceof List<?> entries))return false;
        return entries.stream().anyMatch(p->p instanceof Map<?,?> m && (key(project).equals(m.get("key")) || (project.get("type")+":"+project.get("id")).equals(m.get("key"))));
    }
    public static Map<String,Object> identity(Map<?,?> card) {
        var out=new LinkedHashMap<String,Object>();for(String key:List.of("id","type","name","url"))if(card.get(key)!=null)out.put(key,card.get(key));return out;
    }
    private static String key(Map<?,?> card) {return card.get("url") instanceof String url?url.replaceAll("/+$","").toLowerCase(Locale.ROOT):card.get("type")+":"+card.get("id");}
    private static Map<String,Object> copy(Map<?,?> raw) {var out=new LinkedHashMap<String,Object>();raw.forEach((k,v)->out.put(k.toString(),v));return out;}
}
