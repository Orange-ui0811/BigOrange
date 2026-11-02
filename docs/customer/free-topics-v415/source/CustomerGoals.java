package com.orangejd.server.customer;

import java.util.*;

/** Goals are user preferences, not source categories or eligibility constraints. */
public final class CustomerGoals {
    private CustomerGoals() {}
    public static List<String> values(Object raw) {
        if(raw==null) return List.of();
        List<?> input=raw instanceof List<?> list?list:List.of(raw);
        if(input.size()>6) throw new IllegalArgumentException("TOO_MANY_GOALS");
        Set<String> result=new LinkedHashSet<>();
        for(Object item:input) {
            if(!(item instanceof String value) || value.isBlank() || value.length()>120) throw new IllegalArgumentException("INVALID_GOAL");
            result.add(value.strip());
        }
        return List.copyOf(result);
    }
    public static String display(Object raw) { return String.join("、",values(raw)); }
    public static boolean learning(Object raw) { return values(raw).stream().anyMatch(s->s.matches(".*(学习|练手|熟悉|提升技能).*")); }
    public static boolean career(Object raw) { return values(raw).stream().anyMatch(s->s.matches(".*(求职|面试|简历|就业).*")); }
    public static Object update(Object old,Object raw,String operation,String quote,boolean defaults) {
        List<String> incoming=values(raw);
        if(incoming.isEmpty()) throw new IllegalArgumentException("INVALID_GOAL");
        for(String goal:incoming) {
            // The model normalizes semantic presets. Repeating literal matching here would
            // reject paraphrases such as "挣点外快". Custom goals retain the user's wording.
            boolean grounded=Set.of("学习","求职","赚钱/接单/创业","探索技术兴趣").contains(goal) || quote.contains(goal);
            if(!grounded) throw new IllegalArgumentException("UNSUPPORTED_GOAL");
        }
        Set<String> result=new LinkedHashSet<>(values(old));
        switch(operation) {
            case "SET" -> { result.clear(); result.addAll(incoming); }
            case "ADD" -> {
                if(defaults) throw new IllegalArgumentException("UNSUPPORTED_GOAL_OPERATION");
                result.addAll(incoming);
            }
            case "REMOVE" -> {
                if(defaults) throw new IllegalArgumentException("UNSUPPORTED_GOAL_OPERATION");
                result.removeAll(incoming);
            }
            default -> throw new IllegalArgumentException("INVALID_GOAL_OPERATION");
        }
        if(result.size()>6) throw new IllegalArgumentException("TOO_MANY_GOALS");
        return result.isEmpty()?null:result.size()==1?result.iterator().next():List.copyOf(result);
    }
}
