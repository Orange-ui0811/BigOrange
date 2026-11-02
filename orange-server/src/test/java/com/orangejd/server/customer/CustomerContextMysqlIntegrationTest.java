package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** A dedicated EMPTY disposable database; never uses application credentials or calls a model. */
@EnabledIfEnvironmentVariable(named="CUSTOMER_CONTEXT_TEST_MYSQL_URL",matches="jdbc:mysql://[^/]+/customer_context_acceptance_[A-Za-z0-9_]+.*")
class CustomerContextMysqlIntegrationTest {
    static JdbcTemplate jdbc;static CustomerContextRepository repository;static CustomerHistoryRecovery recovery;static CustomerContextMapper mapper;
    static final CustomerHistoryRecovery.Scope SCOPE=new CustomerHistoryRecovery.Scope(10,1,"current","task-a",3,100);
    @BeforeAll static void setup() throws Exception {
        var data=new DriverManagerDataSource();data.setUrl(System.getenv("CUSTOMER_CONTEXT_TEST_MYSQL_URL"));data.setUsername(System.getenv("CUSTOMER_CONTEXT_TEST_MYSQL_USER"));data.setPassword(System.getenv("CUSTOMER_CONTEXT_TEST_MYSQL_PASSWORD"));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V41__add_customer_service.sql"),new ClassPathResource("db/migration/V42__customer_session_deletion.sql"),new ClassPathResource("db/migration/V46__customer_context_memory.sql")).execute(data);
        jdbc=new JdbcTemplate(data);var factory=new SqlSessionFactoryBean();factory.setDataSource(data);var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(CustomerContextMapper.class);factory.setConfiguration(configuration);
        mapper=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(CustomerContextMapper.class);
        var json=new CustomerJson(new ObjectMapper());var target=new CustomerContextRepository(mapper,json);var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data),new AnnotationTransactionAttributeSource()));repository=(CustomerContextRepository)proxy.getProxy();recovery=new CustomerHistoryRecovery(repository,json);
    }
    @BeforeEach void seed() {
        for(String table:List.of("customer_context_summary","customer_context_summary_lease","customer_context_run","customer_message","customer_run","customer_session"))jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO customer_session(id,account_id,title,state_json) VALUES(1,10,'fixture','{}'),(2,20,'other','{}')");
        run("old",1,10,"SUCCEEDED");run("legacy",1,10,"SUCCEEDED");run("other-task",1,10,"SUCCEEDED");run("foreign",2,20,"SUCCEEDED");run("future",1,10,"SUCCEEDED");run("current",1,10,"RUNNING");
        jdbc.update("INSERT INTO customer_context_run(run_id,session_id,task_id,requirement_version) VALUES('old',1,'task-a',1),('other-task',1,'task-b',1),('future',1,'task-a',3)");
        message(1,1,"old","user","Java项目必须离线运行。");message(2,1,"old","assistant","尚未找到符合要求项目，也未导入。");
        message(3,1,"legacy","user","历史Java信息，但任务归属未知。");message(4,1,"other-task","user","另一个任务允许联网。");message(5,2,"foreign","user","其他用户的私有消息。");message(101,1,"future","user","本轮之后才出现的消息。");
        repository.bind(SCOPE);
    }
    static void run(String id,long session,long owner,String status){jdbc.update("INSERT INTO customer_run(id,session_id,account_id,request_id,input_hash,message,status,stage,prompt_version,deadline_at) VALUES(?,?,?,?,?,?,?,?,?,DATE_ADD(NOW(),INTERVAL 1 HOUR))",id,session,owner,"request-"+id,"0".repeat(64),"fixture",status,"fixture","fixture");}
    static void message(long id,long session,String run,String role,String text){jdbc.update("INSERT INTO customer_message(id,session_id,run_id,role,content,status) VALUES(?,?,?,?,?,'COMPLETED')",id,session,run,role,text);}
    @Test void ownershipWatermarkAndLegacyTaskIsolationApplyToActualQueries(){
        var context=recovery.open(SCOPE);assertThat(context.read(3,0,100,"")).containsEntry("attribution","UNASSIGNED_LEGACY").containsEntry("historicalOnly",true);
        assertThatThrownBy(()->repository.source(SCOPE,5)).hasMessage("CONTEXT_SOURCE_NOT_FOUND");
        assertThatThrownBy(()->repository.source(SCOPE,101)).hasMessage("CONTEXT_SOURCE_OUTSIDE_WATERMARK");
        var wrong=new CustomerHistoryRecovery.Scope(20,1,"current","task-a",3,100);assertThatThrownBy(()->recovery.open(wrong)).isInstanceOf(SecurityException.class);
        assertThat(repository.completed(SCOPE,0)).extracting(r->((Number)r.get("id")).longValue()).containsExactly(1L,2L);
    }
    @Test void callbacksSearchAndPaginationUseStoredRawSources(){
        var context=recovery.open(SCOPE);var found=context.search("Java",false);
        assertThat(found).extracting(r->r.get("messageId")).contains(1L,3L).doesNotContain(5L,101L);
        String hash=CustomerStore.hash("Java项目必须离线运行。");assertThat(context.read(1,0,1200,hash)).containsEntry("text","Java项目必须离线运行。");
        assertThatThrownBy(()->context.read(1,0,1200,hash)).hasMessage("CONTEXT_RECOVERY_LIMIT");
    }
    @Test void summaryLeaseIsExclusiveAndExpiredOrCancelledWritesCannotCommit(){
        String lease=repository.claimSummary(SCOPE);assertThat(lease).isNotBlank();assertThat(repository.claimSummary(SCOPE)).isNull();
        var rows=repository.completed(SCOPE,0);String hash=CustomerContextRepository.sourceHash(rows);
        assertThat(repository.saveSummary(SCOPE,"wrong",rows,hash,"历史背景","mock")).isFalse();
        jdbc.update("UPDATE customer_run SET status='CANCELLED' WHERE id='current'");
        assertThatThrownBy(()->repository.saveSummary(SCOPE,lease,rows,hash,"历史背景","mock")).hasMessage("RUN_STOPPED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_context_summary",Integer.class)).isZero();
    }
    @Test void immutableSummarySourceHashOverlapAndTaskBindingAreRechecked(){
        String lease=repository.claimSummary(SCOPE);var rows=repository.completed(SCOPE,0);String hash=CustomerContextRepository.sourceHash(rows);
        assertThat(repository.saveSummary(SCOPE,lease,rows,hash,"用户要求离线；客服尚未导入。","mock")).isTrue();
        assertThat(repository.saveSummary(SCOPE,lease,rows,hash,"重复段落","mock")).isFalse();
        var service=new CustomerSummaryService(repository,org.mockito.Mockito.mock(CustomerModel.class),new CustomerJson(new ObjectMapper()),new CustomerProperties());assertThat(service.summaries(SCOPE)).hasSize(1);
        jdbc.update("UPDATE customer_message SET content='模拟来源被修改' WHERE id=1");assertThat(service.summaries(SCOPE)).isEmpty();
        var wrongTask=new CustomerHistoryRecovery.Scope(10,1,"current","task-b",3,100);assertThatThrownBy(()->repository.bind(wrongTask)).hasMessage("STALE_CONTEXT_TASK");
    }
}
