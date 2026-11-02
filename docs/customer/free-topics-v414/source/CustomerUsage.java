package com.orangejd.server.customer;

import java.util.*;

/** Sum provider-reported usage across all attempts; never manufacture usage for failed requests. */
public final class CustomerUsage {
    private CustomerUsage() {}
    public static Map<String,Object> summarize(List<Map<String,Object>> calls) {
        long input=0,output=0,total=0,reasoning=0,cacheHit=0,cacheMiss=0;
        int unknown=0; Set<String> models=new LinkedHashSet<>();
        for(var call:calls) {
            if(call.get("model") instanceof String model) models.add(model);
            if(!(call.get("usage") instanceof Map<?,?> usage) || !(usage.get("total_tokens") instanceof Number)) { unknown++; continue; }
            input+=count(usage.get("prompt_tokens")); output+=count(usage.get("completion_tokens")); total+=count(usage.get("total_tokens"));
            cacheHit+=count(usage.get("prompt_cache_hit_tokens")); cacheMiss+=count(usage.get("prompt_cache_miss_tokens"));
            if(usage.get("completion_tokens_details") instanceof Map<?,?> details) reasoning+=count(details.get("reasoning_tokens"));
        }
        return Map.of("inputTokens",input,"outputTokens",output,"totalTokens",total,"reasoningTokens",reasoning,
            "cacheHitTokens",cacheHit,"cacheMissTokens",cacheMiss,"calls",calls.size(),"callsWithoutUsage",unknown,"complete",unknown==0,"models",models);
    }
    private static long count(Object value) { return value instanceof Number n?Math.max(0,n.longValue()):0; }
}
