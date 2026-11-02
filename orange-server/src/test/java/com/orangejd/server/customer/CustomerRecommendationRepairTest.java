package com.orangejd.server.customer;

import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerRecommendationRepairTest {
    @Test void whitespaceOnlyQuoteVariationIsRestoredToLiteralSourceWithoutChangingWords() {
        String message="只推荐 Java 项目";
        var next=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("language",Map.of("value","Java","quote","只推荐Java项目"))),message,"",new ArrayList<>());
        assertThat(next).containsEntry("language","Java").doesNotContainKey("pendingConditions");
        assertThat(CustomerState.groundedQuote(message,"只推荐Java项目")).isEqualTo(message);
        assertThat(CustomerState.groundedQuote(message,"只推荐Python项目")).isNull();
        assertThat(CustomerState.groundedQuote("必须不使用 Docker","必须使用Docker")).isNull();
    }
    @Test void duplicatedConfirmedLanguageCannotCreateAnotherClarificationButConflictingLanguageCan() {
        String quote="只推荐 Java 项目";
        var action=Map.<String,Object>of("intent","RECOMMEND","preferences",List.of(quote));
        assertThat(CustomerState.reconcile(Map.of("language","Java"),action,quote,"",new ArrayList<>())).doesNotContainKey("pendingConditions");
        assertThat(CustomerState.reconcile(Map.of("language","Python"),action,quote,"",new ArrayList<>())).containsKey("pendingConditions");
    }
    @Test void groundedOptionalPreferenceDoesNotBlockAndHardRequirementCannotBeDowngraded() {
        String preference="希望先从可运行的小功能起步";
        var action=Map.<String,Object>of("intent","RECOMMEND","preferences",List.of(preference),"pendingConditions",List.of(Map.of("field","other","quote",preference,"question","要具体哪一种功能？")));
        var next=CustomerState.reconcile(Map.of(),action,preference,"",new ArrayList<>());
        assertThat(next).containsEntry("preferences",List.of(preference)).doesNotContainKey("pendingConditions");
        var hard=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","preferences",List.of("必须离线运行")),"必须离线运行","",new ArrayList<>());
        assertThat(hard).containsKey("pendingConditions").doesNotContainKey("preferences");
        var fake=CustomerState.reconcile(Map.of(),action,"推荐项目","",new ArrayList<>());
        assertThat(fake).doesNotContainKey("preferences");
    }
    CustomerProjects service=new CustomerProjects(mock(BeginnerProjectMapper.class),mock(CareerProjectMapper.class),new RagBConstraintMatcher());
    Map<String,Object> project(String description) {
        return new HashMap<>(Map.of("type","EXTERNAL_PROJECT","id",1L,"name","acme/demo","url","https://github.com/acme/demo","status","DOCUMENTED","description",description));
    }
    @Test void multiTopicArrayAndLegacyStringAreBothNormalizedWithoutClarification() {
        for(Object value:List.of(List.of("RAG","知识库"),"RAG、知识库")) {
            var rejected=new ArrayList<Map<String,Object>>();
            var next=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("topic",Map.of("value",value,"quote","RAG、知识库"),"maxDurationDays",Map.of("value",Map.of("quantity",6,"unit","MONTH","relation","AT_MOST"),"quote","半年"))),"Java RAG、知识库，半年","",rejected);
            assertThat(next).containsEntry("topic",List.of("RAG","知识库")).containsEntry("maxDurationDays",180).doesNotContainKey("pendingConditions");
            assertThat(rejected).isEmpty();
        }
    }
    @Test void bareConfirmationNeverClearsAnAmbiguousTopic() {
        var state=Map.<String,Object>of("topic",List.of("Java"),"pendingConditions",List.of(Map.of("field","topic","quote","RAG、知识库","question","请确认主题")));
        var errors=new ArrayList<Map<String,Object>>();
        assertThat(CustomerState.reconcile(state,Map.of("intent","STATE"),"确认","",errors)).isEqualTo(state);
        assertThat(errors).isEmpty();
    }
    @Test void strictRequestOverridesGenericPermissionInSameMessage() {
        String text="只能推荐能核实符合条件的现有仓库。资料缺失可以展示探索候选。";
        var next=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("verificationMode",Map.of("value","EXPLORE","quote","资料缺失可以展示探索候选"))),text,"",new ArrayList<>());
        assertThat(next).containsEntry("verificationMode","STRICT");
    }
    @Test void unknownMetadataDoesNotEraseTheTopicAndStrictCannotExploreIt() {
        var unrelated=project("JSON格式工具");
        assertThat(service.explorable(unrelated,Map.of("topic",List.of("量子芯片")))).isFalse();
        var relevant=project("RAG knowledge-base application");
        var conditions=Map.<String,Object>of("topic",List.of("RAG","知识库"),"language","Java","maxDurationDays",180);
        assertThat(service.explorable(relevant,conditions)).isTrue();
        var strict=new HashMap<>(conditions);strict.put("verificationMode","STRICT");
        assertThat(service.explorable(relevant,strict)).isFalse();
        relevant.put("language","Java");relevant.put("duration","两个月");
        assertThat(service.accepts(relevant,strict)).isTrue();
        relevant.put("duration","一年");assertThat(service.explorable(relevant,strict)).isFalse();
    }
    @Test void resourceDirectoryCannotPretendToBeARunnableProject() {
        assertThat(service.explorable(project("精选的RAG资源合集"),Map.of("topic",List.of("RAG")))).isFalse();
        assertThat(service.explorable(project("This repository is a compilation of well-written, step-by-step guides for re-creating technologies."),Map.of())).isFalse();
        assertThat(CustomerTopics.matches(List.of("Spring Boot"),"Spring Boot application")).isTrue();
        assertThat(CustomerTopics.matches(List.of("RAG"),"storage")).isFalse();
    }
    @Test void parentAiApplicationMatchesRagWithoutBroadeningAnExplicitRagConstraint() {
        for(String topic:List.of("AI应用开发","AI 应用开发","人工智能应用开发","大模型应用开发方向")) {
            assertThat(service.explorable(project("Retrieval-augmented generation knowledge base"),Map.of("topic",List.of(topic)))).isTrue();
            assertThat(CustomerTopics.retrievalQuery(List.of(topic))).contains("RAG","智能体");
        }
        assertThat(CustomerTopics.matches(List.of("RAG"),"An AI agent for browser automation")).isFalse();
        assertThat(CustomerTopics.matches(List.of("AI应用开发"),"storage management and CLI agent for monitoring")).isFalse();
        var knownPython=project("RAG application");knownPython.put("language","Python");
        assertThat(service.explorable(knownPython,Map.of("topic",List.of("AI应用开发"),"language","Java"))).isFalse();
    }
    @Test void hardwareRestrictionIsRetainedAndUnknownDoesNotPassStrictMode() {
        var next=CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("allowHardware",Map.of("value",false,"quote","不能使用硬件"))),"不能使用硬件","",new ArrayList<>());
        assertThat(next).containsEntry("allowHardware",false);
        next.put("verificationMode","STRICT");assertThat(service.explorable(project("RAG"),next)).isFalse();
    }
    @Test void topicRejectionRecordsTheValueTypeWithoutLoggingProfileContents() {
        var rejected=new ArrayList<Map<String,Object>>();
        CustomerState.reconcile(Map.of(),Map.of("intent","RECOMMEND","changes",Map.of("topic",Map.of("value",42,"quote","RAG"))),"RAG","",rejected);
        assertThat(rejected.getFirst()).containsEntry("valueType","Integer").containsEntry("reason","INVALID_TOPICS");
    }
}
