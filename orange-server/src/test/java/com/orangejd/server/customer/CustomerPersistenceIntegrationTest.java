package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in disposable database test; refuses ordinary application database names. */
@EnabledIfEnvironmentVariable(named="CUSTOMER_TEST_MYSQL_URL",matches=".*customer_acceptance_.*")
class CustomerPersistenceIntegrationTest {
    static DataSource source;
    static CustomerMapper mapper;
    static CustomerSourceFactMapper facts;
    static CustomerStore store;
    static JdbcTemplate jdbc;
    @BeforeAll static void setup() throws Exception {
        DriverManagerDataSource data=new DriverManagerDataSource();
        data.setUrl(System.getenv("CUSTOMER_TEST_MYSQL_URL")); data.setUsername(System.getenv("CUSTOMER_TEST_MYSQL_USER")); data.setPassword(System.getenv("CUSTOMER_TEST_MYSQL_PASSWORD")); source=data;
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("40").load().migrate();
        Flyway full=Flyway.configure().dataSource(source).locations("classpath:db/migration").load(); full.migrate(); full.validate();
        jdbc=new JdbcTemplate(source);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
        org.apache.ibatis.session.Configuration mybatis=new org.apache.ibatis.session.Configuration(); mybatis.addMapper(CustomerMapper.class);mybatis.addMapper(CustomerSourceFactMapper.class); factory.setConfiguration(mybatis);
        mapper=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(CustomerMapper.class);
        facts=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(CustomerSourceFactMapper.class);
        CustomerProperties props=new CustomerProperties(); props.setEnabled(true); props.setAllowedAccounts(Set.of(900001L,900002L));
        CustomerStore target=new CustomerStore(mapper,new CustomerJson(new ObjectMapper()),props);
        ProxyFactory proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource())); store=(CustomerStore)proxy.getProxy();
    }
    @BeforeEach void clear() { jdbc.update("DELETE FROM customer_step"); jdbc.update("DELETE FROM customer_message"); jdbc.update("DELETE FROM customer_run"); jdbc.update("DELETE FROM customer_session");jdbc.update("DELETE FROM customer_source_fact_event");jdbc.update("DELETE FROM customer_source_fact"); }
    @Test void sourceFactsPersistRevisionsAndRevocationCannotBeOverwrittenBySeed() {
        assertThat(facts.insert("test-fact","{\"quote\":\"source\"}","DRAFT")).isEqualTo(1);
        assertThat(facts.transition("test-fact",1,"REVIEWED",900001L)).isEqualTo(1);
        assertThat(facts.transition("test-fact",1,"REVIEWED",900002L)).isZero();
        facts.event("test-fact",2,"REVIEWED",900001L,"核对");
        assertThat(facts.transition("test-fact",2,"REVOKED",900001L)).isEqualTo(1);
        assertThat(facts.insert("test-fact","{}","REVIEWED")).isZero();
        assertThat(facts.all().getFirst()).containsEntry("status","REVOKED");
        assertThat(facts.events("test-fact")).hasSize(1);
    }
    @Test void migratedSchemaAndActualReplayPreserveMessages() {
        var session=store.create(900001,"测试"); long id=CustomerStore.number(session,"id");
        var first=store.submit(id,900001,"request001","Java项目"); var second=store.submit(id,900001,"request001","Java项目");
        assertThat(first.get("id")).isEqualTo(second.get("id")); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_message",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->store.submit(id,900001,"request001","Python项目")).hasMessageContaining("409");
    }
    @Test void commitMakesResultStateAndHistoryConsistent() {
        long id=CustomerStore.number(store.create(900001,"测试"),"id"); String run=store.submit(id,900001,"request001","你好").get("id").toString();
        store.complete(run,Map.of("answer","追问"),Map.of("language","Java"),Map.of());
        assertThat(mapper.run(run)).containsEntry("status","SUCCEEDED");
        assertThat(store.history(id,900001,Long.MAX_VALUE,50).get("state")).isEqualTo(Map.of("language","Java"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_message",Integer.class)).isEqualTo(2);
    }
    @Test void databaseMutexRejectsConcurrentRunsForOneOwner() throws Exception {
        long session=CustomerStore.number(store.create(900001,"并发"),"id"); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<Callable<Boolean>>of(()->submit(session,"request001"),()->submit(session,"request002"));
            long successes=0; for(var result:pool.invokeAll(tasks)) if(result.get()) successes++;
            assertThat(successes).isEqualTo(1); assertThat(mapper.active()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    boolean submit(long session,String request) { try { store.submit(session,900001,request,"测试"); return true; } catch(org.springframework.web.server.ResponseStatusException ex) { return false; } }
    @Test void cancelledRunCannotBeResurrectedByLateCompletion() {
        long session=CustomerStore.number(store.create(900001,"取消"),"id"); String id=store.submit(session,900001,"request001","测试").get("id").toString();
        store.terminal(id,"CANCELLED","USER_CANCELLED",Map.of()); store.complete(id,Map.of("answer","迟到"),Map.of("language","Python"),Map.of());
        assertThat(mapper.run(id)).containsEntry("status","CANCELLED"); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_message WHERE role='assistant'",Integer.class)).isEqualTo(1);
    }
}
