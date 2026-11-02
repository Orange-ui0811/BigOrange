package com.orangejd.server.foundation.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.aitool.AiToolProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.*;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Loopback only. The live acceptance ledger is never used or reset. */
@ResourceLock("acceptance-ledger")
class CustomerSpringAiChatModelTest {
    final ObjectMapper json=new ObjectMapper();
    @TempDir Path temp;
    AiRuntimeConfig config(String endpoint,String model) {return new AiRuntimeConfig("TEXT","fixture",endpoint,"test-only",model,"TEST");}
    CustomerSpringAiChatModel driver(String endpoint,boolean nativeAction,int inputLimit) {
        return new CustomerSpringAiChatModel(config(endpoint,"fixture-model"),3,256,nativeAction,false,inputLimit,(kind,payload)->{});
    }
    final class Endpoint implements AutoCloseable {
        final HttpServer server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        final AtomicInteger calls=new AtomicInteger();
        final List<Map<String,Object>> requests=new ArrayList<>();
        final AtomicReference<String> authorization=new AtomicReference<>();
        Endpoint(int status,String response) throws Exception {
            this(status,response,0);
        }
        Endpoint(int status,String response,long delayMs) throws Exception {
            server.createContext("/v1/chat/completions",exchange->{
                calls.incrementAndGet();authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                synchronized(requests){requests.add(json.readValue(exchange.getRequestBody(),new TypeReference<Map<String,Object>>(){}));}
                if(delayMs>0)try {Thread.sleep(delayMs);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();
        }
        String url(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/v1";}
        @Override public void close(){server.stop(0);}
    }
    String response(Object content,String finish,Map<String,Object> usage) throws Exception {
        var body=new LinkedHashMap<String,Object>();body.put("model","served-model");
        body.put("choices",List.of(Map.of("message",Map.of("role","assistant","content",content),"finish_reason",finish)));
        if(usage!=null)body.put("usage",usage);return json.writeValueAsString(body);
    }
    interface Checked {void run() throws Exception;}
    void withLedger(int limit,Checked action) throws Exception {
        String oldFile=System.getProperty("orange.acceptance.ledger"),oldLimit=System.getProperty("orange.acceptance.limit");
        try {System.setProperty("orange.acceptance.ledger",temp.resolve("calls.jsonl").toString());System.setProperty("orange.acceptance.limit",Integer.toString(limit));action.run();}
        finally {if(oldFile==null)System.clearProperty("orange.acceptance.ledger");else System.setProperty("orange.acceptance.ledger",oldFile);if(oldLimit==null)System.clearProperty("orange.acceptance.limit");else System.setProperty("orange.acceptance.limit",oldLimit);}
    }
    @Test void typedModelPreservesProviderMetadataAndObservedPayloads() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(200,response("```json\n{\"ok\":true}\n```","length",Map.of("prompt_tokens",12,"completion_tokens",5,"total_tokens",17,"cache_hit_tokens",3)))) {
            var events=new ArrayList<String>();
            ChatModel model=new CustomerSpringAiChatModel(config(endpoint.url(),"fixture-model"),3,256,false,false,6000,(kind,payload)->events.add(kind));
            var result=model.call(new Prompt(List.of(new SystemMessage("rules"),new UserMessage("question"))));
            assertThat(result.getResult().getOutput().getText()).isEqualTo("{\"ok\":true}");
            assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("length");
            assertThat(result.getMetadata().getModel()).isEqualTo("served-model");
            assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(17);
            assertThat((Map<String,Object>)result.getMetadata().get(CustomerSpringAiChatModel.RAW_USAGE)).containsEntry("cache_hit_tokens",3);
            assertThat(events).containsExactly("MODEL_REQUEST","MODEL_RESPONSE");
            assertThat(endpoint.calls).hasValue(1);assertThat(endpoint.authorization.get()).isEqualTo("Bearer test-only");
            assertThat(endpoint.requests.getFirst()).containsEntry("max_tokens",256).containsEntry("model","fixture-model");
            assertThat(Files.readAllLines(temp.resolve("calls.jsonl"))).hasSize(1);
        }});
    }
    @Test void facadeResolvesDynamicConfigForEachTypedCall() throws Exception {
        withLedger(3,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null))) {
            var runtime=mock(AiRuntimeConfigService.class);
            when(runtime.resolveText(null)).thenReturn(config(endpoint.url(),"first-model"),config(endpoint.url(),"second-model"));
            var facade=new ExternalAiClient(runtime,"");
            assertThat(facade.callCustomer("规则 {\"mode\":\"strict\"}","{\"question\":\"hello\"}",3,256).usage()).isEmpty();
            facade.callCustomer("rules","question",3,256);
            assertThat(endpoint.requests).extracting(row->row.get("model")).containsExactly("first-model","second-model");
            assertThat((List<Map<String,Object>>)endpoint.requests.getFirst().get("messages")).containsExactly(
                Map.of("role","system","content","规则 {\"mode\":\"strict\"}"),Map.of("role","user","content","{\"question\":\"hello\"}"));
        }});
    }
    @Test void unknownUsageRemainsUnknownInSpringAiResponse() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null))) {
            var response=driver(endpoint.url(),false,6000).call(new Prompt("question"));
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isNull();
            assertThat(response.getMetadata().getUsage().getCompletionTokens()).isNull();
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isNull();
        }});
    }
    @Test void nativeProposalIsReturnedAsArgumentsAndNeverAutoExecuted() throws Exception {
        var proposal="{\"intent\":\"CHAT\",\"reply\":\"hello\",\"changes\":{}}";
        String body=json.writeValueAsString(Map.of("model","served-model","choices",List.of(Map.of("finish_reason","tool_calls","message",Map.of("tool_calls",List.of(Map.of("id","call-1","type","function","function",Map.of("name","customerAction","arguments",proposal))))))));
        withLedger(2,()->{try(var endpoint=new Endpoint(200,body)) {
            var runtime=mock(AiRuntimeConfigService.class);when(runtime.resolveText(null)).thenReturn(config(endpoint.url(),"fixture-model"));
            var result=new ExternalAiClient(runtime,"").callCustomerAction("rules","question",3,256);
            assertThat(result.text()).isEqualTo(proposal);assertThat(result.finishReason()).isEqualTo("tool_calls");
            assertThat(endpoint.calls).hasValue(1);assertThat(endpoint.requests.getFirst()).containsKeys("tools","tool_choice");
        }});
    }
    @Test void fullNativeSchemaBudgetBlocksBeforeLedgerAndHttp() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null))) {
            var model=new CustomerSpringAiChatModel(config(endpoint.url(),"fixture-model"),3,256,true,false,6000,512,(kind,payload)->{});
            assertThatThrownBy(()->model.call(new Prompt("short"))).hasMessage("CUSTOMER_WIRE_BUDGET_EXCEEDED");
            assertThat(endpoint.calls).hasValue(0);assertThat(temp.resolve("calls.jsonl")).doesNotExist();
        }});
    }
    @Test void logicalPromptLimitStillBlocksEvenWhenCompleteRequestFitsWireLimit() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null))) {
            var prompt=new Prompt("x".repeat(6001));var model=driver(endpoint.url(),false,6000);
            assertThat(CustomerSpringAiChatModel.estimate(model.requestBody(prompt))).isLessThan(CustomerSpringAiChatModel.DEFAULT_WIRE_INPUT_CODE_POINTS);
            assertThatThrownBy(()->model.call(prompt)).hasMessage("CONTEXT_BUDGET_EXCEEDED");
            assertThat(endpoint.calls).hasValue(0);assertThat(temp.resolve("calls.jsonl")).doesNotExist();
        }});
    }
    @Test void actualActionPromptAndNativeSchemaFitExplicitDefaultWireBudget() {
        var config=config("https://api.deepseek.com","deepseek-flash");
        var model=new CustomerSpringAiChatModel(config,3,4096,true,false,6000,(kind,payload)->{});
        String user="{\"message\":\"推荐一个Java离线库存管理项目\",\"state\":{\"workflowTask\":{\"id\":\"task-fixture\",\"status\":\"READY_TO_SEARCH\",\"requirementVersion\":1,\"requirements\":[]}},\"history\":[],\"summary\":\"\"}";
        var prompt=new Prompt(List.of(new SystemMessage(com.orangejd.server.customer.CustomerState.PROMPT),new UserMessage(user)));
        assertThat(CustomerSpringAiChatModel.logicalCost(prompt)).isLessThanOrEqualTo(6000);
        assertThat(CustomerSpringAiChatModel.estimate(model.requestBody(prompt))).isGreaterThan(12000)
            .isLessThanOrEqualTo(CustomerSpringAiChatModel.DEFAULT_WIRE_INPUT_CODE_POINTS);
    }
    @Test void failureCountsOneAttemptAndDriverNeverRetries() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(503,"{\"error\":\"fixture\"}")) {
            assertThatThrownBy(()->driver(endpoint.url(),false,6000).call(new Prompt("question"))).isInstanceOf(AiToolProviderException.class);
            assertThat(endpoint.calls).hasValue(1);assertThat(Files.readAllLines(temp.resolve("calls.jsonl"))).hasSize(1);
        }});
    }
    @Test void exhaustedLedgerPreventsSecondHttpAttempt() throws Exception {
        withLedger(1,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null))) {
            var model=driver(endpoint.url(),false,6000);model.call(new Prompt("question"));
            assertThatThrownBy(()->model.call(new Prompt("again"))).hasMessage("ACCEPTANCE_BUDGET_EXHAUSTED");assertThat(endpoint.calls).hasValue(1);
        }});
    }
    @Test void slowProviderIsBoundedAndDoesNotTriggerAnInternalRetry() throws Exception {
        withLedger(2,()->{try(var endpoint=new Endpoint(200,response("{}","stop",null),1800)) {
            ChatModel model=new CustomerSpringAiChatModel(config(endpoint.url(),"fixture-model"),1,256,false,false,6000,(kind,payload)->{});
            long start=System.nanoTime();
            assertThatThrownBy(()->model.call(new Prompt("question"))).isInstanceOf(ResourceAccessException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toMillis()).isLessThan(5000);
            assertThat(endpoint.calls).hasValue(1);assertThat(Files.readAllLines(temp.resolve("calls.jsonl"))).hasSize(1);
        }});
    }
    @Test void typedHistoryPreservesToolCallIdsAndRejectsOrphans() {
        var model=driver("http://127.0.0.1:1/v1",false,6000);
        var toolCall=new AssistantMessage("",Map.of(),List.of(new AssistantMessage.ToolCall("c1","function","readSource","{}")));
        var toolResult=new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("c1","readSource","source text")));
        var messages=(List<Map<String,Object>>)model.requestBody(new Prompt(List.of(new UserMessage("read"),toolCall,toolResult))).get("messages");
        assertThat(messages.getLast()).containsEntry("tool_call_id","c1").containsEntry("content","source text");
        assertThatThrownBy(()->model.requestBody(new Prompt(List.of(toolResult)))).hasMessage("INVALID_TOOL_MESSAGE_PAIR");
        assertThatThrownBy(()->model.requestBody(new Prompt(List.of(toolCall,new UserMessage("next"))))).hasMessage("INVALID_TOOL_MESSAGE_PAIR");
    }
    @Test void deepSeekThinkingAndJsonFormatsStayExplicit() {
        var config=config("https://api.deepseek.com","deepseek-flash");
        var plain=new CustomerSpringAiChatModel(config,3,256,false,false,6000,(kind,payload)->{}).requestBody(new Prompt("question"));
        assertThat(plain).containsEntry("thinking",Map.of("type","disabled")).containsEntry("response_format",Map.of("type","json_object"));
        var nativeBody=new CustomerSpringAiChatModel(config,3,256,true,true,6000,(kind,payload)->{}).requestBody(new Prompt("question"));
        assertThat(nativeBody).containsEntry("thinking",Map.of("type","enabled")).containsKey("tools").doesNotContainKeys("tool_choice","response_format");
    }
    @Test void promptOptionsCannotIncreaseStageOutputBudgetOrChangeModel() {
        var model=driver("http://127.0.0.1:1/v1",false,6000);
        assertThat(model.requestBody(new Prompt("query",ChatOptions.builder().maxTokens(2048).build()))).containsEntry("max_tokens",256);
        assertThatThrownBy(()->model.requestBody(new Prompt("query",ChatOptions.builder().model("other").build()))).hasMessage("CUSTOMER_MODEL_OVERRIDE_NOT_ALLOWED");
    }
}
