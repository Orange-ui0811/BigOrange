package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.Pattern;

/** Preserve an explicit first-person use case; never infer a goal from project evidence. */
final class CustomerGoalCoverage {
    private CustomerGoalCoverage() {}
    private static final Pattern SAVE=Pattern.compile("(?:记住|记下|记录|保存).{0,8}(?:用途|目的|目标)|(?:用途|目的|目标).{0,8}(?:记住|记下|记录|保存)");
    private static final Pattern USE=Pattern.compile("(?:给|供)(?:我自己|我们自己|我的|我们的|我|我们)[^，,。；;！？!?]{2,60}?(?:用的|使用|用)(?=[，,。；;！？!?]|$)");
    private static final Pattern NEGATED=Pattern.compile("(?:不是|不要|不用|并非|例如|比如|假如|假设|他说|她说|原文|引用)[^，,。；;！？!?]{0,20}$");

    static String complete(Map<String,Object> action,String message) {
        if(!Set.of("CHAT","COLLECT","RECOMMEND").contains(action.get("intent")) || !SAVE.matcher(message).find())return "";
        var existing=action.get("changes") instanceof Map<?,?> map?map:Map.of();
        var matcher=USE.matcher(message);
        if(!matcher.find())return "";
        String quote=matcher.group(),prefix=message.substring(0,matcher.start());
        if(NEGATED.matcher(prefix).find() || prefix.contains("“") || prefix.contains("\""))return "";
        // A second stated use needs semantic interpretation, not an arbitrary first-match selection.
        if(matcher.find())return "";
        if(existing.get("goal") instanceof Map<?,?> proposal) {
            // Respect valid presets, literal goals, and explicit ADD/REMOVE/CLEAR operations.
            // For a single paraphrased SET with authentic use-case evidence, retain that original
            // use-case instead of saving the model's unsupported wording or silently losing it.
            if(!"SET".equals(proposal.get("operation")))return "";
            String evidence=proposal.get("quote") instanceof String q?CustomerState.groundedQuote(message,q):null;
            if(evidence==null || !evidence.contains(quote))return "";
            List<String> values;
            try {values=CustomerGoals.values(proposal.get("value"));} catch(IllegalArgumentException invalid) {return "";}
            if(values.size()!=1 || CustomerGoals.preset(values.getFirst()) || evidence.contains(values.getFirst()))return "";
        }
        Map<String,Object> changes=new LinkedHashMap<>();existing.forEach((key,value)->changes.put(key.toString(),value));
        changes.put("goal",Map.of("operation","SET","value",quote,"quote",quote));
        action.put("changes",changes);
        return quote;
    }
}
