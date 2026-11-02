package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
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
import static com.orangejd.server.ragb.ingest.ReadmeModelReview.*;
import static com.orangejd.server.ragb.ingest.ReadmeRecommendationProjection.*;
import static org.junit.jupiter.api.Assertions.*;

/** No Spring context, business DB, chat provider or production index. */
@EnabledIfSystemProperty(named="three.arm.root",matches=".+")
class ReadmeThreeArmAcceptanceTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final String RUN="readme-three-arm-v1";
    private static final String REVISION="sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private final Path root=Path.of(System.getProperty("three.arm.root"));
    private final Path out=root.resolve("docs/rag-b/readme-filter-v1/retrieval-three-arm-v1");
    private final RagBTokenChunker tokenizer=new RagBTokenChunker();
    private JsonNode read(Path p)throws Exception{return JSON.readTree(Files.readString(p));}
    private void write(String name,Object value)throws Exception{Files.createDirectories(out);Files.writeString(out.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    private record Prepared(Source source,ReadmeKnowledgeFilter.Preview selected,Projection projection){}
    private Prepared synthetic(Source source){
        var filter=new ReadmeKnowledgeFilter();var initial=filter.preview(source);
        List<Block> decided=new ArrayList<>();List<Selection> selections=new ArrayList<>();
        for(var b:initial.blocks()){
            var reviewed=b;
            if(b.decision()==Decision.REVIEW)reviewed=new Block(b.blockId(),b.kind(),b.startUtf16(),b.endUtf16(),b.startLine(),b.endLine(),b.text(),b.sha256(),b.sectionPath(),b.headingIds(),Decision.KEEP,"ASSISTANT_SYNTHETIC_ONLY",b.ruleIds(),List.of("TECHNICAL_TEXT"));
            decided.add(reviewed);
            if(reviewed.decision()==Decision.KEEP&&!b.kind().equals("HEADING"))selections.add(new Selection(b.blockId(),Decision.KEEP,
                b.kind().equals("CODE")||b.kind().equals("INDENTED")?Kind.USAGE:Kind.TECHNICAL_TEXT,
                b.sectionPath().toString().contains("Enterprise")?Scope.ENTERPRISE:Scope.CORE,Reason.KNOWLEDGE,Applicability.IN_SCOPE,List.of(b.blockId())));
        }
        var selected=filter.reassemble(source,initial,decided);var projected=new ReadmeRecommendationProjection().project(source,selected,selections,"ASSISTANT_SYNTHETIC","THREE_ARM_FIXTURE");
        return new Prepared(source,projected.preview(),projected.projection());
    }
    private List<Map<String,Object>> spans(Source s,List<Block> blocks,int a,int b){
        List<Map<String,Object>> result=new ArrayList<>();
        for(var block:blocks){int x=Math.max(a,block.startUtf16()),y=Math.min(b,block.endUtf16());if(x<y)result.add(ref(s,block.blockId(),x,y));}
        return result;
    }
    private Map<String,Object> ref(Source s,String block,int a,int b){
        return Map.of("documentId",s.documentId(),"blockId",block,"startUtf16",a,"endUtf16",b,"text",s.text().substring(a,b),"sha256",sha256(s.text().substring(a,b)));
    }
    private List<Map<String,Object>> refs(Source s,List<ThreeArmKnowledgeChunker.Span> spans){return spans.stream().map(x->ref(s,x.blockId(),x.startUtf16(),x.endUtf16())).toList();}
    private List<int[]> intervals(List<Map<String,Object>> spans){
        List<int[]> result=new ArrayList<>();
        var sorted=spans.stream().map(s->new int[]{((Number)s.get("startUtf16")).intValue(),((Number)s.get("endUtf16")).intValue()}).sorted(Comparator.comparingInt(a->a[0])).toList();
        for(var a:sorted){if(!result.isEmpty()&&a[0]<=result.getLast()[1])result.getLast()[1]=Math.max(result.getLast()[1],a[1]);else result.add(a.clone());}
        return result;
    }
    private Map<String,Object> chunk(String variant,Source s,int index,String unit,String text,List<Map<String,Object>> refs,String expanded,List<Map<String,Object>> full){
        assertFalse(text.startsWith("Repository:"));assertTrue(tokenizer.tokens(text)<=768);
        var row=new LinkedHashMap<String,Object>();row.put("id",variant+"-"+s.documentId()+"-"+index);row.put("variant",variant);row.put("documentId",s.documentId());row.put("versionId",s.versionId());row.put("repository",s.sourceRef());row.put("scope",s.documentId().startsWith("S")?"SYNTHETIC":"REAL");row.put("unitId",unit);row.put("text",text);row.put("tokens",tokenizer.tokens(text));row.put("sourceSpans",refs);row.put("expandedText",expanded);row.put("expandedTokens",tokenizer.tokens(expanded));row.put("expandedSpans",full);return row;
    }
    @Test void exportAndCheckThreeArms()throws Exception{
        assertFalse(Files.exists(out.resolve("experiment-manifest.json")),"Never overwrite frozen inputs");
        String original=Files.readString(root.resolve("orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeKnowledgeChunker.java")).replace("\r\n","\n");
        String adapted=Files.readString(root.resolve("orange-server/src/test/java/com/orangejd/server/ragb/ingest/ThreeArmKnowledgeChunker.java")).replace("\r\n","\n");
        int a=original.indexOf("        return \"Repository: \"",original.indexOf("private String prefix(")),b=original.indexOf("\n    }",a);
        String expected=(original.substring(0,a)+"        return \"\"; // Experiment-only renderer: original body, no derived embedding prefix."+original.substring(b)).replace("ReadmeKnowledgeChunker","ThreeArmKnowledgeChunker").replace("/** Offline adapter","/** Experiment-only copy of the sealed production algorithm; only class name and prefix renderer differ.\n * Offline adapter");
        assertEquals(expected,adapted,"Experiment copy must only disable the derived prefix");
        List<Prepared> sources=new ArrayList<>();
        var pilot=root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3");
        for(var x:read(out.resolve("sources.json")).path("real")){
            var source=JSON.treeToValue(x,Source.class);var result=JSON.treeToValue(read(pilot.resolve("results/diagnostic/"+source.documentId()+".json")).path("result"),Result.class);
            ReadmeRecommendationProjection.verify(source,result.preview(),result.recommendation());sources.add(new Prepared(source,result.preview(),result.recommendation()));
        }
        for(var x:read(out.resolve("sources.json")).path("synthetic"))sources.add(synthetic(JSON.treeToValue(x,Source.class)));
        assertEquals(11,sources.size());List<Map<String,Object>> corpus=new ArrayList<>(),admission=new ArrayList<>(),units=new ArrayList<>();
        for(var prepared:sources){
            var source=prepared.source();var blocks=prepared.selected().blocks();var chunker=new ThreeArmKnowledgeChunker();
            var preview=chunker.preview(source,prepared.selected(),prepared.projection(),new ThreeArmKnowledgeChunker.Identity(null,RUN));
            List<Map<String,Object>> approved=new ArrayList<>();
            for(var u:preview.units()){
                boolean eligible=u.reasons().stream().noneMatch(reason->reason.startsWith("REQUIRED_BLOCK_NOT_ADMITTED"));
                if(eligible)approved.addAll(refs(source,u.sourceSpans()));
                units.add(Map.of("documentId",source.documentId(),"unitId",u.unitId(),"status",u.status(),"eligibleBeforeCutting",eligible,"reasons",u.reasons(),"tokens",tokenizer.tokens(u.sourceSpans().stream().map(ThreeArmKnowledgeChunker.Span::text).reduce("",(x,y)->x+"\n"+y)),"sourceSpans",refs(source,u.sourceSpans()),"chunks",u.chunkIds().size()));
            }
            var ranges=intervals(approved);List<Map<String,Object>> bRefs=new ArrayList<>();int i=0;
            for(var raw:tokenizer.split(source.text(),4096)){
                var refs=spans(source,blocks,raw.startOffset(),raw.endOffset());corpus.add(chunk("A",source,i++,"",raw.content(),refs,raw.content(),refs));
            }
            i=0;
            for(var range:ranges)for(var raw:tokenizer.split(source.text().substring(range[0],range[1]),4096)){
                var refs=spans(source,blocks,range[0]+raw.startOffset(),range[0]+raw.endOffset());bRefs.addAll(refs);
                corpus.add(chunk("B",source,i++,"",raw.content(),refs,raw.content(),refs));
            }
            assertEquals(JSON.writeValueAsString(ranges),JSON.writeValueAsString(intervals(bRefs)),"B cannot introduce or remove approved characters");
            i=0;
            for(var c:preview.chunks()){
                var e=chunker.evidence(preview,c.chunkId(),Integer.MAX_VALUE);assertEquals("READY",e.status());
                var refs=refs(source,c.sourceSpans());for(var ref:refs)assertTrue(ranges.stream().anyMatch(r->r[0]<=((Number)ref.get("startUtf16")).intValue()&&r[1]>=((Number)ref.get("endUtf16")).intValue()));
                corpus.add(chunk("C",source,i++,c.unitId(),c.embeddingText(),refs,e.text(),refs(source,e.sourceSpans())));
            }
            admission.add(Map.of("documentId",source.documentId(),"sourceSha256",sha256(source.text()),"commonApprovedIntervals",ranges,"commonApprovedUtf16Chars",ranges.stream().mapToInt(r->r[1]-r[0]).sum(),"units",preview.units().size(),"cChunks",preview.chunks().size()));
        }
        for(var c:read(out.resolve("cases.json")).path("cases"))for(var group:c.path("evidenceGroups"))for(var gold:group){
            var source=sources.stream().map(Prepared::source).filter(s->s.documentId().equals(gold.path("documentId").asText())).findFirst().orElseThrow();
            assertEquals(gold.path("text").asText(),source.text().substring(gold.path("startUtf16").asInt(),gold.path("endUtf16").asInt()));
        }
        assertEquals(52,read(out.resolve("cases.json")).path("cases").size());
        assertTrue(units.stream().anyMatch(u->((Number)u.get("chunks")).intValue()>1),"Must exercise real splitting");
        assertTrue(units.stream().anyMatch(u->u.get("reasons").toString().contains("OVERSIZED_ATOMIC_BLOCK")),"Keep oversized failures in the denominator");
        write("corpus.json",corpus);write("admission.json",admission);write("units.json",units);
        write("offline-check.json",Map.of("groups",3,"sources",11,"cases",52,"goldSourceChecks",true,"sameApprovedInputs",true,"cloudCalls",0,"indexWrites",0));
    }
    private static final class Cached extends AbstractEmbeddingModel{
        final Map<String,float[]> values=new LinkedHashMap<>();
        public int dimensions(){return 1024;}
        public float[] embed(String text){return Objects.requireNonNull(values.get(text),"Unfrozen embedding input");}
        public float[] embed(Document d){return embed(d.getText());}
        public List<float[]> embed(List<String> texts){return texts.stream().map(this::embed).toList();}
        public EmbeddingResponse call(EmbeddingRequest r){List<Embedding> result=new ArrayList<>();for(int i=0;i<r.getInstructions().size();i++)result.add(new Embedding(embed(r.getInstructions().get(i)),i));return new EmbeddingResponse(result);}
    }
    private double cosine(float[] a,float[] b){double dot=0,aa=0,bb=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return dot/Math.sqrt(aa*bb);}
    @Test @EnabledIfSystemProperty(named="three.arm.live",matches="true")
    void runFrozenVectorExperiment()throws Exception{
        assertFalse(Files.exists(out.resolve("live-started.json")));for(var it=read(out.resolve("experiment-manifest.json")).path("files").fields();it.hasNext();){var e=it.next();assertEquals(e.getValue().asText(),sha256(Files.readString(root.resolve(e.getKey()))));}
        assertEquals("/workspace/docs/rag-b/readme-filter-v1/retrieval-three-arm-v1/local-embedding-calls.jsonl",System.getProperty("orange.acceptance.ledger"));
        var corpus=read(out.resolve("corpus.json"));var cases=read(out.resolve("cases.json")).path("cases");
        LinkedHashSet<String> unique=new LinkedHashSet<>();for(var c:corpus)unique.add(c.path("text").asText());for(var c:cases)unique.add(c.path("query").asText());
        var texts=new ArrayList<>(unique);int calls=(texts.size()+7)/8;assertEquals(calls,read(out.resolve("requests.json")).path("maximumRequests").asInt());assertTrue(calls<=50);
        write("live-started.json",Map.of("runId",RUN,"maximumRequests",calls,"modelRevision",REVISION));
        var config=new RagBProperties();config.setEmbeddingProvider("OLLAMA");config.setEmbeddingRevision(REVISION);config.setOllamaModel("bge-m3:orangejd-v1");config.setOllamaBaseUrl("http://host.docker.internal:11434");
        var cached=new Cached();List<Object> embeddings=new ArrayList<>();
        try(var live=new RagBOllamaEmbeddingModel(config);var jedis=new JedisPooled("127.0.0.1",6379)){
            assertEquals("PONG",jedis.ping());
            for(int at=0;at<texts.size();at+=8){var batch=texts.subList(at,Math.min(at+8,texts.size()));long start=System.nanoTime();var vectors=live.embed(batch);
                for(int j=0;j<batch.size();j++){cached.values.put(batch.get(j),vectors.get(j));embeddings.add(Map.of("textSha256",sha256(batch.get(j)),"vector",vectors.get(j)));}
                write("embedding-progress.json",Map.of("completedTexts",cached.values.size(),"totalTexts",texts.size(),"lastBatchMs",(System.nanoTime()-start)/1e6));write("vectors.json",embeddings);
            }
            Map<String,RedisVectorStore> stores=new LinkedHashMap<>();Map<String,List<JsonNode>> docs=new LinkedHashMap<>();
            for(String variant:List.of("A","B","C")){
                String name=RUN+"-"+variant.toLowerCase(Locale.ROOT);assertThrows(RuntimeException.class,()->jedis.ftInfo(name));
                var store=RedisVectorStore.builder(jedis,cached).indexName(name).prefix("orange:rag:b:experiment:"+RUN+":"+variant+":").initializeSchema(true).vectorAlgorithm(RedisVectorStore.Algorithm.HSNW).metadataFields(RedisVectorStore.MetadataField.tag("documentId"),RedisVectorStore.MetadataField.tag("scope")).build();store.afterPropertiesSet();
                List<Document> values=new ArrayList<>();List<JsonNode> rows=new ArrayList<>();for(var c:corpus)if(c.path("variant").asText().equals(variant)){values.add(Document.builder().id(c.path("id").asText()).text(c.path("text").asText()).metadata(Map.of("documentId",c.path("documentId").asText(),"scope",c.path("scope").asText())).build());rows.add(c);}
                store.add(values);for(int retry=0;retry<80;retry++){if(jedis.ftSearch(name,new Query("*").limit(0,0)).getTotalResults()==values.size())break;Thread.sleep(50);}assertEquals(values.size(),jedis.ftSearch(name,new Query("*").limit(0,0)).getTotalResults());stores.put(variant,store);docs.put(variant,rows);
            }
            List<Object> results=new ArrayList<>();int qi=0;int exactAgreement=0;
            for(var c:cases){String query=c.path("query").asText(),scope=c.path("scope").asText();var variants=new LinkedHashMap<String,Object>();var order=new ArrayList<>(List.of("A","B","C"));Collections.rotate(order,qi++%3);
                for(var variant:order){List<Object> reps=new ArrayList<>();var eligible=docs.get(variant).stream().filter(d->d.path("scope").asText().equals(scope)).toList();
                    var exact=eligible.stream().sorted(Comparator.<JsonNode>comparingDouble(d->cosine(cached.embed(query),cached.embed(d.path("text").asText()))).reversed().thenComparing(d->d.path("id").asText())).limit(10).map(d->d.path("id").asText()).toList();
                    for(int repeat=0;repeat<3;repeat++){long start=System.nanoTime();var found=stores.get(variant).similaritySearch(SearchRequest.builder().query(query).topK(Math.max(1,docs.get(variant).size())).similarityThreshold(-1).filterExpression("scope == '"+scope+"'").build());
                        var hits=found.stream().sorted(Comparator.comparingDouble((Document d)->cosine(cached.embed(query),cached.embed(d.getText()))).reversed().thenComparing(Document::getId)).map(d->Map.<String,Object>of("id",d.getId(),"documentId",d.getMetadata().get("documentId"),"similarity",cosine(cached.embed(query),cached.embed(d.getText())))).toList();
                        assertEquals(eligible.size(),found.size(),"No vector candidates may disappear before scoring");assertEquals(exact,hits.stream().limit(10).map(h->h.get("id")).toList());exactAgreement++;
                        reps.add(Map.of("hits",hits,"retrievalMs",(System.nanoTime()-start)/1e6));
                    }variants.put(variant,reps);
                }results.add(Map.of("id",c.path("id").asText(),"variants",variants));write("retrieval-results.json",results);
            }
            write("live-completion.json",Map.of("groups",3,"cases",52,"repetitions",3,"localEmbeddingRequests",calls,"exactVectorTop10Checks",exactAgreement,"cloudCalls",0,"productionWrites",0,"mode","Redis HNSW full candidates, cosine-only stable tie order; real/synthetic scoped separately"));
        }
    }
}
