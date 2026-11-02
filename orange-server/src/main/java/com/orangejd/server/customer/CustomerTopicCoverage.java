package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative literal coverage guard, not a second semantic extractor. */
final class CustomerTopicCoverage {
    private CustomerTopicCoverage() {}
    private static final Pattern REQUEST=Pattern.compile("(?:推荐|帮我挑|帮我找|我想找|想做|想找|找一下|找些|寻找|方向(?:改成|换成)|主题(?:改成|换成))");
    // Recover an explicitly requested application noun phrase, without a domain keyword catalogue.
    private static final Pattern APPLICATION=Pattern.compile("(?:找|寻找|推荐|想做)([^，,。；;！？!?\\n]{1,60}?(?:系统|软件|工具|平台|应用))");
    private static final Pattern TOPIC=Pattern.compile("(?i)(?<![a-z0-9])(?:retrieval[- ]augmented generation|RAG|chatbot|agent)(?![a-z0-9])|检索增强生成|检索增强|(?:AI|人工智能|大模型|LLM)\\s*应用(?:开发)?|智能体|聊天机器人|知识库|数据分析");
    private static final Pattern NEGATION=Pattern.compile("(?:不要|不想|不做|不找|不推荐|不需要|不含|不一定|排除|不是|而非|无需|不用|取消|不限|不限制|例如|比如|什么是|解释|包括|\\bnot\\b|\\bwithout\\b|\\bexclude\\b)[^，,。；;！？!?]{0,20}$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DISMISS_AFTER=Pattern.compile("^(?:方向|项目|类|这一类)?(?:就)?(?:不要|不考虑|不需要|不做|不限)|^.{0,12}(?:只是例子|仅供参考)");

    static List<String> complete(Map<String,Object> action,String message) {
        if(!Set.of("RECOMMEND","COLLECT").contains(action.get("intent")) || (!REQUEST.matcher(message).find()&&!APPLICATION.matcher(message).find()))return List.of();
        var existing=action.get("changes") instanceof Map<?,?> map?map:Map.of();
        // Explicit operations (especially CLEAR/REMOVE) remain authoritative and are never overwritten.
        if(existing.get("topic") instanceof Map<?,?>)return List.of();
        var topics=new LinkedHashSet<String>();
        var application=APPLICATION.matcher(message);
        while(application.find()) {
            int boundary=Math.max(Math.max(message.lastIndexOf('，',application.start()),message.lastIndexOf(',',application.start())),Math.max(message.lastIndexOf('。',application.start()),message.lastIndexOf('；',application.start())));
            String prefix=message.substring(boundary+1,application.start());
            String phrase=application.group(1),suffix=message.substring(application.end(),Math.min(message.length(),application.end()+20));
            if(NEGATION.matcher(prefix).find() || prefix.matches("(?s).*(不|别|不要)$") || phrase.matches("(?s).*(不要|不含|不需要|不限|例如|比如|[“\"]).*") || suffix.matches("^[吗？?].*") || DISMISS_AFTER.matcher(suffix).find() || prefix.contains("“") || prefix.contains("\""))continue;
            topics.add(phrase);
        }
        var matcher=TOPIC.matcher(message);
        while(matcher.find()) {
            int boundary=Math.max(Math.max(message.lastIndexOf('，',matcher.start()),message.lastIndexOf(',',matcher.start())),Math.max(message.lastIndexOf('。',matcher.start()),message.lastIndexOf('；',matcher.start())));
            String prefix=message.substring(boundary+1,matcher.start());
            String suffix=message.substring(matcher.end(),Math.min(message.length(),matcher.end()+20));
            if(NEGATION.matcher(prefix).find() || DISMISS_AFTER.matcher(suffix).find() || prefix.contains("“") || prefix.contains("\""))continue;
            topics.add(matcher.group());
        }
        if(topics.isEmpty() || topics.size()>6)return List.of();
        Map<String,Object> changes=new LinkedHashMap<>();existing.forEach((key,value)->changes.put(key.toString(),value));
        changes.put("topic",Map.of("operation","SET","value",List.copyOf(topics),"quote",message));
        action.put("changes",changes);
        return List.copyOf(topics);
    }
}
