package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.orangejd.server.customer.CustomerWorkflow.*;

/** Executes production services and the real Turn router. Expected fixture snapshots are never replayed. */
class CustomerWorkflowV2Test {
    CustomerJson json=new CustomerJson(new ObjectMapper());
    Map<String,Object> state;
    @BeforeEach void initialize(){state=new LinkedHashMap<>();CustomerWorkflow.initialize(state,List.of(),"first");}
    Map<String,Object> change(String text){return new LinkedHashMap<>(Map.of("intent","COLLECT","changes",Map.of(),"requirementProposals",List.of(Map.of("operation","ADD","text",text,"quote",text,"strength","HARD","kind","FEATURE"))));}
    void apply(Map<String,Object> action,String raw){var before=json.read(json.write(state));CustomerRequirementService.apply(state,before,action,raw,"message-1","run-1");}
    void control(String operation,String raw){var a=new LinkedHashMap<String,Object>(Map.of("intent","CHAT","taskAction",Map.of("operation",operation,"quote",raw)));CustomerTaskService.prepare(state,a,raw,"control");CustomerTaskService.apply(state,a,raw,"m","control",CustomerReferenceResolver.resolve(raw,state,Map.of()));}
    @Test void arbitraryNeedsHaveStableIdentitySourceAndTombstone(){
        apply(change("必须离线运行"),"必须离线运行");var r=requirements(state).getFirst();String id=r.get("id").toString();
        assertThat(r).containsEntry("strength","HARD").containsEntry("status","ACTIVE");assertThat(map(r.get("source"))).containsEntry("messageId","message-1");
        apply(new LinkedHashMap<>(Map.of("intent","COLLECT","requirementProposals",List.of(Map.of("operation","REMOVE","id",id,"text","取消离线要求","quote","取消离线要求")))),"取消离线要求");
        assertThat(requirements(state)).hasSize(1);assertThat(r).containsEntry("status","REMOVED").containsEntry("revision",2L);assertThat(number(task(state),"requirementVersion")).isEqualTo(2);
        assertThat(CustomerRequirementService.query(state)).doesNotContain("离线");
    }
    @Test void queryOrUngroundedEvidenceCannotCreateNeeds(){
        var q=change("必须离线运行");q.put("intent","DETAIL");apply(q,"必须离线运行吗");assertThat(requirements(state)).isEmpty();
        apply(change("必须离线运行"),"支持离线吗");assertThat(requirements(state)).isEmpty();
    }
    @Test void inferredNeedsRemainPending(){var a=change("我会Java");var proposals=new ArrayList<>(rows(a.get("requirementProposals")));var p=new LinkedHashMap<>(proposals.getFirst());p.put("assertion","INFERRED");a.put("requirementProposals",List.of(p));apply(a,"我会Java");assertThat(active(state)).isEmpty();assertThat(requirements(state).getFirst()).containsEntry("status","PENDING");}
    @Test void repeatingIdenticalNeedDoesNotAdvanceRevision(){apply(change("必须离线运行"),"必须离线运行");apply(change("必须离线运行"),"必须离线运行");assertThat(number(task(state),"requirementVersion")).isEqualTo(1);}
    @Test void migrationOnlyUsesActualUserMessageAndRequiresConfirmation(){
        state=new LinkedHashMap<>(Map.of("language","Java","allowDocker",false,"provenance",Map.of("language",Map.of("source","MESSAGE","quote","Java"),"allowDocker",Map.of("source","MESSAGE","quote","不用Docker")),"lastCandidates",List.of(Map.of("id",9)),"lastQuestion",Map.of("text","旧问题")));
        CustomerWorkflow.initialize(state,List.of(Map.of("id",7,"role","user","content","只要Java项目")),"migration");
        assertThat(state).doesNotContainKeys("language","allowDocker","lastCandidates","lastQuestion");assertThat(requirements(state)).hasSize(1);assertThat(requirements(state).getFirst()).containsEntry("status","PENDING");
        CustomerTaskService.ask(state,"MIGRATION","是否沿用？",List.of("确认沿用"),"migration","migration");
        var a=new LinkedHashMap<String,Object>();CustomerTaskService.apply(state,a,"确认沿用","m8","next",CustomerReferenceResolver.resolve("确认沿用",state,Map.of()));
        assertThat(state).containsEntry("language","Java");assertThat(active(state)).hasSize(1);
        assertThat(question(state)).isEmpty();assertThat(task(state)).containsEntry("status","DISCOVERING");
    }
    @Test void insertionDoesNotAnswerQuestionAndSkipDoesNotInventValue(){
        CustomerTaskService.ask(state,"REQUIREMENT","主要用来做什么？",List.of("求职","自用"),"goal","r1");String q=question(state).get("id").toString();
        var a=new LinkedHashMap<String,Object>(Map.of("intent","QUESTION"));CustomerTaskService.apply(state,a,"Redis是什么","m2","r2",CustomerReferenceResolver.resolve("Redis是什么",state,Map.of()));
        assertThat(question(state)).containsEntry("id",q).containsEntry("status","OPEN");control("SKIP_QUESTION","不知道");
        assertThat(question(state)).isEmpty();assertThat(rows(task(state).get("questions")).getFirst()).containsEntry("status","SKIPPED");assertThat(active(state)).isEmpty();
    }
    @Test void staleQuestionAnswerCannotCloseNewQuestion(){
        CustomerTaskService.ask(state,"REQUIREMENT","旧问题",List.of(),"old","r");var old=question(state).get("id");
        CustomerTaskService.ask(state,"REQUIREMENT","新问题",List.of(),"new","r2");
        var a=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","questionAnswer",Map.of("questionId",old,"quote","两周")));
        CustomerTaskService.apply(state,a,"两周","m","r3",CustomerReferenceResolver.resolve("两周",state,Map.of()));assertThat(question(state)).containsEntry("text","新问题");
    }
    @Test void unaffectedQuestionCanBeAnsweredAfterAnotherNeedChanged(){
        CustomerTaskService.ask(state,"REQUIREMENT","想用于什么场景？",List.of(),"goal","r");var id=question(state).get("id");apply(change("必须离线运行"),"必须离线运行");
        var a=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","questionAnswer",Map.of("questionId",id,"quote","小店库存")));
        CustomerTaskService.apply(state,a,"小店库存","m","r2",CustomerReferenceResolver.resolve("小店库存",state,Map.of()));assertThat(question(state)).isEmpty();
    }
    @Test void changingAQuestionDependencySupersedesItsOldIdentity(){
        apply(change("必须离线运行"),"必须离线运行");var r=requirements(state).getFirst();CustomerTaskService.ask(state,"TRADEOFF","接受待核实的离线候选吗？",List.of(),"tradeoff:1:"+r.get("id"),"r");String old=question(state).get("id").toString();
        var a=new LinkedHashMap<String,Object>(Map.of("intent","CHAT","requirementProposals",List.of(Map.of("operation","REMOVE","id",r.get("id"),"text","取消离线要求","quote","取消离线要求"))));
        apply(a,"取消离线要求");CustomerTaskService.apply(state,a,"取消离线要求","m","next",CustomerReferenceResolver.resolve("取消离线要求",state,Map.of()));
        assertThat(question(state)).isEmpty();assertThat(rows(task(state).get("questions")).stream().filter(q->old.equals(q.get("id"))).findFirst().orElseThrow()).containsEntry("status","SUPERSEDED");
    }
    @Test void pauseAndSwitchPreserveOriginalTaskWithoutLeakingNeeds(){
        apply(change("必须离线运行"),"必须离线运行");var original=task(state).get("id");control("PAUSE_TASK","先暂停任务");assertThat(task(state)).containsEntry("status","PAUSED");
        control("SWITCH_TASK","换个目标，帮我找Java求职项目");assertThat(task(state).get("id")).isNotEqualTo(original);assertThat(active(state)).isEmpty();assertThat(rows(workflow(state).get("archivedTasks")).getFirst()).containsEntry("id",original).containsEntry("status","PAUSED");
    }
    @Test void resumeMayExplicitlyNameTheCurrentPausedTaskButNeverAnotherIdentity(){
        apply(change("必须离线运行"),"必须离线运行");var id=task(state).get("id");control("PAUSE_TASK","暂停这个任务");
        var wrong=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","taskAction",Map.of("operation","RESUME_TASK","quote","继续这个任务","taskId","foreign-task")));
        CustomerTaskService.prepare(state,wrong,"继续这个任务","wrong");assertThat(task(state)).containsEntry("status","PAUSED");
        var right=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","taskAction",Map.of("operation","RESUME_TASK","quote","继续这个任务","taskId",id)));
        CustomerTaskService.prepare(state,right,"继续这个任务","resume");
        assertThat(task(state)).containsEntry("id",id).containsEntry("status","DISCOVERING");assertThat(workflow(state)).containsEntry("activeTaskId",id);
        assertThat(active(state)).hasSize(1);assertThat(right).doesNotContainKey("workflowReply");
    }
    @Test void ambiguousReferenceStopsRatherThanUsingModelId(){assertThat(CustomerReferenceResolver.resolve("第二个支持扫码吗",state,Map.of()).ambiguous()).isTrue();}
    @Test void explanationOrNegationCannotCancelTask(){control("CANCEL_TASK","怎么取消任务？");assertThat(task(state).get("status")).isEqualTo("DISCOVERING");control("CANCEL_TASK","不要取消任务");assertThat(task(state).get("status")).isEqualTo("DISCOVERING");}
    @Test void shortAcknowledgementRequiresAnOpenQuestionSource(){var a=change("是的");apply(a,"是的");assertThat(requirements(state)).isEmpty();CustomerTaskService.ask(state,"REQUIREMENT","必须离线运行吗？",List.of(),"offline","r");a.put("questionAnswer",Map.of("questionId",question(state).get("id"),"quote","是的"));apply(a,"是的");assertThat(requirements(state)).hasSize(1);assertThat(map(requirements(state).getFirst().get("source"))).containsKey("questionId");}
    void list(String listId,String first,String second){var projects=List.of(Map.of("id",1,"type","EXTERNAL_PROJECT","name",first),Map.of("id",2,"type","EXTERNAL_PROJECT","name",second));var lists=new ArrayList<>(rows(task(state).get("lists")));lists.add(Map.of("id",listId,"projects",projects,"runId","display"));task(state).put("lists",lists);task(state).put("currentListId",listId);}
    @Test void boundHistoricalListIsNotReordered(){list("old","A","B");list("new","C","D");var resolution=CustomerReferenceResolver.resolve("第二个支持扫码吗",state,Map.of("listId","old"));assertThat(resolution.resolvedQuery()).contains("B").doesNotContain("D");assertThat(map(resolution.references().getFirst().get("project"))).containsEntry("name","B");assertThat(requirements(state)).isEmpty();}
    @Test void historicalOrdinalWithoutListIsAmbiguous(){list("old","A","B");list("new","C","D");assertThat(CustomerReferenceResolver.resolve("上次第二个项目",state,Map.of()).ambiguous()).isTrue();}
    @Test void theseTwoMeansExactlyTheTwoMembersOfTheCurrentList(){list("one","A","B");assertThat(CustomerReferenceResolver.resolve("比较这两个的改造工作量",state,Map.of()).positions()).containsExactly(1,2);}
    @Test void selectionCompletesButDoesNotImport(){list("one","A","B");control("SELECT_PROJECT","就选第二个项目");assertThat(task(state)).containsEntry("status","COMPLETED");assertThat(map(task(state).get("selectedProject"))).containsEntry("name","B");}
    @Test void invalidSelectionAfterRequirementChangeDoesNotComplete(){list("one","A","B");apply(change("必须离线运行"),"必须离线运行");control("SELECT_PROJECT","就选第二个项目");assertThat(task(state).get("status")).isNotEqualTo("COMPLETED");}
    @Test void noMatchFinishesTheRunWithoutLeavingTaskExecuting(){task(state).put("status","EVALUATING");var result=new LinkedHashMap<String,Object>(Map.of("status","NO_MATCH","projects",List.of()));CustomerTaskService.finish(state,result,"RECOMMEND","r");assertThat(task(state)).containsEntry("status","READY_TO_SEARCH").containsEntry("lastOutcome","NO_MATCH");}
    @Test void structuredUpdatesCannotBypassTheExistingFieldValidator(){var r=CustomerRequirementService.entry(task(state),"language","只要Java","Java","HARD","m","只要Java","MESSAGE");task(state).put("requirements",new ArrayList<>(List.of(r)));state.put("language","Java");var action=new LinkedHashMap<String,Object>(Map.of("intent","COLLECT","requirementProposals",List.of(Map.of("operation","UPDATE","id",r.get("id"),"quote","改成香蕉语言","text","香蕉","normalizedValue","香蕉"))));apply(action,"改成香蕉语言");assertThat(state).containsEntry("language","Java").containsKey("pendingConditions");assertThat(r).containsEntry("normalizedValue","Java");}

    @Test void actualMultiTurnRouterPreservesQuestionAcrossKnowledgeInterruptionThenSearches(){
        var store=mock(CustomerStore.class);var db=mock(CustomerMapper.class);var model=mock(CustomerModel.class);var tools=mock(CustomerTools.class);var context=mock(CustomerTools.Context.class);var projects=mock(CustomerProjects.class);
        var conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(context,"run","run");
        when(tools.context(anyString(),anyLong(),any())).thenReturn(context);when(context.profile()).thenReturn("");
        when(db.messages(anyLong(),anyLong(),anyInt())).thenReturn(List.of());when(db.steps(anyString())).thenReturn(List.of());
        when(store.session(1,5)).thenAnswer(i->Map.of("state_json",json.write(state)));
        when(db.run("r1")).thenReturn(Map.of("account_id",5,"session_id",1,"message","我想做求职项目"));
        when(model.call(eq(CustomerState.PROMPT),any(),any(),anyList())).thenReturn(new LinkedHashMap<>(Map.of("intent","COLLECT","changes",Map.of(),"clarification",Map.of("question","准备什么岗位？","options",List.of("Java后端","前端")))));
        var first=conversation.prepare(new CustomerRunContext("r1",Instant.now().plusSeconds(90),new ArrayList<>()));state=json.read(json.write(first.state()));
        assertThat(first.result()).containsEntry("status","FOLLOW_UP");String q=question(state).get("id").toString();
        when(db.run("r2")).thenReturn(Map.of("account_id",5,"session_id",1,"message","Redis是什么"));
        when(model.call(eq(CustomerState.PROMPT),any(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","Redis是内存数据存储。","changes",Map.of()));
        var second=conversation.prepare(new CustomerRunContext("r2",Instant.now().plusSeconds(90),new ArrayList<>()));state=json.read(json.write(second.state()));
        assertThat(question(state)).containsEntry("id",q);verify(context,never()).projects(anyMap());
        when(db.run("r3")).thenReturn(Map.of("account_id",5,"session_id",1,"message","Java后端，只要Java项目"));
        when(model.call(eq(CustomerState.PROMPT),any(),any(),anyList())).thenReturn(Map.of("intent","COLLECT","changes",Map.of("language",Map.of("value","Java","quote","只要Java项目")),"questionAnswer",Map.of("questionId",q,"quote","Java后端")));
        when(context.projectRecall(anyString())).thenReturn(new com.orangejd.server.ragb.service.RagBSearchService.SearchResult("g",0,0,Map.of(),List.of()));when(context.projects(anyMap())).thenReturn(List.of());when(context.diagnose(anyMap())).thenReturn(Map.of());
        var third=conversation.prepare(new CustomerRunContext("r3",Instant.now().plusSeconds(90),new ArrayList<>()));state=json.read(json.write(third.state()));
        assertThat(question(state)).isEmpty();assertThat(state).containsEntry("language","Java");verify(context).projectRecall(contains("Java"));
        var input=org.mockito.ArgumentCaptor.forClass(Map.class);verify(model,times(3)).call(eq(CustomerState.PROMPT),input.capture(),any(),anyList());assertThat(input.getAllValues()).allMatch(m->m.containsKey("referenceResolution"));
    }
}
