package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDialogueTest {
    private final CustomerJson json=new CustomerJson(new ObjectMapper());
    @Test void workflowModelViewPreservesIdentityAndNeedsWithoutAuditBulk() {
        var state=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(state,List.of(),"first");
        var task=CustomerWorkflow.task(state);
        var requirement=CustomerRequirementService.entry(task,"open","必须离线运行","必须离线运行","HARD","m","必须离线运行","MESSAGE");
        requirement.put("history",List.of(Map.of("text","历史版本".repeat(2000))));
        task.put("requirements",List.of(requirement));
        CustomerTaskService.ask(state,"REQUIREMENT","准备什么岗位？",List.of("Java后端"),"job","r");
        CustomerWorkflow.question(state).put("debugAudit","不可传给模型".repeat(3000));
        String original=json.write(state);
        var input=CustomerDialogue.input("暂停这个任务",state,"",List.of(),"",6000,json);
        var visible=CustomerWorkflow.map(CustomerWorkflow.map(input.get("state")).get("workflowTask"));
        assertEquals(task.get("id"),visible.get("id"));
        assertEquals(CustomerWorkflow.question(state).get("id"),CustomerWorkflow.map(visible.get("question")).get("id"));
        assertEquals(requirement.get("id"),CustomerWorkflow.rows(visible.get("requirements")).getFirst().get("id"));
        assertTrue(json.write(visible).contains("必须离线运行"));
        assertFalse(json.write(visible).contains("debugAudit"));
        assertFalse(json.write(visible).contains("history"));
        assertEquals(original,json.write(state));
        assertTrue(CustomerModel.estimate(CustomerState.PROMPT+json.write(input))<=6000);
    }
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
    @Test void referenceContextIsCountedBeforeHistoryTrimming(){
        var reference=Map.<String,Object>of("references",List.of(Map.of("project",Map.of("name","项目名称".repeat(100)))),"positions",List.of(1));
        var input=CustomerDialogue.input("第二个怎么部署",Map.of(),"",List.of(Map.of("role","user","content","历史".repeat(2000))),"",6000,json,reference);
        assertTrue(input.containsKey("referenceResolution"));assertTrue(CustomerModel.estimate(CustomerState.PROMPT+json.write(input))<=6000);
    }
    @Test void longUserMessageStillFitsWithConfirmedConditionsAndProfile() {
        var input=CustomerDialogue.input("我想了解"+"技术表达".repeat(490),Map.of("language","Python","topic",List.of("AI应用开发"),"goal",List.of("求职"),"maxDurationDays",180),"画像背景".repeat(250),List.of(),"",6000,json);
        assertTrue(CustomerModel.estimate(CustomerState.PROMPT+json.write(input))<=6000);
        assertEquals(180,((Map<?,?>)input.get("state")).get("maxDurationDays"));
    }
}
