package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerRecommendationAdviceTest {
    Map<String,Object> card() { return new HashMap<>(Map.of("id",1,"type","EXTERNAL_PROJECT","name","acme/demo","importable",false,"references",List.of(Map.of("evidenceId","E1","text","支持检索增强生成")))); }
    Map<String,Object> output(String key,String evidence,String advice) {
        return Map.of("projects",List.of(Map.of("key",key,"evidenceIds",List.of(evidence),"recommendationAdvice",advice,"difficultyAdvice","建议先确认运行要求","vibeCodingAdvice","建议使用AI辅助编写测试并人工核对","steps",List.of("核对示例","尝试小改造","验证并复盘"))));
    }
    @Test void suggestionsAreBoundToOwnEvidenceAndCannotEnableImport() {
        var card=card();var cards=List.of(card);
        CustomerRecommendationAdvice.apply(output("EXTERNAL_PROJECT:1","E1","建议用一个知识问答示例展示求职能力"),cards,Map.of("goal","求职"));
        assertThat(card).containsEntry("importable",false).containsKey("practiceAdvice");
        assertThat(((Map<?,?>)card.get("practiceAdvice")).get("kind")).isEqualTo("SUGGESTION");
        var report=CustomerRecommendationAdvice.report(cards,Map.of("topic",List.of("AI应用开发"),"maxDurationDays",180));
        assertThat(report.get("direction").toString()).contains("RAG","智能体");
        assertThat(report.get("timeline").toString()).contains("180天","不是完成承诺");
    }
    @Test void forgedProjectCrossProjectCitationAndLanguageConflictAreRejected() {
        for(var output:List.of(output("EXTERNAL_PROJECT:2","E1","建议先运行示例"),output("EXTERNAL_PROJECT:1","E2","建议先运行示例"),output("EXTERNAL_PROJECT:1","E1","建议换用Python"),output("EXTERNAL_PROJECT:1","E1","需求增长85%"))) {
            var card=card();assertThatThrownBy(()->CustomerRecommendationAdvice.apply(output,List.of(card),Map.of("language","Java"))).isInstanceOf(IllegalArgumentException.class);
            assertThat(card).doesNotContainKey("practiceAdvice").containsEntry("importable",false);
        }
    }
    @Test void noEvidenceUsesClearlyGenericPathWithoutInventingDetails() {
        var card=card();card.put("references",List.of());
        assertThat((List<?>)CustomerRecommendationAdvice.input(List.of(card),Map.of()).get("projects")).isEmpty();
        var report=CustomerRecommendationAdvice.report(List.of(card),Map.of());
        var learning=(List<Map<String,Object>>)report.get("learning");
        assertThat(learning.getFirst()).containsEntry("personalized",false);
        assertThat(learning.getFirst().get("steps").toString()).contains("尚未确认");
    }
}
