package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerHistoryRecoveryTest {
    final CustomerContextRepository repository=mock(CustomerContextRepository.class);
    final CustomerJson json=new CustomerJson(new ObjectMapper());
    final CustomerHistoryRecovery recovery=new CustomerHistoryRecovery(repository,json);
    final CustomerHistoryRecovery.Scope scope=new CustomerHistoryRecovery.Scope(1,2,"current","task-a",3,100);
    Map<String,Object> row(long id,String task,String content){var out=new LinkedHashMap<String,Object>();out.put("id",id);out.put("run_id","run-"+id);out.put("role","user");out.put("task_id",task);out.put("content",content);return out;}
    @Test void callbackSchemasHideIdentityAndRejectInjectedArguments(){
        var session=recovery.open(scope);var tools=session.callbacks();
        for(var tool:tools){var schema=json.read(tool.getToolDefinition().inputSchema());assertThat(((Map<?,?>)schema.get("properties")).containsKey("owner")).isFalse();assertThat(schema).containsEntry("additionalProperties",false);}
        assertThatThrownBy(()->tools[0].call("{\"query\":\"Java\",\"owner\":9}")).hasMessage("INVALID_CONTEXT_ARGUMENTS");
        verify(repository,never()).search(any(),anyList(),anyBoolean());
    }
    @Test void readsUseExactSourceHashAndHaveOneSharedTwoOperationLimit(){
        var original=row(1,"task-a","第一段离线要求。第二段后来改成联网。" );when(repository.source(scope,1)).thenReturn(original);
        var session=recovery.open(scope);var first=session.read(1,0,6,"");
        assertThat(first).containsEntry("partial",true).containsEntry("contentHash",CustomerStore.hash(original.get("content").toString())).containsEntry("historicalOnly",false);
        assertThatThrownBy(()->session.read(1,0,100,"invalid")).hasMessage("CONTEXT_SOURCE_HASH_CHANGED");
        assertThatThrownBy(()->session.read(1,0,100,"")).hasMessage("CONTEXT_RECOVERY_LIMIT");assertThat(session.calls()).isEqualTo(2);
    }
    @Test void unknownAndOtherTaskAreHistoricalOnlyNeverCurrentTask(){
        assertThat(CustomerHistoryRecovery.page(row(1,null,"旧消息"),0,100,scope)).containsEntry("historicalOnly",true).containsEntry("attribution","UNASSIGNED_LEGACY");
        assertThat(CustomerHistoryRecovery.page(row(2,"task-b","另一任务"),0,100,scope)).containsEntry("historicalOnly",true).containsEntry("attribution","OTHER_TASK");
    }
    @Test void searchIncludesLaterCorrectionAndFiltersFutureEvenIfRepositoryMisbehaves(){
        when(repository.search(eq(scope),anyList(),eq(false))).thenReturn(List.of(row(5,"task-a","Java项目必须离线。"),row(101,"task-a","Java未来内容")));
        when(repository.corrections(scope,5,false)).thenReturn(List.of(row(50,"task-a","更正：允许联网。")));
        var result=recovery.open(scope).search("Java",false);
        assertThat(result).extracting(r->r.get("messageId")).containsExactly(5L,50L);
        assertThat(result.getLast()).containsEntry("selection","POSSIBLE_LATER_CORRECTION");
        assertThat(CustomerModel.estimate(json.write(result))).isLessThanOrEqualTo(2200);
    }
    @Test void pagingIsLosslessAndDoesNotSplitSurrogatePairs(){
        var original=row(1,"task-a","甲😀乙😀丙");var first=CustomerHistoryRecovery.page(original,0,3,scope);var second=CustomerHistoryRecovery.page(original,3,4,scope);
        assertThat(first.get("text").toString()+second.get("text")).isEqualTo(original.get("content"));
        assertThatThrownBy(()->CustomerHistoryRecovery.page(original,1,1,scope)).hasMessage("CONTEXT_PAGE_TOO_SMALL");
    }
    @Test void mapperQueriesParseAndKeepOwnershipWatermarkAndBoundedParameters(){
        var config=new org.apache.ibatis.session.Configuration();config.addMapper(CustomerContextMapper.class);
        for(String method:List.of("source","search","recent","completed","sources")) {
            var params=new HashMap<String,Object>();params.put("scope",scope);params.put("message",1L);params.put("terms",List.of("Java"));params.put("otherTasks",false);params.put("limit",80);params.put("after",0L);params.put("ids",List.of(1L,2L));
            String sql=config.getMappedStatement(CustomerContextMapper.class.getName()+"."+method).getBoundSql(params).getSql();
            assertThat(sql).contains("s.account_id=", "s.deleted_at IS NULL","m.id <=").doesNotContain("&lt;","${");
        }
    }
    @Test void repositoryDeniesWrongOwnerAndFutureSourceBeforeFetchingText(){
        var mapper=mock(CustomerContextMapper.class);var repo=new CustomerContextRepository(mapper,json);
        assertThatThrownBy(()->repo.source(scope,1)).isInstanceOf(SecurityException.class);verify(mapper,never()).source(any(),anyLong());
        when(mapper.owned(scope)).thenReturn(2L);
        assertThatThrownBy(()->repo.source(scope,101)).hasMessage("CONTEXT_SOURCE_OUTSIDE_WATERMARK");verify(mapper,never()).source(any(),anyLong());
    }
}
