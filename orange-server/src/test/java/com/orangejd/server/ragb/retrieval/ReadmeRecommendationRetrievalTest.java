package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Query-only benchmark of the unchanged, frozen A/B/C indexes. No Spring context or chat call. */
@EnabledIfSystemProperty(named="recommendation.root",matches=".+")
class ReadmeRecommendationRetrievalTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final String OLD_RUN="readme-three-arm-v1-cached";
    private static final String REVISION="sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private final Path root=Path.of(System.getProperty("recommendation.root"));
    private final Path old=root.resolve("docs/rag-b/readme-filter-v1/retrieval-three-arm-v1");
    private final Path out=root.resolve("docs/rag-b/readme-filter-v1/recommendation-retrieval-v1");
    private JsonNode read(Path p)throws Exception{return JSON.readTree(Files.readString(p));}
    private void write(String name,Object value)throws Exception{Files.createDirectories(out);Files.writeString(out.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    private String hash(String value){return ReadmeKnowledgeFilter.sha256(value);}
    private static class Cached extends AbstractEmbeddingModel {
        final Map<String,float[]> values=new HashMap<>();
        public int dimensions(){return 1024;}
        public float[] embed(String text){return Objects.requireNonNull(values.get(text),"Missing frozen vector");}
        public float[] embed(Document doc){return embed(doc.getText());}
        public List<float[]> embed(List<String> texts){return texts.stream().map(this::embed).toList();}
        public EmbeddingResponse call(EmbeddingRequest request){List<Embedding> values=new ArrayList<>();for(int i=0;i<request.getInstructions().size();i++)values.add(new Embedding(embed(request.getInstructions().get(i)),i));return new EmbeddingResponse(values);}
    }
    private double cosine(float[] a,float[] b){double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return dot/Math.sqrt(aa*bb);}
    @Test void queryFrozenThreeArmIndexes()throws Exception{
        assertFalse(Files.exists(out.resolve("live-started.json")),"Never repeat or overwrite a partial acquisition");
        for(var it=read(out.resolve("manifest.json")).path("files").fields();it.hasNext();){var e=it.next();assertEquals(e.getValue().asText(),hash(Files.readString(root.resolve(e.getKey()))));}
        var requests=read(out.resolve("requests.json"));int maximum=requests.path("maximumRequests").asInt();assertEquals(Integer.toString(maximum),System.getProperty("orange.acceptance.limit"));
        assertEquals("/workspace/docs/rag-b/readme-filter-v1/recommendation-retrieval-v1/local-embedding-calls.jsonl",System.getProperty("orange.acceptance.ledger"));
        Map<String,float[]> existing=new HashMap<>();
        for(var row:read(old.resolve("vectors.json"))){float[] values=new float[1024];assertEquals(1024,row.path("vector").size());for(int i=0;i<1024;i++)values[i]=row.path("vector").get(i).floatValue();existing.put(row.path("textSha256").asText(),values);}
        var corpus=read(old.resolve("corpus.json"));var cases=read(out.resolve("cases.json")).path("cases");assertEquals(26,cases.size());
        var cached=new Cached();for(var c:corpus)cached.values.put(c.path("text").asText(),Objects.requireNonNull(existing.get(hash(c.path("text").asText()))));
        List<String> missing=new ArrayList<>();for(var q:requests.path("newQueryTexts"))missing.add(q.asText());assertEquals((missing.size()+7)/8,maximum);
        for(var c:cases){String query=c.path("query").asText();if(existing.containsKey(hash(query)))cached.values.put(query,existing.get(hash(query)));}
        write("live-started.json",Map.of("maximumLocalRequests",maximum,"newQueryTexts",missing.size(),"modelRevision",REVISION,"oldVectorCacheSha256",hash(Files.readString(old.resolve("vectors.json")))));
        var config=new RagBProperties();config.setEmbeddingProvider("OLLAMA");config.setEmbeddingRevision(REVISION);config.setOllamaModel("bge-m3:orangejd-v1");config.setOllamaBaseUrl("http://host.docker.internal:11434");
        List<Object> vectors=new ArrayList<>();
        try(var live=new RagBOllamaEmbeddingModel(config)){
            for(int at=0;at<missing.size();at+=8){var batch=missing.subList(at,Math.min(at+8,missing.size()));var acquired=live.embed(batch);for(int i=0;i<batch.size();i++){cached.values.put(batch.get(i),acquired.get(i));vectors.add(Map.of("textSha256",hash(batch.get(i)),"vector",acquired.get(i)));}write("query-vectors.json",vectors);}
        }
        Map<String,RedisVectorStore> stores=new LinkedHashMap<>();Map<String,List<JsonNode>> docs=new LinkedHashMap<>();
        try(var jedis=new JedisPooled("127.0.0.1",6379)){
            assertEquals("PONG",jedis.ping());
            for(String variant:List.of("A","B","C")){
                String name=OLD_RUN+"-"+variant.toLowerCase(Locale.ROOT),prefix="orange:rag:b:experiment:"+OLD_RUN+":"+variant+":";
                List<JsonNode> expected=new ArrayList<>();for(var c:corpus)if(c.path("variant").asText().equals(variant)&&c.path("scope").asText().equals("REAL"))expected.add(c);
                var stored=jedis.ftSearch(name,new Query("@scope:{REAL}").returnFields("content").limit(0,1000));assertEquals(expected.size(),stored.getTotalResults());
                Map<String,String> expectedTexts=new HashMap<>();for(var c:expected)expectedTexts.put(c.path("id").asText(),c.path("text").asText());
                for(var d:stored.getDocuments())assertEquals(expectedTexts.get(d.getId().substring(prefix.length())),d.getString("content"));
                var store=RedisVectorStore.builder(jedis,cached).indexName(name).prefix(prefix).initializeSchema(false).vectorAlgorithm(RedisVectorStore.Algorithm.HSNW).metadataFields(RedisVectorStore.MetadataField.tag("documentId"),RedisVectorStore.MetadataField.tag("scope")).build();store.afterPropertiesSet();stores.put(variant,store);docs.put(variant,expected);
            }
            List<Object> output=new ArrayList<>();int position=0,checks=0;
            for(var c:cases){String query=c.path("query").asText();assertNotNull(cached.values.get(query));List<String> order=new ArrayList<>(List.of("A","B","C"));Collections.rotate(order,position++%3);Map<String,Object> variants=new LinkedHashMap<>();
                for(String variant:order){var expected=docs.get(variant);var exact=expected.stream().sorted(Comparator.<JsonNode>comparingDouble(d->cosine(cached.embed(query),cached.embed(d.path("text").asText()))).reversed().thenComparing(d->d.path("id").asText())).limit(10).map(d->d.path("id").asText()).toList();List<Object> reps=new ArrayList<>();
                    for(int rep=0;rep<3;rep++){long start=System.nanoTime();var found=stores.get(variant).similaritySearch(SearchRequest.builder().query(query).topK(expected.size()).similarityThreshold(0).filterExpression("scope == 'REAL'").build());assertEquals(expected.size(),found.size());
                        var hits=found.stream().sorted(Comparator.comparingDouble((Document d)->cosine(cached.embed(query),cached.embed(d.getText()))).reversed().thenComparing(Document::getId)).map(d->Map.<String,Object>of("id",d.getId(),"documentId",d.getMetadata().get("documentId"),"similarity",cosine(cached.embed(query),cached.embed(d.getText())))).toList();
                        assertEquals(exact,hits.stream().limit(10).map(h->h.get("id")).toList());checks++;reps.add(Map.of("hits",hits,"retrievalMs",(System.nanoTime()-start)/1e6));
                    }variants.put(variant,reps);
                }output.add(Map.of("id",c.path("id").asText(),"variants",variants));write("retrieval-results.json",output);
            }
            write("live-completion.json",Map.of("cases",26,"groups",3,"repetitions",3,"exactTop10Checks",checks,"localQueryRequests",maximum,"indexWrites",0,"cloudCalls",0,"agentDecisionCalls",0,"mode","Read-only existing HNSW indexes, full real candidates, cosine-only stable order"));
        }
    }
}
