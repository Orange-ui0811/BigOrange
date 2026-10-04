package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.*;

/** One read-only preamble plus one business action. This is deliberately not an agent loop. */
public final class CustomerTurnPlan {
    private CustomerTurnPlan() {}
    public static boolean hasExplicitTask(String message) {
        return message.matches("(?s).*(帮我找|开始查找|继续查找|继续推荐|再推荐|重新推荐|推荐给我|再找|改成|换成|改为|放宽到|调整为|取消限制|不再限定).*" );
    }
    public static String preamble(Map<String,Object> action, Map<String,Object> prior, String message) {
        if(!(action.get("beforeTask") instanceof Map<?,?> step)
            || !(step.get("quote") instanceof String quote) || quote.isBlank() || !message.contains(quote)) return "";
        return switch(Objects.toString(step.get("intent"),"")) {
            case "STATE" -> CustomerConditionFacts.describe(prior);
            case "EXPLAIN" -> CustomerTaskProgress.describe(prior)+CustomerPurpose.explanation(prior);
            default -> "";
        };
    }
    public static List<Integer> explicitPositions(String message) {
        Matcher matcher=Pattern.compile("第([一二三123])个").matcher(message);
        List<Integer> out=new ArrayList<>();
        while(matcher.find()) {
            int n=switch(matcher.group(1)) {case "一","1"->1;case "二","2"->2;default->3;};
            if(!out.contains(n)) out.add(n);
        }
        if(out.isEmpty() && (message.contains("前两个") || message.contains("前2个"))) return List.of(1,2);
        return out;
    }
    /** Resolve references only against server-owned identities; never accept a model-generated project ID. */
    public static void resolve(Map<String,Object> action, Map<String,Object> state, String message) {
        List<?> candidates=state.get("lastCandidates") instanceof List<?> c?c:List.of();
        List<Integer> explicit=explicitPositions(message);
        if(!explicit.isEmpty())action.put("positions",explicit);
        boolean refers= !explicit.isEmpty() || message.matches("(?s).*(它|这个项目|该项目|刚才那个).*" );
        if(refers && explicit.isEmpty() && (!(action.get("positions") instanceof List<?> p) || p.isEmpty())) {
            if(state.get("focusedProject") instanceof Map<?,?> focus) {
                for(int i=0;i<candidates.size();i++) if(same(focus,(Map<?,?>)candidates.get(i)))action.put("positions",List.of(i+1));
            } else if(candidates.size()==1) action.put("positions",List.of(1));
        }
        if(refers && "QUESTION".equals(action.get("intent")))action.put("intent","DETAIL");
    }
    public static void focus(Map<String,Object> state, List<Map<String,Object>> cards, String run) {
        if(cards.size()!=1)return;
        var card=cards.getFirst();
        state.put("focusedProject",Map.of("id",card.get("id"),"type",card.get("type"),"name",card.get("name"),"runId",run));
    }
    private static boolean same(Map<?,?> a,Map<?,?> b) {
        return Objects.toString(a.get("type"),"").equals(Objects.toString(b.get("type"),""))
            && Objects.toString(a.get("id"),"").equals(Objects.toString(b.get("id"),""));
    }
}
