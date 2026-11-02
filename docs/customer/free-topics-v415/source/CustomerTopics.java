package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.Pattern;

/** A bounded list internally; accepts legacy strings without changing their meaning. */
public final class CustomerTopics {
    private CustomerTopics() {}
    public static List<String> normalize(Object value) {
        List<?> raw=value instanceof List<?> list?list:value instanceof String text?Arrays.asList(text.split("[,，、;；]+")):List.of();
        if(raw.isEmpty() || raw.size()>6) throw new IllegalArgumentException("INVALID_TOPICS");
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(Object item:raw) {
            if(!(item instanceof String s) || s.isBlank() || s.strip().length()>80) throw new IllegalArgumentException("INVALID_TOPICS");
            result.add(s.strip());
        }
        return List.copyOf(result);
    }
    public static String display(Object value) { return value==null?"":String.join("、",normalize(value)); }
    public static boolean matches(Object topics,String facts) {
        if(topics==null)return true;
        String text=Objects.toString(facts,"").toLowerCase(Locale.ROOT);
        return normalize(topics).stream().anyMatch(topic->aliases(topic).stream().anyMatch(alias->contains(text,alias)));
    }
    private static List<String> aliases(String topic) {
        String t=topic.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
        // Parent topics may match their children, never the other way around.
        if(isAiApplication(t)) return List.of("ai应用","ai 应用","人工智能应用","大模型应用","llm application","llm-powered",
            "rag","retrieval augmented","retrieval-augmented","知识库","knowledge base","智能体","ai agent","llm agent","聊天机器人","chatbot");
        return switch(t) {
            case "rag","检索增强","检索增强生成" -> List.of("rag","检索增强","retrieval augmented","retrieval-augmented");
            case "知识库" -> List.of("知识库","knowledge base","knowledge-base","rag");
            case "agent","智能体" -> List.of("agent","agents","智能体");
            case "spring boot","springboot" -> List.of("spring boot","springboot","spring-boot");
            case "数据分析" -> List.of("数据分析","数据可视化","data analysis","data analytics","data visualization");
            default -> List.of(t);
        };
    }
    static boolean isAiApplication(String topic) {
        return topic.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").matches("(?:ai|人工智能|大模型|llm)应用(?:开发|方向|开发方向)?");
    }
    public static List<String> subtopics(Object topics) {
        if(topics==null)return List.of();
        return normalize(topics).stream().anyMatch(CustomerTopics::isAiApplication)?List.of("RAG","智能体","聊天机器人"):List.of();
    }
    public static String retrievalQuery(Object topics) {
        var children=subtopics(topics);
        return display(topics)+(children.isEmpty()?"":" "+String.join(" ",children));
    }
    private static boolean contains(String text,String alias) {
        return alias.chars().allMatch(c->c<128)?Pattern.compile("(?<![a-z0-9])"+Pattern.quote(alias)+"(?![a-z0-9])").matcher(text).find():text.contains(alias);
    }
}
