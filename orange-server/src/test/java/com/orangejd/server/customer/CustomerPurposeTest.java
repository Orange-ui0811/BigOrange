package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerPurposeTest {
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerProjects service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> card() {
        return new HashMap<>(Map.of("type","BEGINNER_PROJECT","id",7,"name","demo/charts","url","https://github.com/demo/charts","status","PUBLISHED","language","Python","duration","2个月","description","可连接数据源并提供可视化仪表盘，帮助分析营业趋势。","requiresDocker",false));
    }
    Map<String,Object> approve(Map<String,Object> card) {
        var state=new HashMap<String,Object>(Map.of("topic",List.of("数据处理与可视化")));
        var input=CustomerPurpose.input(List.of(card),state,json,6000);
        CustomerPurpose.apply(Map.of("matches",List.of(Map.of("key",CustomerPurpose.key(card),"relation","DIRECT","quote",card.get("description")))),input,List.of(card),state);
        return state;
    }
    @Test void groundedSemanticMatchWorksWithoutAnExactTopicButCannotBypassHardConstraints() {
        var p=card();var state=approve(p);
        assertThat(service.accepts(p,state)).isTrue();
        state.put("maxDurationDays",180);assertThat(service.accepts(p,state)).isTrue();
        state.put("language","Java");assertThat(service.explorable(p,state)).isFalse();
        state.remove("language");state.put("maxDurationDays",30);assertThat(service.accepts(p,state)).isFalse();
        state.put("maxDurationDays",180);state.put("allowDocker",false);p.put("requiresDocker",true);assertThat(service.explorable(p,state)).isFalse();
    }
    @Test void topicAndDocumentChangesInvalidateProofEvenForIdenticalIds() {
        var p=card();var state=approve(p);
        state.put("topic",List.of("芯片制造"));assertThat(service.accepts(p,state)).isFalse();
        state.put("topic",List.of("数据处理与可视化"));p.put("description","用途已改为其他方向");assertThat(service.accepts(p,state)).isFalse();
    }
    @Test void InventedIdentityQuoteAndDependencyOnlyLexicalHitCannotAuthorizeCards() {
        var p=card();var state=new HashMap<String,Object>(Map.of("topic",List.of("数据分析")));
        var input=CustomerPurpose.input(List.of(p),state,json,6000);
        assertThatThrownBy(()->CustomerPurpose.apply(Map.of("matches",List.of(
            Map.of("key","BEGINNER_PROJECT:999","relation","DIRECT","quote",p.get("description")),
            Map.of("key",CustomerPurpose.key(p),"relation","DIRECT","quote","不存在的项目用途没有出处不能成立。"))),input,List.of(p),state)).hasMessage("INVALID_PURPOSE_REVIEW");
        assertThat(service.accepts(p,state)).isFalse();
        assertThat(CustomerTopics.matches(List.of("数据分析"),"Python NumPy Jupyter mirror hardware")).isFalse();
    }
    @Test void noMatchSuggestionsKeepPurposeAndOnlyProposeVerifiedConstraintChanges() {
        var p=card();var state=approve(p);state.put("language","Java");state.put("maxDurationDays",180);
        var result=CustomerDiagnostics.evaluate(service,List.of(p),state);
        assertThat(CustomerDiagnostics.describe(result)).contains("Python","尚未修改条件").doesNotContain("智能体");
        assertThat(state).containsEntry("language","Java");
    }
    @Test void reviewInputIsBoundedAndProvenanceIsNotAcceptedFromOmittedCandidates() {
        var state=new HashMap<String,Object>(Map.of("topic",List.of("网站开发")));
        List<Map<String,Object>> cards=new ArrayList<>();
        for(int i=0;i<12;i++){var p=card();p.put("id",i);p.put("description","支持搭建前端界面".repeat(150));cards.add(p);}
        var input=CustomerPurpose.input(cards,state,json,6000);
        assertThat(CustomerModel.estimate(CustomerPurpose.REVIEW_PROMPT+json.write(input))).isLessThanOrEqualTo(6000);
        assertThat((List<?>)input.get("candidates")).hasSizeLessThan(12);
    }
    @Test void unknownFactsWordingDoesNotRequireTheOldKeywordWhitelist() {
        var state=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("verificationMode",Map.of("value","EXPLORE","quote","资料未知可以明确标记未知"))),"资料未知可以明确标记未知","",new ArrayList<>());
        assertThat(state).containsEntry("verificationMode","EXPLORE");
        assertThat(state).doesNotContainKey("pendingConditions");
    }
    @Test void readmeBadgesCannotCrowdOutTheActualPurpose() {
        var p=card();p.remove("description");
        p.put("catalogText","[![build](https://example.com/badge.svg)](https://example.com)\n".repeat(50)+"# 仪表盘\n用于整理业务数据并绘制交互式图表。\n");
        var input=CustomerPurpose.input(List.of(p),Map.of("topic",List.of("数据可视化")),json,6000);
        assertThat(input.toString()).contains("整理业务数据").doesNotContain("badge.svg");
    }
    @Test void protocolRequestsDoNotBlockButMixedUnknownHardRequirementsStillDo() {
        var state=new HashMap<String,Object>();var pending=new LinkedHashMap<String,Map<String,Object>>();
        String protocol="没有符合的就提出调整建议，必须等我同意才能修改条件";
        CustomerPreferences.reconcile(state,List.of(protocol),protocol,pending);
        assertThat(pending).isEmpty();
        String hard="必须等我同意才能修改条件且必须支持离线语音";
        CustomerPreferences.reconcile(state,List.of(hard),hard,pending);
        assertThat(pending).containsKey("other");
    }
    @Test void phraseNominationDoesNotTreatEnglishGlueWordsAsPurposeMatches() {
        var chart=card();chart.put("description","Interactive data visualization charts for business datasets.");
        var image=card();image.put("id",8);image.put("url","https://github.com/demo/image");image.put("description","Local image processing and tools and files and data.");
        var state=Map.<String,Object>of("topic",List.of("数据处理与可视化"));
        String query=CustomerPurpose.query(Map.of("queries",List.of("数据清洗","数据可视化","data visualization")),state);
        assertThat(CustomerPurpose.nominate(List.of(image,chart),state,List.of(),query)).containsExactly(chart);
    }
}
