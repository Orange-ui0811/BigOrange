package com.orangejd.server.customer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerGoalsTest {
    Map<String,Object> action(String operation,Object value,String quote) {
        return Map.of("intent","COLLECT","changes",Map.of("goal",Map.of("operation",operation,"value",value,"quote",quote)));
    }
    @Test void multipleGoalsAndVerbatimCustomGoalSurviveAddRemoveAndDefaults() {
        var state=CustomerState.update(Map.of("language","Java","maxDurationDays",60),action("SET",List.of("学习","求职"),"我想学习也想求职"),"我想学习也想求职","");
        state=CustomerState.update(state,action("ADD","给家里的店做库存管理","另外给家里的店做库存管理"),"另外给家里的店做库存管理","");
        state=CustomerState.update(state,action("REMOVE","求职","不再考虑找工作"),"不再考虑找工作","");
        assertThat(CustomerGoals.values(state.get("goal"))).containsExactly("学习","给家里的店做库存管理");
        assertThat(state).containsEntry("language","Java").containsEntry("maxDurationDays",60);
        assertThat(CustomerState.update(state,Map.of("intent","COLLECT","profileDefaults",Map.of("goal",Map.of("value","求职","quote","求职"))),"你好","求职")).containsEntry("goal",state.get("goal"));
    }
    @Test void removalOfLastGoalCannotBeUndoneByProfileDefaults() {
        var state=CustomerState.update(Map.of("goal","求职"),action("REMOVE","求职","找工作这件事先不考虑"),"找工作这件事先不考虑","");
        assertThat(state).doesNotContainKey("goal");
        assertThat(CustomerState.update(state,Map.of("intent","COLLECT","profileDefaults",Map.of("goal",Map.of("value","求职","quote","求职"))),"你好","求职")).doesNotContainKey("goal");
    }
    @Test void semanticPresetDoesNotRequireLiteralKeywordButQuotesStillMustExist() {
        assertThat(CustomerState.update(Map.of(),action("SET","赚钱/接单/创业","挣点外快"),"想挣点外快","")).containsEntry("goal","赚钱/接单/创业");
        assertThatThrownBy(()->CustomerState.update(Map.of(),action("SET","学习","不存在的原话"),"想挣点外快","")).hasMessage("UNSUPPORTED_CONDITION");
        assertThatThrownBy(()->CustomerState.update(Map.of(),action("SET","外包管理工具","挣点外快"),"想挣点外快","")).hasMessage("UNSUPPORTED_GOAL");
    }
    @Test void badArraysAreRejectedWithoutDiscardingOtherValidConditions() {
        var rejected=new ArrayList<Map<String,Object>>();
        var state=CustomerState.reconcile(Map.of("goal","学习"),Map.of("intent","COLLECT","changes",Map.of("goal",Map.of("value",List.of(1),"quote","求职"),"language",Map.of("value","Java","quote","Java"))),"求职Java","",rejected);
        assertThat(state).containsEntry("goal","学习").containsEntry("language","Java");
        assertThat(rejected).hasSize(1);
    }
    @Test void escapedParagraphsAreFixedButCodeAndPathsStayLiteral() {
        assertThat(CustomerText.paragraphs("你好\\n\\n继续聊")).isEqualTo("你好\n\n继续聊");
        assertThat(CustomerText.paragraphs("`\\n\\n`\n```java\nString s=\"\\n\\n\";\n``` C:\\new")).contains("`\\n\\n`","\"\\n\\n\"","C:\\new");
    }
    @Test void availableGoalsAreServerFactsAndNotAnOldTwoOptionModelReply() {
        assertThat(CustomerConditionFacts.route("目前支持哪些目标，能多选和自定义吗？")).isEqualTo("STATE");
        assertThat(CustomerConditionFacts.describe(Map.of("goal","学习"))).contains("赚钱/接单/创业","探索技术兴趣","自己的目标");
        assertThat(CustomerConditionFacts.route("我想同时选择学习和求职目标")).isNull();
    }
}
