package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.foundation.external.AiAcceptanceBudget;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.config.RagBRerankProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

/** One bounded request; complete-response validation and all-or-nothing fallback to original order. */
@Component
public class RagBReranker {
    public record Trace(String status,String model,int candidateCount,int rerankedCount,double durationMs,String fallbackReason){
        public static Trace disabled(int n){return new Trace("DISABLED","",n,0,0,"");}
    }
    public record Result(List<RagBSearchHit> hits,Trace trace){}
    private final RagBRerankProperties config;
    private final AiEmbeddingProperties embedding;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Semaphore slots;
    public RagBReranker(RagBProperties properties,AiEmbeddingProperties embedding,ObjectMapper json){
        this.config=properties.getRerank();config.validate();this.embedding=embedding;this.json=json;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(2000,config.getTimeoutMillis())))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.slots=new Semaphore(config.getMaxConcurrentRequests());
    }
    public Result rerank(String query,List<RagBSearchHit> candidates){
        var original=List.copyOf(candidates);long started=System.nanoTime();
        if(!config.isEnabled())return new Result(original,Trace.disabled(original.size()));
        if(original.size()<2)return unchanged(original,"SKIPPED",original.isEmpty()?"EMPTY":"SINGLE_CANDIDATE",started);
        if(Thread.currentThread().isInterrupted())return unchanged(original,"FALLBACK","INTERRUPTED",started);
        URI base;String key;
        try{
            base=URI.create(blank(config.getEndpoint())?embedding.getEndpoint():config.getEndpoint());
            boolean local=Set.of("localhost","127.0.0.1","::1").contains(Objects.toString(base.getHost(),""));
            if(base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null
                    ||!("https".equals(base.getScheme())||"http".equals(base.getScheme())&&local))
                return unchanged(original,"FALLBACK","INVALID_ENDPOINT",started);
            key=config.getApiKey();
            if(blank(key)){
                if(!sameOrigin(base,URI.create(embedding.getEndpoint())))return unchanged(original,"FALLBACK","MISSING_DEDICATED_KEY",started);
                key=embedding.getApiKey();
            }
            if(blank(key))return unchanged(original,"FALLBACK","MISSING_KEY",started);
        }catch(RuntimeException ex){return unchanged(original,"FALLBACK","INVALID_CONFIGURATION",started);}
        int count=Math.min(config.getMaxCandidates(),original.size());var input=original.subList(0,count);
        List<String> texts=input.stream().map(RagBReranker::document).toList();
        long characters=(long)query.length()+texts.stream().mapToLong(String::length).sum();
        if(characters>config.getMaxRequestCharacters())return unchanged(original,"FALLBACK","REQUEST_BUDGET",started);
        if(!slots.tryAcquire())return unchanged(original,"FALLBACK","BUSY",started);
        try{
            String body=json.writeValueAsString(Map.of("model",config.getModel(),"query",query,"documents",texts,"top_n",count,"return_documents",false));
            var request=HttpRequest.newBuilder(URI.create(base.toString().replaceAll("/+$","")+"/rerank"))
                    .timeout(Duration.ofMillis(config.getTimeoutMillis())).header("Authorization","Bearer "+key)
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            AiAcceptanceBudget.beforeRequest("GITEE_RERANK");
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)return unchanged(original,"FALLBACK","HTTP_"+response.statusCode(),started);
            JsonNode rows=json.readTree(response.body());rows=rows.has("results")?rows.path("results"):rows.path("data");
            if(!rows.isArray()||rows.size()!=count)return unchanged(original,"FALLBACK","INVALID_RESPONSE",started);
            Map<Integer,Double> scores=new HashMap<>();
            for(var row:rows){
                var index=row.path("index");var value=row.has("relevance_score")?row.path("relevance_score"):row.path("score");
                if(!index.isIntegralNumber()||!index.canConvertToInt()||index.intValue()<0||index.intValue()>=count||scores.containsKey(index.intValue())
                        ||!value.isNumber()||!Double.isFinite(value.doubleValue()))return unchanged(original,"FALLBACK","INVALID_RESPONSE",started);
                scores.put(index.intValue(),value.doubleValue());
            }
            // Stable ties retain the original fused ranking. Unscored tail remains available for diversity.
            var order=new ArrayList<Integer>();for(int i=0;i<count;i++)order.add(i);
            order.sort(Comparator.<Integer>comparingDouble(scores::get).reversed());
            List<RagBSearchHit> ranked=new ArrayList<>();for(int i:order)ranked.add(input.get(i).withRerankScore(scores.get(i)));
            ranked.addAll(original.subList(count,original.size()));
            return new Result(List.copyOf(ranked),new Trace("APPLIED",config.getModel(),original.size(),count,elapsed(started),""));
        }catch(HttpTimeoutException ex){return unchanged(original,"FALLBACK","TIMEOUT",started);}
        catch(InterruptedException ex){Thread.currentThread().interrupt();return unchanged(original,"FALLBACK","INTERRUPTED",started);}
        catch(Exception ex){return unchanged(original,"FALLBACK","REQUEST_FAILED",started);}
        finally{slots.release();}
    }
    private Result unchanged(List<RagBSearchHit> hits,String status,String reason,long started){
        return new Result(hits,new Trace(status,config.getModel(),hits.size(),0,elapsed(started),reason));
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static boolean sameOrigin(URI a,URI b){
        return Objects.equals(a.getScheme(),b.getScheme())&&Objects.equals(a.getHost(),b.getHost())&&port(a)==port(b);
    }
    private static int port(URI a){return a.getPort()<0?("https".equals(a.getScheme())?443:80):a.getPort();}
    private static double elapsed(long start){return (System.nanoTime()-start)/1e6;}
    private static String document(RagBSearchHit h){
        return "Title: "+Objects.toString(h.title(),"")+"\nSource: "+Objects.toString(h.sourceRef(),"")+"\nSection: "+Objects.toString(h.sectionPath(),"")+"\n\n"+Objects.toString(h.content(),"");
    }
    @jakarta.annotation.PreDestroy public void close(){http.close();}
}
