package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerGoalCoverageTest {
    @ParameterizedTest @ValueSource(strings={"自用书店图书管理","自用：给自己的书店管理图书","自用/书店经营"})
    void realProviderParaphrasesRetainUserOriginalInsteadOfBeingSilentlyDropped(String value) {
        String message="这个作品是给我自己的书店管理图书用的，先记住用途。";
        var action=new HashMap<String,Object>(Map.of("intent","CHAT","changes",Map.of("goal",Map.of("operation","SET","value",value,"quote","给我自己的书店管理图书用的"))));
        assertThat(CustomerGoalCoverage.complete(action,message)).isEqualTo("给我自己的书店管理图书用的");
        var rejected=new ArrayList<Map<String,Object>>();
        var state=CustomerState.reconcile(Map.of(),action,message,"",rejected);
        assertThat(state.get("goal")).isEqualTo("给我自己的书店管理图书用的");
        assertThat(rejected).isEmpty();
    }
    @Test void ungroundedModelEvidenceAndValidPresetAreNeverOverwritten() {
        String message="给我自己的书店管理图书用的，先记住用途。";
        for(var proposal:List.of(Map.of("operation","SET","value","学习","quote",message),Map.of("operation","SET","value","自用书店","quote","他人商店"),Map.of("operation","ADD","value","自用书店","quote",message))) {
            var action=new HashMap<String,Object>(Map.of("intent","CHAT","changes",Map.of("goal",proposal)));
            assertThat(CustomerGoalCoverage.complete(action,message)).isEmpty();
            assertThat(((Map<?,?>)action.get("changes")).get("goal")).isSameAs(proposal);
        }
    }
    @Test void preservesActualUseWithoutChangingChatOrExplicitTopic() {
        var action=new HashMap<String,Object>(Map.of("intent","CHAT","changes",Map.of("topic",Map.of("operation","SET","value",List.of("图书管理"),"quote","管理图书"))));
        String message="这个作品是给我自己的书店管理图书用的，先记住用途。";
        assertThat(CustomerGoalCoverage.complete(action,message)).isEqualTo("给我自己的书店管理图书用的");
        var state=CustomerState.reconcile(Map.of(),action,message,"",new ArrayList<>());
        assertThat(state.get("goal").toString()).contains("书店");
        assertThat(state.get("topic")).isEqualTo(List.of("图书管理"));
        assertThat(action.get("intent")).isEqualTo("CHAT");
    }
    @Test void explicitRemovalRemainsAuthoritative() {
        var removal=Map.of("operation","CLEAR","quote","用途不限");
        var action=new HashMap<String,Object>(Map.of("intent","COLLECT","changes",Map.of("goal",removal)));
        assertThat(CustomerGoalCoverage.complete(action,"给我自己的书店用的，记住用途，但现在用途不限")).isEmpty();
        assertThat(((Map<?,?>)action.get("changes")).get("goal")).isSameAs(removal);
    }
    @ParameterizedTest @ValueSource(strings={
        "不是给我自己的书店用的，先记住用途。",
        "例如给我自己的书店用的，先记住用途。",
        "他说给我自己的书店用的，先记住用途。",
        "“给我自己的书店用的”，先记住用途。",
        "给我自己的书店用的。",
        "给我自己的书店用的，也给我们的课堂用的，先记住用途。"})
    void ambiguousOrUnrequestedUseIsNotForced(String message) {
        var action=new HashMap<String,Object>(Map.of("intent","CHAT","changes",Map.of()));
        assertThat(CustomerGoalCoverage.complete(action,message)).isEmpty();
        assertThat((Map<?,?>)action.get("changes")).isEmpty();
    }
}
