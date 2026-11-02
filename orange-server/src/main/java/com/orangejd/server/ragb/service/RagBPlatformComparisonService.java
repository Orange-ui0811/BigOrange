package com.orangejd.server.ragb.service;

import com.orangejd.server.service.TrendContextBuilder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RagBPlatformComparisonService {
    private final TrendContextBuilder platform;
    private final RagBSearchService b;
    public RagBPlatformComparisonService(TrendContextBuilder platform, RagBSearchService b) {this.platform=platform; this.b=b;}
    public Map<String,Object> retrieve(String question) {
        var a=platform.previewKnowledge(question);
        long started=System.nanoTime();
        var result=b.retrieve(question,8,.55,Set.of());
        Map<String,Object> left=new LinkedHashMap<>();
        left.put("trace",a.trace()); left.put("promptContext",a.text());
        left.put("durationMs",a.trace().get("durationMs"));
        left.put("evidence",a.trace().get("evidence"));
        Map<String,Object> right=new LinkedHashMap<>();
        right.put("generation",result.generation()); right.put("hits",result.hits());
        right.put("durationMs",(System.nanoTime()-started)/1_000_000.0);
        right.put("answerPolicy",result.answerPolicy()); right.put("excludedReasons",result.excludedReasons());
        return Map.of("a",left,"b",right,"scope","A platform knowledge retrieval and compressed evidence; B native retrieval. Excludes graph/candidate recommendation and final product generation.");
    }
}
