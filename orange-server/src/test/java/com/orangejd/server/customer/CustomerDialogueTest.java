package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDialogueTest {
    private final CustomerJson json=new CustomerJson(new ObjectMapper());
    @Test void longHistoryIsBoundedWithoutMutatingSavedConditionsOrHistory() {
        Map<String,Object> state=new LinkedHashMap<>(Map.of("language","Java","allowDocker",false,"provenance",Map.of("large","x".repeat(25000))));
        List<Map<String,Object>> history=new ArrayList<>();
        for(int i=0;i<30;i++) history.add(Map.of("role",i%2==0?"user":"assistant","content","旧消息".repeat(500)));
        var input=CustomerDialogue.input("你好",state,"已确认偏好：Java",history,"历史摘要",6000,json);
        assertEquals(30,history.size());
        assertTrue(((List<?>)input.get("history")).size()<30);
        assertEquals(false,((Map<?,?>)input.get("state")).get("allowDocker"));
        assertFalse(((Map<?,?>)input.get("state")).containsKey("provenance"));
        assertTrue(state.containsKey("provenance"));
    }
    @Test void chatCannotInventEvidenceOrLinks() {
        assertEquals("你好！",CustomerDialogue.reply(Map.of("reply","你好！")));
        assertFalse(CustomerDialogue.reply(Map.of("reply","相关链接 https://invented.example")).contains("https://"));
        assertFalse(CustomerDialogue.reply(Map.of("reply","说明[E1]")).contains("[E1]"));
    }
    @Test void longUserMessageStillFitsWithConfirmedConditionsAndProfile() {
        var input=CustomerDialogue.input("我想了解"+"技术表达".repeat(490),Map.of("language","Python","topic",List.of("AI应用开发"),"goal",List.of("求职"),"maxDurationDays",180),"画像背景".repeat(250),List.of(),"",6000,json);
        assertTrue(CustomerModel.estimate(CustomerState.PROMPT+json.write(input))<=6000);
        assertEquals(180,((Map<?,?>)input.get("state")).get("maxDurationDays"));
    }
}
