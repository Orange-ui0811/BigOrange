package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerTopicCoverageTest {
    @ParameterizedTest @ValueSource(strings={"给小店找库存管理系统，必须支持扫码，日常必须离线运行。先记录，不用推荐。","帮诊所找预约排队系统","我想做仓储软件"})
    void applicationPurposeCannotFallThroughToUnrelatedRecommendations(String message) {
        var action=action();
        assertThat(CustomerTopicCoverage.complete(action,message)).isNotEmpty();
        var state=CustomerState.reconcile(Map.of(),action,message,"",new ArrayList<>());
        assertThat(state).containsKey("topic");
    }
    @ParameterizedTest @ValueSource(strings={"不要找库存管理系统，推荐其他项目","推荐不含联网功能的软件","推荐项目，比如找库存系统只是例子","你能找库存管理系统吗？","不要推荐库存系统"})
    void applicationNegationsAndExamplesAreNotAdded(String message) {
        assertThat(CustomerTopicCoverage.complete(action(),message)).isEmpty();
    }
    Map<String,Object> action(){return new LinkedHashMap<>(Map.of("intent","RECOMMEND","changes",Map.of()));}
    @ParameterizedTest @ValueSource(strings={"帮我挑Python的检索增强生成项目，求职用，最多六个月。","推荐RAG项目","我想找AI应用开发项目","帮我找聊天机器人项目","帮我找retrieval-augmented generation项目"})
    void missingLiteralTopicIsRecoveredWithoutChangingOtherFields(String message){
        var action=action();assertThat(CustomerTopicCoverage.complete(action,message)).isNotEmpty();
        var rejected=new ArrayList<Map<String,Object>>();
        var state=CustomerState.reconcile(Map.of("language","Python","maxDurationDays",180),action,message,"",rejected);
        assertThat(state).containsEntry("language","Python").containsEntry("maxDurationDays",180).containsKey("topic");assertThat(rejected).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"推荐项目，不要RAG","不推荐RAG，帮我找其他项目","推荐不含RAG的项目","RAG就不要了，帮我找其他项目","推荐项目，RAG只是例子","推荐项目，不限制RAG方向","推荐项目，例如RAG是什么意思？","解释一下RAG，再推荐项目","推荐项目，“RAG”只是我引用的例子","推荐几个Python项目，不限制方向"})
    void ambiguousNegatedAndExampleMentionsAreNotNewRequirements(String message){assertThat(CustomerTopicCoverage.complete(action(),message)).isEmpty();}
    @Test void explicitClearAndSemanticTopicsAreNeverOverwritten(){
        for(Object change:List.of(Map.of("operation","CLEAR"),Map.of("value",List.of("企业文档问答"),"quote","RAG"))){
            var action=action();action.put("changes",Map.of("topic",change));assertThat(CustomerTopicCoverage.complete(action,"推荐RAG项目")).isEmpty();assertThat(((Map<?,?>)action.get("changes")).get("topic")).isEqualTo(change);
        }
    }
    @Test void chatAndKnowledgeQuestionsDoNotAcquireProjectConditions(){
        var action=action();action.put("intent","QUESTION");assertThat(CustomerTopicCoverage.complete(action,"知识库有没有推荐RAG项目的说明？")).isEmpty();
    }
}
