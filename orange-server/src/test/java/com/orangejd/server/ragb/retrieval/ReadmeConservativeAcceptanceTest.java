package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.databind.*;
import com.orangejd.server.ragb.config.RagBProperties;
import com.orangejd.server.ragb.ingest.*;
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
import static com.orangejd.server.ragb.ingest.ReadmeKnowledgeFilter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Saved classifications only; separate experimental indexes, no Spring/business lifecycle. */
@EnabledIfSystemProperty(named="conservative.root",matches=".+")
class ReadmeConservativeAcceptanceTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final String RUN="readme-conservative-v1";
    private static final String REVISION="sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private final Path root=Path.of(System.getProperty("conservative.root"));
    private final Path old=root.resolve("docs/rag-b/readme-filter-v1/retrieval-three-arm-v1");
    private final Path prior=root.resolve("docs/rag-b/readme-filter-v1/recommendation-retrieval-v1");
    private final Path out=root.resolve("docs/rag-b/readme-filter-v1/conservative-policy-v1");
    private final RagBTokenChunker tokenizer=new RagBTokenChunker();
    private JsonNode read(Path p)throws Exception{return JSON.readTree(Files.readString(p));}
    private void write(String name,Object value)throws Exception{Path path=out.resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    private Map<String,Object> ref(Source s,String id,int a,int b){return Map.of("documentId",s.documentId(),"blockId",id,"startUtf16",a,"endUtf16",b,"text",s.text().substring(a,b),"sha256",sha256(s.text().substring(a,b)));}
    private Map<String,Object> row(String v,Source s,int index,String unit,String text,List<Map<String,Object>> spans,String full,List<Map<String,Object>> expanded){
        assertTrue(tokenizer.tokens(text)<=768);assertFalse(text.startsWith("Repository:"));
        var r=new LinkedHashMap<String,Object>();r.put("id",v+"-"+s.documentId()+"-"+index);r.put("variant",v);r.put("documentId",s.documentId());r.put("scope","REAL");r.put("unitId",unit);r.put("text",text);r.put("tokens",tokenizer.tokens(text));r.put("sourceSpans",spans);r.put("expandedText",full);r.put("expandedTokens",tokenizer.tokens(full));r.put("expandedSpans",expanded);return r;
    }
    @Test void exportPreparation()throws Exception{
        assertFalse(Files.exists(out.resolve("manifest.json")),"Preserve frozen preparation");
        List<Object> corpus=new ArrayList<>(),admission=new ArrayList<>();
        for(var c:read(old.resolve("corpus.json")))if(c.path("variant").asText().equals("A")&&c.path("scope").asText().equals("REAL"))corpus.add(c);
        var pilot=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3");
        for(var x:read(old.resolve("sources.json")).path("real")){
            var s=JSON.treeToValue(x,Source.class);
            var classified=JSON.treeToValue(read(pilot.resolve("results/diagnostic/"+s.documentId()+".json")).path("result"),ReadmeModelReview.Result.class);
            var prepared=new ReadmeKnowledgePreparation().prepare(s,classified,RUN);
            write("previews/"+s.documentId()+".json",prepared);
            var output=prepared.admission().output();var bodyOnly=new ThreeArmKnowledgeChunker();
            var knowledge=bodyOnly.preview(s,output.preview(),output.projection(),new ThreeArmKnowledgeChunker.Identity(null,RUN));
            List<Map<String,Object>> approved=new ArrayList<>();
            for(var u:knowledge.units())if(u.reasons().stream().noneMatch(r->r.startsWith("REQUIRED_BLOCK_NOT_ADMITTED")))
                for(var sp:u.sourceSpans())approved.add(ref(s,sp.blockId(),sp.startUtf16(),sp.endUtf16()));
            int n=0;for(var c:prepared.structural().chunks()){
                var refs=c.sourceSpans().stream().map(sp->ref(s,sp.blockId(),sp.startUtf16(),sp.endUtf16())).toList();
                assertTrue(c.sourceSpans().stream().anyMatch(sp->!sp.blockKind().equals("HEADING")));
                corpus.add(row("B",s,n++,"",c.text(),refs,c.text(),refs));
            }
            n=0;for(var c:knowledge.chunks()){
                var e=bodyOnly.evidence(knowledge,c.chunkId(),Integer.MAX_VALUE);assertEquals("READY",e.status());
                var refs=c.sourceSpans().stream().map(sp->ref(s,sp.blockId(),sp.startUtf16(),sp.endUtf16())).toList();
                var full=e.sourceSpans().stream().map(sp->ref(s,sp.blockId(),sp.startUtf16(),sp.endUtf16())).toList();
                corpus.add(row("C",s,n++,c.unitId(),c.embeddingText(),refs,e.text(),full));
            }
            // The same source REVIEW blocks must remain excluded even when they hurt benchmark scores.
            Set<String> pending=new HashSet<>();classified.preview().blocks().stream().filter(b->b.decision()==Decision.REVIEW).forEach(b->pending.add(b.blockId()));
            assertTrue(output.projection().knowledgeCandidates().stream().flatMap(c->c.evidence().stream()).noneMatch(a->pending.contains(a.blockId())));
            admission.add(Map.of("documentId",s.documentId(),"approvedSpans",approved,"conditions",prepared.admission().conditions(),"changes",prepared.admission().changes(),"remainingReview",output.projection().reviewBlockIds(),"structuralDeferred",prepared.structural().deferred()));
        }
        write("corpus.json",corpus);write("admission.json",admission);
        write("preparation-check.json",Map.of("sourceReviewNeverPromoted",true,"pureHeadingVectors",0,"cloudCalls",0,"productionWrites",0));
    }
    private static final class Cached extends AbstractEmbeddingModel{
        final Map<String,float[]> values=new HashMap<>();public int dimensions(){return 1024;}
        public float[] embed(String text){return Objects.requireNonNull(values.get(text),"Missing frozen vector");}
        public float[] embed(Document d){return embed(d.getText());}public List<float[]> embed(List<String> t){return t.stream().map(this::embed).toList();}
        public EmbeddingResponse call(EmbeddingRequest r){List<Embedding> result=new ArrayList<>();for(int i=0;i<r.getInstructions().size();i++)result.add(new Embedding(embed(r.getInstructions().get(i)),i));return new EmbeddingResponse(result);}
    }
    private double cosine(float[] a,float[] b){double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return dot/Math.sqrt(aa*bb);}
    @Test void runFrozenRetrieval()throws Exception{
        assertFalse(Files.exists(out.resolve("live-started.json")),"Never overwrite acquisition");
        for(var it=read(out.resolve("manifest.json")).path("files").fields();it.hasNext();){var e=it.next();assertEquals(e.getValue().asText(),sha256(Files.readString(root.resolve(e.getKey()))));}
        var requests=read(out.resolve("requests.json"));int maximum=requests.path("maximumRequests").asInt();
        assertEquals(Integer.toString(maximum),System.getProperty("orange.acceptance.limit"));
        assertEquals(out.resolve("local-embedding-calls.jsonl").toString(),System.getProperty("orange.acceptance.ledger"));
        Map<String,float[]> vectors=new HashMap<>();for(Path file:List.of(old.resolve("vectors.json"),prior.resolve("query-vectors.json")))for(var row:read(file)){
            float[] v=new float[1024];assertEquals(v.length,row.path("vector").size());for(int j=0;j<v.length;j++){v[j]=row.path("vector").get(j).floatValue();assertTrue(Float.isFinite(v[j]));}vectors.put(row.path("textSha256").asText(),v);
        }
        var corpus=read(out.resolve("corpus.json"));var cases=read(prior.resolve("cases.json")).path("cases");
        List<String> missing=new ArrayList<>();for(var t:requests.path("newDocumentTexts")){missing.add(t.asText());assertFalse(vectors.containsKey(sha256(t.asText())));}
        assertEquals((missing.size()+7)/8,maximum);write("live-started.json",Map.of("maximumLocalRequests",maximum,"newDocumentTexts",missing.size(),"queryEmbeddingRequests",0,"cloudCalls",0));
        var config=new RagBProperties();config.setEmbeddingProvider("OLLAMA");config.setEmbeddingRevision(REVISION);config.setOllamaModel("bge-m3:orangejd-v1");config.setOllamaBaseUrl("http://host.docker.internal:11434");
        List<Object> acquired=new ArrayList<>();try(var live=new RagBOllamaEmbeddingModel(config)){
            for(int at=0;at<missing.size();at+=8){var batch=missing.subList(at,Math.min(at+8,missing.size()));var result=live.embed(batch);for(int j=0;j<batch.size();j++){vectors.put(sha256(batch.get(j)),result.get(j));acquired.add(Map.of("textSha256",sha256(batch.get(j)),"vector",result.get(j)));}write("new-vectors.json",acquired);}
        }
        var cached=new Cached();for(var c:corpus)cached.values.put(c.path("text").asText(),Objects.requireNonNull(vectors.get(sha256(c.path("text").asText()))));
        for(var c:cases)cached.values.put(c.path("query").asText(),Objects.requireNonNull(vectors.get(sha256(c.path("query").asText()))));
        try(var jedis=new JedisPooled("127.0.0.1",6379)){
            assertEquals("PONG",jedis.ping());Map<String,RedisVectorStore> stores=new LinkedHashMap<>();Map<String,List<JsonNode>> docs=new LinkedHashMap<>();
            for(String variant:List.of("A","B","C")){
                String name=RUN+"-"+variant.toLowerCase(Locale.ROOT);assertThrows(RuntimeException.class,()->jedis.ftInfo(name));
                var store=RedisVectorStore.builder(jedis,cached).indexName(name).prefix("orange:rag:b:experiment:"+RUN+":"+variant+":").initializeSchema(true).vectorAlgorithm(RedisVectorStore.Algorithm.HSNW).metadataFields(RedisVectorStore.MetadataField.tag("documentId"),RedisVectorStore.MetadataField.tag("scope")).build();store.afterPropertiesSet();
                List<JsonNode> rows=new ArrayList<>();List<Document> values=new ArrayList<>();for(var c:corpus)if(c.path("variant").asText().equals(variant)){rows.add(c);values.add(Document.builder().id(c.path("id").asText()).text(c.path("text").asText()).metadata(Map.of("documentId",c.path("documentId").asText(),"scope","REAL")).build());}
                assertFalse(values.isEmpty());store.add(values);for(int n=0;n<80;n++){if(jedis.ftSearch(name,new Query("*").limit(0,0)).getTotalResults()==values.size())break;Thread.sleep(50);}assertEquals(values.size(),jedis.ftSearch(name,new Query("*").limit(0,0)).getTotalResults());stores.put(variant,store);docs.put(variant,rows);
            }
            List<Object> results=new ArrayList<>();int position=0,checks=0;
            for(var c:cases){String query=c.path("query").asText();var order=new ArrayList<>(List.of("A","B","C"));Collections.rotate(order,position++%3);Map<String,Object> variants=new LinkedHashMap<>();
                for(String v:order){var rows=docs.get(v);var exact=rows.stream().sorted(Comparator.<JsonNode>comparingDouble(d->cosine(cached.embed(query),cached.embed(d.path("text").asText()))).reversed().thenComparing(d->d.path("id").asText())).limit(10).map(d->d.path("id").asText()).toList();List<Object> repeats=new ArrayList<>();
                    for(int rep=0;rep<3;rep++){long start=System.nanoTime();var found=stores.get(v).similaritySearch(SearchRequest.builder().query(query).topK(rows.size()).similarityThreshold(0).build());assertEquals(rows.size(),found.size());
                        var hits=found.stream().sorted(Comparator.comparingDouble((Document d)->cosine(cached.embed(query),cached.embed(d.getText()))).reversed().thenComparing(Document::getId)).map(d->Map.<String,Object>of("id",d.getId(),"documentId",d.getMetadata().get("documentId"),"similarity",cosine(cached.embed(query),cached.embed(d.getText())))).toList();
                        assertEquals(exact,hits.stream().limit(10).map(h->h.get("id")).toList());checks++;repeats.add(Map.of("hits",hits,"retrievalMs",(System.nanoTime()-start)/1e6));
                    }variants.put(v,repeats);
                }results.add(Map.of("id",c.path("id").asText(),"variants",variants));write("retrieval-results.json",results);
            }write("live-completion.json",Map.of("cases",26,"exactTop10Checks",checks,"repetitions",3,"localRequests",maximum,"queryEmbeddingRequests",0,"cloudCalls",0,"productionWrites",0));
        }
    }
}
