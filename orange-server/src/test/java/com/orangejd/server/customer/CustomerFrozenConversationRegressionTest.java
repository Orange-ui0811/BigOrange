package com.orangejd.server.customer;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Independent v4.19 implementation, captured before extracting shared operations. */
class CustomerFrozenConversationRegressionTest extends LegacyCustomerConversationContract {
    // New coverage guards deliberately fix these omissions in both active engines.
    // Reproduce the historical failure instead of modifying the frozen implementation.
    @Override @Test void omittedRagRequirementIsSavedBeforeProjectPlanning() {
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message","帮我挑Python的检索增强生成项目，求职用，最多六个月。"));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",Map.of()));
        when(context.projects(anyMap())).thenReturn(List.of());
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var state=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),anyMap(),state.capture(),any());
        assertThat(state.getValue()).doesNotContainKey("topic");
        verify(model,never()).call(eq(CustomerPurpose.PLAN_PROMPT),anyMap(),any(),anyList());
    }
    @Override @Test void omittedExplicitPersonalUseIsSavedWithoutTriggeringRecommendation() {
        String message="这个作品是给我自己的书店管理图书用的，先记住用途。";
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","用途已经记下。","changes",Map.of("topic",Map.of("operation","SET","value",List.of("图书管理"),"quote","管理图书"))));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var state=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),anyMap(),state.capture(),any());
        assertThat(state.getValue()).doesNotContainKey("goal");
        assertThat(state.getValue().get("topic")).isEqualTo(List.of("图书管理"));
        verify(context,never()).projects(anyMap());
    }
    @BeforeEach @Override void setup(){
        super.setup();
        var frozen=new FrozenCustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
        conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties()) {
            @Override public void execute(String id,List<Map<String,Object>> usage,Instant deadline) {frozen.execute(id,usage,deadline);}
        };
    }
    @Override @Test void paraphrasedUseWithExactEvidenceSurvivesJavaValidationInEitherEngine() {
        String message="这个作品是给我自己的书店管理图书用的，先记住用途。";
        when(db.run("r")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
        when(model.call(eq(CustomerState.PROMPT),anyMap(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","已记住用途。","changes",Map.of("goal",Map.of("operation","SET","value","自用书店图书管理","quote","给我自己的书店管理图书用的"))));
        conversation.execute("r",new ArrayList<>(),Instant.now().plusSeconds(90));
        var state=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(store).complete(eq("r"),anyMap(),state.capture(),any());
        assertThat(state.getValue()).doesNotContainKey("goal");
    }
}
