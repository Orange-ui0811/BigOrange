package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerEvidenceRoutingTest extends CustomerConversationTest {
    @ParameterizedTest @ValueSource(strings={"请解释知识库中RAG项目通常能实现什么功能","根据项目文档总结主要功能","请根据README说明部署步骤"})
    void chatClassificationCannotBypassExplicitEvidenceRequest(String message) {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","未经核验的内容","changes",Map.of()));
        when(context.knowledge(anyString(),anySet())).thenThrow(new IllegalStateException("EMBEDDING_COOLDOWN"));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        verify(context).knowledge(eq(message),anySet());
        var result=org.mockito.ArgumentCaptor.forClass(Map.class);verify(store).complete(eq("r"),result.capture(),anyMap(),any());
        assertThat(result.getValue()).containsEntry("intent","QUESTION").containsEntry("status","KNOWLEDGE_UNAVAILABLE");
        assertThat(result.getValue().get("answer").toString()).doesNotContain("未经核验的内容");
    }
    @Test void generalConceptGreetingAndExplicitNoSearchKeepSemanticRouting() {
        for(String input:List.of("你好","解释一下RAG是什么","不用查知识库，简单解释RAG","我想记录一个知识库项目"))
            assertThat(CustomerDialogue.explicitlyRequestsEvidence(input)).isFalse();
    }
}
