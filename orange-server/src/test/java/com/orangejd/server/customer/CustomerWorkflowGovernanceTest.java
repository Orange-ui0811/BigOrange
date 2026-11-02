package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.AiAcceptanceBudget;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerWorkflowGovernanceTest {
    @TempDir Path temp;
    @Test void budgetPersistsAcrossCallsAndRejectsAttemptBeforeNetwork() throws Exception {
        Path ledger=temp.resolve("calls.jsonl");System.setProperty("orange.acceptance.ledger",ledger.toString());System.setProperty("orange.acceptance.limit","3");
        try {for(int i=0;i<3;i++)AiAcceptanceBudget.beforeRequest("TEST");assertThatThrownBy(()->AiAcceptanceBudget.beforeRequest("TEST")).hasMessage("ACCEPTANCE_BUDGET_EXHAUSTED");assertThat(Files.readAllLines(ledger)).hasSize(3);}
        finally {System.clearProperty("orange.acceptance.ledger");System.clearProperty("orange.acceptance.limit");}
    }
    @Test void staleStateCannotFinishRun(){
        var db=mock(CustomerMapper.class);var config=new CustomerProperties();var store=new CustomerStore(db,new CustomerJson(new ObjectMapper()),config);
        when(db.run("run")).thenReturn(Map.of("status","RUNNING","session_id",7));when(db.stateCas(eq(7L),anyString(),eq(4L))).thenReturn(0);
        assertThatThrownBy(()->store.complete("run",Map.of(),new HashMap<>(Map.of("_expectedStateVersion",4L)),Map.of())).hasMessage("STALE_SESSION_REVISION");
        verify(db,never()).finish(anyString(),anyString(),any(),any(),any());verify(db,never()).message(anyLong(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void checkpointAdvancesExpectedVersionButDoesNotPersistInternalKey(){
        var db=mock(CustomerMapper.class);var store=new CustomerStore(db,new CustomerJson(new ObjectMapper()),new CustomerProperties());
        when(db.run("run")).thenReturn(Map.of("status","RUNNING","session_id",7));when(db.stateCas(eq(7L),anyString(),eq(4L))).thenReturn(1);
        var state=new HashMap<String,Object>(Map.of("_expectedStateVersion",4L,"goal","求职"));store.checkpoint("run",state);
        assertThat(state).containsEntry("_expectedStateVersion",5L);verify(db).stateCas(7L,"{\"goal\":\"求职\"}",4L);
    }
    @Test void staleQuestionBindingRejectedAndReplayIsCheckedBeforeCurrentBinding(){
        var db=mock(CustomerMapper.class);var json=new CustomerJson(new ObjectMapper());var config=new CustomerProperties();config.setEnabled(true);config.setAllowedAccounts(Set.of(8L));
        var store=new CustomerStore(db,json,config);var state=new LinkedHashMap<String,Object>();CustomerWorkflow.initialize(state,List.of(),"r");CustomerTaskService.ask(state,"REQUIREMENT","用途？",List.of(),"goal","r");
        when(db.session(7,8)).thenReturn(Map.of("account_id",8,"state_json",json.write(state)));
        assertThatThrownBy(()->store.submit(7,8,"request123","求职",Map.of("questionId","wrong"))).hasMessageContaining("409");verify(db,never()).insertRun(anyMap());
        var context=Map.<String,Object>of("questionId","old");String hash=CustomerStore.hash("求职\n"+json.write(new TreeMap<>(context)));when(db.replay(7,"request123")).thenReturn(Map.of("input_hash",hash,"id","existing"));
        assertThat(store.submit(7,8,"request123","求职",context)).containsEntry("replayed",true);
    }
    @Test void cancelledRunCannotCommitLateWorkflow(){
        var db=mock(CustomerMapper.class);var store=new CustomerStore(db,new CustomerJson(new ObjectMapper()),new CustomerProperties());when(db.run("r")).thenReturn(Map.of("status","CANCELLED"));
        store.complete("r",Map.of(),new HashMap<>(Map.of("_expectedStateVersion",4)),Map.of());verify(db,never()).stateCas(anyLong(),anyString(),anyLong());
    }
}
