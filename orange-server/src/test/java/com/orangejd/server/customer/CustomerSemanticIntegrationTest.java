package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic dialogue only: no account profile/history or repository evidence is sent. */
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_SEMANTIC_PROBE",matches="true")
class CustomerSemanticIntegrationTest {
    @Test void compositeTurnsAndReferencesUseRealConfiguredModel() throws Exception {
        var env=new StandardEnvironment();
        for(String name:List.of("application-dev.yml","application.yml"))for(var source:new YamlPropertySourceLoader().load(name,new ClassPathResource(name)))env.getPropertySources().addLast(source);
        var connection=new LettuceConnectionFactory(env.resolveRequiredPlaceholders("${orange.redis.host:redis}"),Integer.parseInt(env.resolveRequiredPlaceholders("${orange.redis.port:6379}")));
        connection.setPassword(env.resolveRequiredPlaceholders("${orange.redis.password:}"));connection.setDatabase(Integer.parseInt(env.resolveRequiredPlaceholders("${orange.redis.database:0}")));connection.afterPropertiesSet();
        try {
            var redis=new StringRedisTemplate(connection);var crypto=new AiConfigCrypto(env.resolveRequiredPlaceholders("${orange.ai-config.encryption-key:}"));
            var constructor=AiRuntimeConfigService.class.getConstructors()[0];var parameters=constructor.getParameters();Object[] args=new Object[parameters.length];
            for(int i=0;i<parameters.length;i++)args[i]=parameters[i].getType()==StringRedisTemplate.class?redis:parameters[i].getType()==AiConfigCrypto.class?crypto:env.resolveRequiredPlaceholders(parameters[i].getAnnotation(Value.class).value());
            var runtime=(AiRuntimeConfigService)constructor.newInstance(args);var config=new CustomerProperties();
            config.setModel(env.resolveRequiredPlaceholders("${orange.customer.model:deepseek-flash}"));config.setThinking(Boolean.parseBoolean(env.resolveRequiredPlaceholders("${orange.customer.thinking:false}")));config.setNativeActions(true);
            var mapper=new ObjectMapper();var json=new CustomerJson(mapper);var model=new CustomerModel(new ExternalAiClient(runtime,config.getModel(),config.isThinking()),config,json);
            List<Map<String,Object>> identities=List.of(Map.of("id",901,"type","EXTERNAL_PROJECT","name","synthetic-one"),Map.of("id",902,"type","EXTERNAL_PROJECT","name","synthetic-two"));
            List<Map<String,Object>> cases=List.of(
                Map.of("id","greeting","message","你好","state",Map.of(),"intent","CHAT"),
                Map.of("id","composite","message","用途核对是什么？把周期放宽到半年，再推荐项目。","state",Map.of("language","Java","maxDurationDays",60),"intent","RECOMMEND","days",180),
                Map.of("id","record","message","我想用Python做AI应用开发，目标求职，半年内完成，先记录不用推荐。","state",Map.of(),"intent","CHAT","days",180),
                Map.of("id","clear","message","不再限定刚学对应的难度，继续推荐","state",Map.of("experience","刚学","difficulty","BEGINNER"),"intent","RECOMMEND"),
                Map.of("id","ordinal","message","第二个怎么部署","state",Map.of("lastCandidates",identities),"intent","DETAIL","position",2),
                Map.of("id","focus","message","它需要Docker吗","state",Map.of("lastCandidates",identities,"focusedProject",identities.get(1)),"intent","DETAIL","position",2),
                Map.of("id","exclude","message","不要第一个，换一批","state",Map.of("lastCandidates",identities),"intent","RECOMMEND"),
                Map.of("id","ambiguous-duration","message","项目必须在十来个工作周内完成","state",Map.of(),"pending",true));
            List<Map<String,Object>> rows=new ArrayList<>();
            for(var c:cases) {
                var prior=new LinkedHashMap<>((Map<String,Object>)c.get("state"));String message=c.get("message").toString();
                var calls=new ArrayList<Map<String,Object>>();var row=new LinkedHashMap<String,Object>(Map.of("caseId",c.get("id"),"message",message));long start=System.nanoTime();
                try {
                    var action=new LinkedHashMap<>(model.call(CustomerState.PROMPT,CustomerDialogue.input(message,prior,"",List.of(),"",6000,json),Instant.now().plusSeconds(90),calls));
                    var state=CustomerState.reconcile(prior,action,message,"",new ArrayList<>());CustomerTurnPlan.resolve(action,state,message);CustomerProjectFeedback.apply(state,action.get("projectFeedback"),message,"synthetic");
                    boolean passed=!c.containsKey("intent") || c.get("intent").equals(action.get("intent"));
                    if(c.containsKey("days"))passed &= c.get("days").equals(state.get("maxDurationDays"));
                    if(c.containsKey("position"))passed &= List.of(c.get("position")).equals(action.get("positions"));
                    if("composite".equals(c.get("id")))passed &= !CustomerTurnPlan.preamble(action,prior,message).isBlank();
                    if("clear".equals(c.get("id")))passed &= !state.containsKey("difficulty") && "刚学".equals(state.get("experience"));
                    if("record".equals(c.get("id")))passed &= "Python".equals(state.get("language")) && CustomerTopics.normalize(state.get("topic")).contains("AI应用开发") && CustomerGoals.display(state.get("goal")).contains("求职");
                    if("exclude".equals(c.get("id")))passed &= CustomerProjectFeedback.excluded(identities.getFirst(),state);
                    if(Boolean.TRUE.equals(c.get("pending")))passed &= state.get("pendingConditions") instanceof List<?> p && !p.isEmpty() && !state.containsKey("maxDurationDays");
                    row.put("passed",passed);row.put("action",action);row.put("state",state);
                } catch(RuntimeException ex) {row.put("passed",false);row.put("errorCode",CustomerRecovery.code(ex));}
                row.put("durationMs",(System.nanoTime()-start)/1_000_000);row.put("calls",calls);rows.add(row);
            }
            Path out=Path.of("../docs/customer/orchestration-v419/semantic-probe.json");Files.createDirectories(out.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("scope","8 synthetic semantic cases, no real account or project evidence","model",config.getModel(),"thinking",config.isThinking(),"promptVersion",CustomerConversation.PROMPT_VERSION,"cases",rows,"costUsd","UNKNOWN"));
            assertThat(rows).allMatch(r->Boolean.TRUE.equals(r.get("passed")));
        } finally {connection.destroy();}
    }
}
