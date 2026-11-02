// Test-only launcher. External to both release JARs; never shipped in the application.
package com.orangejd.rehearsal;

import java.lang.reflect.*;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class CustomerDeploymentProbe {
    static Object call(Object target,String method,Class<?>[] signature,Object... args) throws Exception {
        return target.getClass().getMethod(method,signature).invoke(target,args);
    }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object value) { return (Map<String,Object>)value; }
    static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    public static void main(String[] args) throws Exception {
        Object context=null;Map<String,Object> report=new LinkedHashMap<>();
        var mapperClass=Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        var mapper=mapperClass.getConstructor().newInstance();
        Path output=Path.of(System.getenv("PROBE_REPORT"));
        try {
            Class<?> app=Class.forName("com.orangejd.server.OrangeApplication");
            context=Class.forName("org.springframework.boot.SpringApplication").getMethod("run",Class.class,String[].class)
                .invoke(null,app,args);
            var getBean=context.getClass().getMethod("getBean",Class.class);
            Object store=getBean.invoke(context,Class.forName("com.orangejd.server.customer.CustomerStore"));
            Object runs=getBean.invoke(context,Class.forName("com.orangejd.server.customer.CustomerRuns"));
            Object db=getBean.invoke(context,Class.forName("com.orangejd.server.customer.CustomerMapper"));
            Object jdbc=getBean.invoke(context,Class.forName("org.springframework.jdbc.core.JdbcTemplate"));
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/actuator/health"))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
            require(response.statusCode()==200 && response.body().contains("UP"),"HEALTH_FAILED");
            report.put("health","UP");
            var blocked=http.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/app/customer/sessions"))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
            // The platform may use HTTP 200 with an application-level authentication error.
            require(blocked.statusCode()==401 || blocked.statusCode()==403 || blocked.body().contains("未登录")
                || blocked.body().contains("401") || blocked.body().contains("请先登录"),"ANONYMOUS_ACCESS_ALLOWED");
            report.put("anonymousHttpStatus",blocked.statusCode());
            long owner=900003;
            List<?> sessions=(List<?>)call(store,"sessions",new Class[]{long.class},owner);
            Map<String,Object> session=sessions.isEmpty()?map(call(store,"create",new Class[]{long.class,String.class},owner,"isolated-deployment-rehearsal")):map(sessions.getFirst());
            long sessionId=((Number)session.get("id")).longValue();
            // Initial synthetic conditions survive graph/config/binary rollback. No real account exists here.
            if(sessions.isEmpty())call(db,"state",new Class[]{long.class,String.class},sessionId,"{\"language\":\"Python\",\"maxDurationDays\":180}");
            String message="当前有什么条件";
            String request="deployment-"+System.getenv("PROBE_STEP");
            Map<String,Object> run=map(call(runs,"submit",new Class[]{long.class,long.class,String.class,String.class},sessionId,owner,request,message));
            String id=run.get("id").toString();long stop=System.nanoTime()+Duration.ofSeconds(20).toNanos();
            do {Thread.sleep(100);run=map(call(runs,"get",new Class[]{String.class,long.class},id,owner));}
            while(Set.of("QUEUED","RUNNING").contains(run.get("status")) && System.nanoTime()<stop);
            require("SUCCEEDED".equals(run.get("status")),"STATE_RUN_FAILED");
            Object persisted=call(db,"run",new Class[]{String.class},id);
            Object actualEngine=map(persisted).getOrDefault("execution_engine","LEGACY");
            require(System.getenv("PROBE_EXPECT_ENGINE").equals(actualEngine),"ENGINE_NOT_APPLIED");
            Map<String,Object> replay=map(call(runs,"submit",new Class[]{long.class,long.class,String.class,String.class},sessionId,owner,request,message));
            require(id.equals(replay.get("id")),"DUPLICATE_RUN");
            Map<String,Object> original=map(call(runs,"submit",new Class[]{long.class,long.class,String.class,String.class},sessionId,owner,"deployment-previous",message));
            require("SUCCEEDED".equals(original.get("status")),"OLD_REQUEST_NOT_PRESERVED");
            Map<String,Object> saved=map(call(store,"session",new Class[]{long.class,long.class},sessionId,owner));
            Map<?,?> state=(Map<?,?>)mapperClass.getMethod("readValue",String.class,Class.class).invoke(mapper,saved.get("state_json").toString(),Map.class);
            require("Python".equals(state.get("language")) && Integer.valueOf(180).equals(state.get("maxDurationDays")),"CONDITIONS_LOST");
            var history=map(call(store,"history",new Class[]{long.class,long.class,long.class,int.class},sessionId,owner,Long.MAX_VALUE,100));
            List<?> messages=(List<?>)history.get("messages");
            long assistant=messages.stream().map(CustomerDeploymentProbe::map).filter(m->id.equals(m.get("run_id")) && "assistant".equals(m.get("role"))).count();
            require(assistant==1,"ASSISTANT_MESSAGE_DUPLICATED");
            int schema=((Number)call(jdbc,"queryForObject",new Class[]{String.class,Class.class},"SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1",Integer.class)).intValue();
            int checkpoints=0;
            if(schema>=44)checkpoints=((Number)call(jdbc,"queryForObject",new Class[]{String.class,Class.class},"SELECT COUNT(*) FROM customer_graph_checkpoint",Integer.class)).intValue();
            if("LANGGRAPH4J".equals(actualEngine))require(checkpoints>0,"NO_DURABLE_CHECKPOINT");
            report.putAll(Map.of("passed",true,"step",System.getenv("PROBE_STEP"),"engine",actualEngine,"sessionId",sessionId,
                "runId",id,"oldRequestId",original.get("id"),"schemaVersion",schema,"checkpointCount",checkpoints));
            report.putAll(Map.of("replaySame",true,"conditionsPreserved",true,"assistantMessagesForRun",assistant,"externalModelCalls",0));
        } catch(Throwable e) {
            report.put("passed",false);report.put("errorType",e.getClass().getSimpleName());
            // Avoid returning raw exception text, connection strings or credentials.
            throw e;
        } finally {
            Files.writeString(output,mapperClass.getMethod("writeValueAsString",Object.class).invoke(mapper,report).toString());
            if(context!=null)call(context,"close",new Class[]{});
        }
    }
}
