package com.orangejd.server.customer;

import com.orangejd.common.context.BaseContext;
import com.orangejd.server.controller.admin.AdminCustomerRunsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The web interceptor verifies active ADMIN identity; these tests verify the additional raw-content gate. */
class CustomerAdminDiagnosticsTest {
    final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerGraphRepository repo=mock(CustomerGraphRepository.class);
    final CustomerProperties config=new CustomerProperties();
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final AdminCustomerRunsController controller=new AdminCustomerRunsController(db,json,repo,config);
    @AfterEach void clear(){BaseContext.clear();}
    @Test void rawRequiresIdentityAndExplicitSwitch(){
        var input=new AdminCustomerRunsController.RawAccess("payload","核对证据");
        assertThatThrownBy(()->controller.raw("run",input)).hasMessageContaining("403");
        BaseContext.setCurrentId(5L);
        assertThatThrownBy(()->controller.raw("run",input)).hasMessageContaining("403");
        verifyNoInteractions(repo);
    }
    @Test void reasonAndRunAreValidatedBeforeAudit(){
        BaseContext.setCurrentId(5L);config.setRawAccessEnabled(true);
        when(db.run("run")).thenReturn(null);
        assertThatThrownBy(()->controller.raw("run",new AdminCustomerRunsController.RawAccess("p"," "))).hasMessage("INVALID_RAW_ACCESS_REASON");
        assertThatThrownBy(()->controller.raw("run",new AdminCustomerRunsController.RawAccess("p","查证"))).hasMessage("RUN_NOT_FOUND");
        verifyNoInteractions(repo);
    }
    @Test void auditFailureNeverReturnsContent(){
        BaseContext.setCurrentId(5L);config.setRawAccessEnabled(true);when(db.run("run")).thenReturn(Map.of("id","run"));
        when(repo.raw(5,"run","p","查证")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("audit unavailable"));
        assertThatThrownBy(()->controller.raw("run",new AdminCustomerRunsController.RawAccess("p","查证"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void checkpointViewExcludesFullStateAndRawBody(){
        when(db.run("run")).thenReturn(Map.of("id","run"));
        when(repo.checkpoints("run")).thenReturn(List.of(Map.of("checkpoint_id","c","node_id","understand_turn","state_json",json.write(Map.of("intent","CHAT","history","private history","prompt","private prompt","candidateIdentities",List.of("OPEN:2"))))));
        String response=json.write(controller.checkpoints("run").getData());
        assertThat(response).contains("CHAT","OPEN:2").doesNotContain("private history","private prompt","state_json");
        verify(repo,never()).raw(anyLong(),anyString(),anyString(),anyString());
    }
    @Test void rawPassesRunScopedPayloadAndTrimmedReasonToAuditedRepository(){
        BaseContext.setCurrentId(5L);config.setRawAccessEnabled(true);when(db.run("run")).thenReturn(Map.of("id","run"));
        when(repo.raw(5,"run","p","核对引用")).thenReturn(Map.of("content_json","{}"));
        assertThat(controller.raw("run",new AdminCustomerRunsController.RawAccess("p","  核对引用  ")).getData()).isEqualTo(Map.of("content_json","{}"));
        verify(repo).raw(5,"run","p","核对引用");
    }
}
