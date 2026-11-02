package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.server.mapper.*;
import com.orangejd.server.ragb.retrieval.RagBConstraintMatcher;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerContinuousRepairTest {
    @Test void reportedConversationPreservesUpdatesFindsCrossCategoryProjectsAndDoesNotLoop() {
        var json=new CustomerJson(new ObjectMapper());var store=mock(CustomerStore.class);var db=mock(CustomerMapper.class);
        var model=mock(CustomerModel.class);var tools=mock(CustomerTools.class);var context=mock(CustomerTools.Context.class);org.springframework.test.util.ReflectionTestUtils.setField(context,"run","run");
        var beginner=mock(BeginnerProjectMapper.class);var career=mock(CareerProjectMapper.class);
        var p=new CareerProjectProfile();p.setProjectId(1L);p.setFullName("synthetic/java");p.setHtmlUrl("https://github.com/synthetic/java");p.setPrimaryLanguage("Java");p.setStatus("PUBLISHED");p.setEstimatedDuration("2个月");
        when(career.selectAllPublished()).thenReturn(List.of(p));
        when(career.selectById(1L)).thenReturn(p);
        var projects=new CustomerProjects(beginner,career,new RagBConstraintMatcher());
        var current=new HashMap<String,Object>(Map.of("language","Java","goal","学习","experience","刚学","maxDurationDays",60));
        var outputs=new ArrayList<Map<String,Object>>();
        when(store.session(1,5)).thenAnswer(inv->CustomerWorkflowFixtures.session(json.write(current)));
        when(tools.context(eq("run"),eq(5L),any())).thenReturn(context);when(context.profile()).thenReturn("");
        when(context.projects(anyMap())).thenAnswer(inv->projects.search(inv.getArgument(0)));
        when(context.diagnose(anyMap())).thenAnswer(inv->projects.diagnose(inv.getArgument(0)));
        when(context.projectRecall(anyString())).thenThrow(new IllegalStateException("SYNTHETIC_NO_RAG"));
        when(context.projectEvidence(anyString(),anyList())).thenThrow(new IllegalStateException("SYNTHETIC_NO_RAG"));
        doAnswer(inv->{current.clear();current.putAll(inv.getArgument(2));outputs.add(new LinkedHashMap<>(inv.getArgument(1)));return null;}).when(store).complete(eq("run"),anyMap(),anyMap(),any());
        var conversation=new CustomerConversation(store,db,json,model,tools,projects,new CustomerProperties());
        List<String> messages=List.of("给我推荐项目吧","当前有什么条件","把周期放宽到半年","为什么条件没有放宽","不再限定“刚学”对应的难度","确认","项目难度不限");
        for(String message:messages) {
            when(db.run("run")).thenReturn(Map.of("account_id",5,"session_id",1,"message",message));
            Map<String,Object> changes=message.equals("把周期放宽到半年")?Map.of("maxDurationDays",Map.of("value",Map.of("quantity",6,"unit","MONTH","relation","AT_MOST"),"quote",message)):
                message.contains("难度")?Map.of("difficulty",Map.of("operation","CLEAR","quote",message)):Map.of();
            when(model.call(eq(CustomerState.PROMPT),any(),any(),anyList())).thenReturn(Map.of("intent","RECOMMEND","changes",changes));
            conversation.execute("run",new ArrayList<>(),Instant.now().plusSeconds(90));
            assertThat(current).doesNotContainKey("pendingConditions");
        }
        assertThat(outputs.get(0).get("projects")).asList().hasSize(1);
        assertThat(outputs.get(1).get("answer").toString()).contains("60天","没有项目难度限制");
        assertThat(outputs.get(2).get("answer").toString()).contains("60天调整为180天");
        assertThat(outputs.get(3).get("answer").toString()).contains("180天").doesNotContain("条件未放宽");
        assertThat(current).containsEntry("maxDurationDays",180).containsEntry("experience","刚学");
        verify(model,times(4)).call(eq(CustomerState.PROMPT),any(),any(),anyList());
    }
}
