package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerStoreTest {
    final CustomerMapper db=mock(CustomerMapper.class);
    final CustomerProperties props=new CustomerProperties();
    final CustomerStore store=new CustomerStore(db,new CustomerJson(new ObjectMapper()),props);
    CustomerStoreTest() { props.setEnabled(true); props.setAllowedAccounts(Set.of(7L,8L)); when(db.session(1,7)).thenReturn(Map.of("id",1L,"account_id",7L)); }
    @Test void disabledAndRestrictedEmptyWhitelistDenyAccess() {
        props.setEnabled(false); assertThatThrownBy(()->store.access(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        props.setEnabled(true); props.setAllAuthenticatedUsers(false); props.setAllowedAccounts(Set.of()); assertThatThrownBy(()->store.access(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void publicAccessAllowsAccountsOutsideWhitelistButRequiresLogin() {
        store.access(999L);
        props.setAllowedAccounts(Set.of()); store.access(999L);
        for(Long owner:new Long[]{null,0L,-1L}) assertThatThrownBy(()->store.access(owner)).hasMessageContaining("401");
        when(db.session(1,999)).thenReturn(Map.of("id",1L,"account_id",7L));
        when(db.run("r")).thenReturn(Map.of("account_id",7L));
        assertThatThrownBy(()->store.session(1,999)).hasMessageContaining("404");
        assertThatThrownBy(()->store.run("r",999)).hasMessageContaining("404");
        props.setAllAuthenticatedUsers(false);
        assertThatThrownBy(()->store.access(999L)).hasMessageContaining("403");
    }
    @Test void otherOwnerCannotReadSessionOrRun() {
        assertThatThrownBy(()->store.session(1,8)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(db.run("r")).thenReturn(Map.of("account_id",7L)); assertThatThrownBy(()->store.run("r",8)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void replayReturnsOriginalWithoutSecondMessageOrCall() {
        when(db.replay(1,"request123")).thenReturn(Map.of("id","original","input_hash",CustomerStore.hash("Java")));
        assertThat(store.submit(1,7,"request123","Java")).containsEntry("id","original").containsEntry("replayed",true);
        verify(db,never()).insertRun(any()); verify(db,never()).message(anyLong(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void differentMessageWithSameRequestIsConflict() {
        when(db.replay(1,"request123")).thenReturn(Map.of("input_hash",CustomerStore.hash("Java")));
        assertThatThrownBy(()->store.submit(1,7,"request123","Python")).hasMessageContaining("409");
    }
    @Test void ownerAndGlobalConcurrencyLimitsApply() {
        when(db.activeOwner(7)).thenReturn(1); assertThatThrownBy(()->store.submit(1,7,"request123","Java")).hasMessageContaining("409");
        when(db.activeOwner(7)).thenReturn(0); when(db.active()).thenReturn(4); assertThatThrownBy(()->store.submit(1,7,"request123","Java")).hasMessageContaining("409");
    }
    @Test void jsonIdsAcceptIntegerStringsButRejectInvalidNumericValues() {
        assertThat(CustomerStore.positiveId("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        assertThat(CustomerStore.positiveId(7L)).isEqualTo(7L);
        for(Object invalid:List.of("9223372036854775808","0","-1","1.5"," 7","01",1.5d,0L,-1L)) {
            assertThatThrownBy(()->CustomerStore.positiveId(invalid)).hasMessage("INVALID_ID");
        }
        assertThatThrownBy(()->CustomerStore.positiveId(null)).hasMessage("INVALID_ID");
    }
    @Test void cancelledRunCannotPublishLateAnswerOrChangeState() {
        when(db.run("r")).thenReturn(Map.of("status","CANCELLED","session_id",1)); store.complete("r",Map.of(),Map.of(),Map.of());
        verify(db,never()).state(anyLong(),anyString()); verify(db,never()).finish(anyString(),anyString(),any(),any(),any());
    }
    @Test void sessionDeletionChecksOwnerAndActiveRun() {
        assertThatThrownBy(()->store.deleteSession(1,8)).hasMessageContaining("404");
        when(db.sessionIncludingDeleted(1,7)).thenReturn(Map.of("id",1L,"account_id",7L));
        when(db.activeSession(1)).thenReturn(Map.of("id","r","status","RUNNING"));
        assertThatThrownBy(()->store.deleteSession(1,7)).hasMessageContaining("409");
        verify(db,never()).deleteSession(anyLong(),anyLong());
    }
    @Test void deletionIsIdempotentAndDeletedHistoryAndRunsCannotBeRead() {
        when(db.sessionIncludingDeleted(1,7)).thenReturn(Map.of("id",1L,"account_id",7L));
        store.deleteSession(1,7);
        verify(db).deleteSession(1,7);
        when(db.sessionIncludingDeleted(1,7)).thenReturn(Map.of("id",1L,"account_id",7L,"deleted_at","deleted"));
        store.deleteSession(1,7); verify(db,times(1)).deleteSession(1,7);
        when(db.session(1,7)).thenReturn(null);
        when(db.run("r")).thenReturn(Map.of("account_id",7L,"session_id",1L));
        assertThatThrownBy(()->store.history(1,7,Long.MAX_VALUE,50)).hasMessageContaining("404");
        assertThatThrownBy(()->store.run("r",7)).hasMessageContaining("404");
        assertThatThrownBy(()->store.submit(1,7,"request123","hello")).hasMessageContaining("404");
        verify(db,never()).insertRun(any());
    }

}
