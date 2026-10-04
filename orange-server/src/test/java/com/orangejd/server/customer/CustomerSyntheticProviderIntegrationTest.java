package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in: only synthetic text, system prompt and empty account/history/evidence. */
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_SYNTHETIC",matches="true")
class CustomerSyntheticProviderIntegrationTest {
    @Test void actualProviderProtocolAndJavaSemantics() throws Exception {
        ObjectMapper mapper=new ObjectMapper(); CustomerJson json=new CustomerJson(mapper);
        Path root=Files.isDirectory(Path.of("docs/customer"))?Path.of("."):Path.of("..");
        String report=System.getenv().getOrDefault("CUSTOMER_SYNTHETIC_REPORT","customer-v4-synthetic");
        if(!report.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("Invalid report name");
        List<Map<String,Object>> cases=mapper.readValue(root.resolve("docs/customer/acceptance/"+report+"-cases.json").toFile(),new com.fasterxml.jackson.core.type.TypeReference<>(){});
        String endpoint=System.getenv("CUSTOMER_PROBE_ENDPOINT"),key=System.getenv("CUSTOMER_PROBE_KEY");
        assertThat(java.net.URI.create(endpoint).getHost()).isEqualTo("api.deepseek.com");
        AiRuntimeConfigService configs=mock(AiRuntimeConfigService.class);
        when(configs.resolveText(null)).thenReturn(new AiRuntimeConfig("TEXT","synthetic-probe",endpoint,key,"deepseek-flash","SYNTHETIC_ONLY"));
        CustomerProperties settings=new CustomerProperties(); settings.setNativeActions(true); settings.setThinking(true);
        CustomerModel model=new CustomerModel(new ExternalAiClient(configs,"deepseek-flash",true),settings,json);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var c:cases) {
            String message=c.get("message").toString(); List<Map<String,Object>> usage=new ArrayList<>();
            Map<String,Object> row=new LinkedHashMap<>(); row.put("id",c.get("id")); row.put("message",message); long start=System.nanoTime();
            try {
                Map<String,Object> prior=c.get("prior") instanceof Map<?,?> raw?json.read(json.write(raw)):Map.of();
                var input=CustomerDialogue.input(message,prior,"",List.of(),"",6000,json);
                var action=model.call(CustomerState.PROMPT,input,Instant.now().plusSeconds(90),usage);
                List<Map<String,Object>> rejected=new ArrayList<>();
                var state=CustomerState.reconcile(prior,action,message,"",rejected);
                row.put("action",action); row.put("state",state); row.put("rejected",rejected);
                Map<?,?> expected=(Map<?,?>)c.get("expected");
                boolean passed=true;
                for(var e:expected.entrySet()) {
                    if(e.getKey().equals("pending")) passed &= state.get("pendingConditions") instanceof List<?> l && !l.isEmpty();
                    else if(e.getKey().equals("noPending")) passed &= !state.containsKey("pendingConditions");
                    else if(e.getKey().equals("pendingFields")) passed &= state.get("pendingConditions") instanceof List<?> l && l.stream().map(p->((Map<?,?>)p).get("field")).toList().equals(e.getValue());
                    else if(e.getKey().equals("absent")) passed &= !state.containsKey(e.getValue());
                    else if(e.getKey().equals("intent")) passed &= e.getValue().equals(action.get("intent"));
                    else if(e.getKey().equals("positions")) passed &= e.getValue().equals(action.get("positions"));
                    else if(e.getKey().equals("goal")) passed &= new HashSet<>(CustomerGoals.values(e.getValue())).equals(new HashSet<>(CustomerGoals.values(state.get("goal"))));
                    else passed &= e.getValue().equals(state.get(e.getKey()));
                }
                if("CHAT".equals(action.get("intent"))) passed &= !CustomerDialogue.reply(action).isBlank();
                row.put("passed",passed);
            } catch(RuntimeException ex) { row.put("passed",false); row.put("errorCode",CustomerRecovery.code(ex)); }
            row.put("modelCalls",usage); row.put("durationMs",(System.nanoTime()-start)/1_000_000); rows.add(row);
            mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve("docs/customer/acceptance/"+report+".json").toFile(),Map.of("scope","Synthetic text only; no account profile, platform documents or real history", "promptVersion",CustomerConversation.PROMPT_VERSION,"rows",rows,"costUsd","UNKNOWN"));
            System.out.println("SYNTHETIC "+row.get("id")+" passed="+row.get("passed")+" ms="+row.get("durationMs"));
        }
        assertThat(rows).allMatch(r->Boolean.TRUE.equals(r.get("passed")));
    }
}
