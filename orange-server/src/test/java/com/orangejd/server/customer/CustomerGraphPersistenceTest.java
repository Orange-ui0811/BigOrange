package com.orangejd.server.customer;

import com.zaxxer.hikari.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CUSTOMER_TEST_MYSQL_URL",matches=".*customer_acceptance_.*")
class CustomerGraphPersistenceTest {
    static HikariDataSource pool;static JdbcTemplate jdbc;static CustomerGraphRepository repository;
    static CustomerJson json=new CustomerJson(new ObjectMapper());
    @BeforeAll static void setup() throws Exception {
        CustomerPersistenceIntegrationTest.setup();
        HikariConfig cfg=new HikariConfig();cfg.setJdbcUrl(System.getenv("CUSTOMER_TEST_MYSQL_URL"));cfg.setUsername(System.getenv("CUSTOMER_TEST_MYSQL_USER"));cfg.setPassword(System.getenv("CUSTOMER_TEST_MYSQL_PASSWORD"));cfg.setMaximumPoolSize(2);cfg.setConnectionTimeout(3000);
        pool=new HikariDataSource(cfg);jdbc=new JdbcTemplate(pool);
        ProxyFactory proxy=new ProxyFactory(new CustomerGraphRepository(jdbc));proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(pool),new AnnotationTransactionAttributeSource()));repository=(CustomerGraphRepository)proxy.getProxy();
    }
    @AfterAll static void close(){if(pool!=null)pool.close();}
    @BeforeEach void clear(){for(String table:List.of("customer_raw_access_audit","customer_raw_payload","customer_node_execution","customer_graph_checkpoint","customer_graph_execution","customer_step","customer_message","customer_run","customer_session"))jdbc.update("DELETE FROM "+table);}
    @Test void actualGraphCommitAndTwoConnectionsSurviveTwoHundredRuns() throws Exception {
        var db=CustomerPersistenceIntegrationTest.mapper;var store=CustomerPersistenceIntegrationTest.store;
        var config=new CustomerProperties();config.setEnabled(true);config.setAllowedAccounts(Set.of(900001L,900002L));
        var tools=mock(CustomerTools.class);var model=mock(CustomerModel.class);var projects=mock(CustomerProjects.class);
        when(model.call(anyString(),any(),any(),anyList())).thenReturn(Map.of("intent","CHAT","reply","你好，我们可以继续聊项目方向。","changes",Map.of()));
        when(tools.context(anyString(),anyLong(),any())).thenAnswer(i->{var ctx=mock(CustomerTools.Context.class);org.springframework.test.util.ReflectionTestUtils.setField(ctx,"run",i.getArgument(0));when(ctx.profile()).thenReturn("");return ctx;});
        var app=new CustomerConversation(store,db,json,model,tools,projects,config);
        var trace=new CustomerExecutionTrace(repository,json,new SimpleMeterRegistry());
        var graph=new LangGraphCustomerEngine(app,repository,json,trace);var legacy=new LegacyCustomerEngine(app,trace);
        var owners=List.of(900001L,900002L);Map<Long,Long> sessions=new HashMap<>();for(long owner:owners)sessions.put(owner,CustomerStore.number(store.create(owner,"图验收"),"id"));
        var workers=Executors.newFixedThreadPool(2);Map<String,List<Long>> durations=new LinkedHashMap<>();Map<String,List<Map<String,Object>>> components=new ConcurrentHashMap<>();
        try {
            for(CustomerExecutionEngine engine:List.of(legacy,graph)) {
                List<Long> times=new CopyOnWriteArrayList<>();
                for(int batch=0;batch<55;batch++) {
                    final boolean measured=batch>=5;List<Callable<Void>> jobs=new ArrayList<>();
                    for(long owner:owners)jobs.add(()->{
                        var run=store.submit(sessions.get(owner),owner,UUID.randomUUID().toString(),"你好");String id=run.get("id").toString();
                        jdbc.update("UPDATE customer_run SET execution_engine=? WHERE id=?",engine.kind().name(),id);
                        var ledger=new CustomerUsageLedger(id,trace);
                        long start=System.nanoTime();var result=engine.execute(new CustomerRunContext(id,Instant.now().plusSeconds(90),ledger));store.complete(id,result.result(),result.state(),Map.of());trace.archive(id,"SUCCEEDED",null);
                        if(measured)components.computeIfAbsent(engine.kind().name(),key->new CopyOnWriteArrayList<>()).addAll(ledger.orchestration);
                        if(measured)times.add((System.nanoTime()-start)/1_000_000);assertThat(db.run(id)).containsEntry("status","SUCCEEDED");return null;
                    });
                    for(var future:workers.invokeAll(jobs))future.get();
                }
                durations.put(engine.kind().name(),times.stream().sorted().toList());
            }
        } finally {workers.shutdownNow();}
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_graph_execution",Integer.class)).isEqualTo(110);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_message WHERE role='assistant'",Integer.class)).isEqualTo(220);
        var legacy95=durations.get("LEGACY").get(94);var graph95=durations.get("LANGGRAPH4J").get(94);
        java.nio.file.Path report=java.nio.file.Path.of("target/customer-graph-overhead.json");java.nio.file.Files.writeString(report,json.write(Map.of("measuredPerEngine",100,"concurrency",2,"warmupPerEngine",10,"legacyP95Ms",legacy95,"graphP95Ms",graph95,"additionalP95Ms",graph95-legacy95,"activeConnectionsAfter",pool.getHikariPoolMXBean().getActiveConnections(),"releaseApproved",false,"componentTimings",components)));
        assertThat(graph95-legacy95).isLessThanOrEqualTo(200);
    }
    @Test void rawAccessAuditsBeforeReturningAndExpiredPayloadIsUnavailable(){
        repository.payload("p","run","n","MODEL_REQUEST","{\"messages\":[]}");
        assertThat(repository.raw(900001,"run","p","排查引用")).containsKey("content_json");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_raw_access_audit",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->repository.raw(900001,"another","p","跨运行")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE customer_raw_payload SET created_at=CURRENT_TIMESTAMP - INTERVAL 31 DAY");
        assertThatThrownBy(()->repository.raw(900001,"run","p","过期")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void rawAuditFailureReturnsNoPayload(){
        repository.payload("p","run","n","MODEL_REQUEST","{}");
        assertThatThrownBy(()->repository.raw(900001,"run","p","过长".repeat(1000))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_raw_access_audit",Integer.class)).isZero();
    }
    @Test void cleanupHonorsThirtyNinetyDaysAndPreservesActiveRuns(){
        var store=CustomerPersistenceIntegrationTest.store;
        long first=CustomerStore.number(store.create(900001,"清理终态"),"id");
        String terminal=store.submit(first,900001,"cleanup001","你好").get("id").toString();
        store.complete(terminal,Map.of("answer","完成"),Map.of(),Map.of());
        long second=CustomerStore.number(store.create(900002,"清理活跃"),"id");
        String active=store.submit(second,900002,"cleanup002","你好").get("id").toString();
        for(String id:List.of(terminal,active)){
            repository.begin(id);repository.checkpoint(id,id,"load_context","END","{}");
            repository.payload(id,id,"node","MODEL_REQUEST","{}");repository.nodeStart(id,id,"LANGGRAPH4J","load_context");
        }
        for(String table:List.of("customer_graph_checkpoint","customer_raw_payload","customer_node_execution","customer_graph_execution"))jdbc.update("UPDATE "+table+" SET created_at=CURRENT_TIMESTAMP - INTERVAL 31 DAY");
        repository.cleanup();
        assertThat(repository.checkpoints(terminal)).isEmpty();assertThat(repository.checkpoints(active)).hasSize(1);
        assertThat(repository.nodes(terminal)).hasSize(1);
        jdbc.update("UPDATE customer_node_execution SET created_at=CURRENT_TIMESTAMP - INTERVAL 91 DAY");
        repository.cleanup();assertThat(repository.nodes(terminal)).isEmpty();assertThat(repository.nodes(active)).hasSize(1);
    }
    @Test void sessionDeletionImmediatelyRemovesTerminalRawAndCheckpoints(){
        var store=CustomerPersistenceIntegrationTest.store;
        long session=CustomerStore.number(store.create(900001,"删除验收"),"id");
        String run=store.submit(session,900001,"delete001","你好").get("id").toString();
        repository.checkpoint(run,"c","load_context","END","{}");repository.payload("p",run,"n","MODEL_REQUEST","{}");
        assertThatThrownBy(()->store.deleteSession(session,900001)).hasMessageContaining("409");
        store.complete(run,Map.of("answer","完成"),Map.of(),Map.of());store.deleteSession(session,900001);
        assertThat(repository.checkpoints(run)).isEmpty();assertThat(repository.payloads(run)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_message WHERE session_id=?",Integer.class,session)).isEqualTo(2);
    }
    @Test @EnabledIfEnvironmentVariable(named="CUSTOMER_EMPTY_MYSQL_URL",matches=".*customer_acceptance_.*_empty.*")
    void emptySchemaStartsAtLatestMigrationWithoutAnIntermediateTarget(){
        var data=new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getenv("CUSTOMER_EMPTY_MYSQL_URL"),System.getenv("CUSTOMER_TEST_MYSQL_USER"),System.getenv("CUSTOMER_TEST_MYSQL_PASSWORD"));
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(data).locations("classpath:db/migration").load();
        flyway.migrate();flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("46");
        assertThat(new JdbcTemplate(data).queryForObject("SELECT COUNT(*) FROM customer_graph_checkpoint",Integer.class)).isZero();
    }
    @Test void reviewedEvidenceStatusFitsExistingSchema(){
        assertThatThrownBy(()->CustomerPersistenceIntegrationTest.mapper.step("test-run","reviewProjectPurpose","KNOWN_EVIDENCE_SUFFICIENT","{}")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        CustomerPersistenceIntegrationTest.mapper.step("test-run","reviewProjectPurpose",CustomerRecommendationSteps.KNOWN_EVIDENCE_STATUS,"{}");
        assertThat(CustomerPersistenceIntegrationTest.mapper.steps("test-run")).singleElement().satisfies(row->assertThat(row).containsEntry("status",CustomerRecommendationSteps.KNOWN_EVIDENCE_STATUS));
    }
}
