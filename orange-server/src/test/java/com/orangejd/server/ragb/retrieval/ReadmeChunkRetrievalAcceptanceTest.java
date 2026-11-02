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
import static org.junit.jupiter.api.Assertions.*;

/** Isolated five-repository experiment. No Spring context, business DB, production index or chat model. */
@EnabledIfSystemProperty(named="readme.retrieval.root",matches=".+")
class ReadmeChunkRetrievalAcceptanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REVISION = "sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private static final String RUN = "readme-retrieval-v1-20261031";
    private final Path root = Path.of(System.getProperty("readme.retrieval.root"));
    private final Path out = root.resolve("docs/rag-b/readme-filter-v1/retrieval-v1");
    private final Path pilot = root.resolve("docs/rag-b/readme-filter-v1/k3/quality-v2/v5-pilot-v3");
    private JsonNode read(Path path) throws Exception { return JSON.readTree(Files.readString(path)); }
    private void write(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");
    }
    private Map<String,Object> span(ReadmeKnowledgeFilter.Block block,int a,int b) {
        String text=block.text().substring(a-block.startUtf16(),b-block.startUtf16());
        return Map.of("blockId",block.blockId(),"startUtf16",a,"endUtf16",b,"text",text,"sha256",ReadmeKnowledgeFilter.sha256(text));
    }
    @Test void exportRawBaselineAndValidateFrozenGoldWithoutNetwork() throws Exception {
        List<Map<String,Object>> inputs=new ArrayList<>();
        for(var row:read(pilot.resolve("sources.json")).path("documents")) {
            var source=JSON.treeToValue(row.path("source"),ReadmeKnowledgeFilter.Source.class);
            var result=JSON.treeToValue(read(pilot.resolve("results/diagnostic/"+source.documentId()+".json")).path("result"),ReadmeModelReview.Result.class);
            ReadmeRecommendationProjection.verify(source,result.preview(),result.recommendation());
            var old=new RagBTokenChunker().split(source.text(),4096);
            for(var chunk:old) {
                List<Map<String,Object>> refs=new ArrayList<>();
                for(var block:result.preview().blocks()) {
                    int a=Math.max(chunk.startOffset(),block.startUtf16()),b=Math.min(chunk.endOffset(),block.endUtf16());
                    if(a<b)refs.add(span(block,a,b));
                }
                inputs.add(input("raw-"+source.documentId()+"-"+chunk.index(),"RAW_STRUCT",source,"",chunk.content(),refs,refs));
            }
            var preview=JSON.treeToValue(read(root.resolve("docs/rag-b/readme-filter-v1/chunk-v2/previews/"+source.documentId()+".json")),ReadmeKnowledgeChunker.Preview.class);
            var chunker=new ReadmeKnowledgeChunker(); chunker.verify(source,result.preview(),result.recommendation(),preview);
            for(var chunk:preview.chunks()) {
                var evidence=chunker.evidence(preview,chunk.chunkId(),8192); assertEquals("READY",evidence.status());
                List<Map<String,Object>> refs=JSON.convertValue(chunk.sourceSpans(),new TypeReference<>(){});
                List<Map<String,Object>> expanded=JSON.convertValue(evidence.sourceSpans(),new TypeReference<>(){});
                inputs.add(input("knowledge-"+chunk.chunkId(),"KNOWLEDGE_V2",source,chunk.unitId(),chunk.embeddingText(),refs,expanded));
            }
        }
        assertEquals(28,inputs.stream().filter(r->r.get("variant").equals("KNOWLEDGE_V2")).count());
        assertEquals(5,read(out.resolve("cases.json")).path("sources").size());
        var cases=read(out.resolve("cases.json")).path("cases"); assertEquals(28,cases.size());
        for(var c:cases) {
            if(!c.path("answerable").asBoolean()) {assertEquals(0,c.path("evidenceGroups").size());continue;}
            assertTrue(c.path("evidenceGroups").size()>0);
            for(var group:c.path("evidenceGroups")) for(var gold:group) {
                String id=gold.path("blockId").asText();
                assertTrue(inputs.stream().filter(r->r.get("variant").equals("KNOWLEDGE_V2"))
                        .flatMap(r->((List<?>)r.get("expandedSpans")).stream())
                        .anyMatch(r->((Map<?,?>)r).get("blockId").equals(id)),"Gold must be available in approved knowledge: "+c.path("id"));
            }
        }
        write(out.resolve("corpus.json"),inputs);
        write(out.resolve("offline-check.json"),Map.of("cases",28,"positiveCases",22,"insufficientCases",6,"newChunks",28,
                "rawChunks",inputs.stream().filter(r->r.get("variant").equals("RAW_STRUCT")).count(),"providerCalls",0,"indexWrites",0));
    }
    private Map<String,Object> input(String id,String variant,ReadmeKnowledgeFilter.Source s,String unit,String text,
                                     List<Map<String,Object>> spans,List<Map<String,Object>> expanded) {
        return Map.of("id",id,"variant",variant,"documentId",s.documentId(),"versionId",s.versionId(),"repository",s.sourceRef(),
                "unitId",unit,"text",text,"sourceSpans",spans,"expandedSpans",expanded);
    }
    private record Index(RedisVectorStore store,String name,String prefix) {}
    private static class CachedModel extends AbstractEmbeddingModel {
        private final Map<String,float[]> vectors=new HashMap<>();
        void put(String text,float[] vector) { vectors.put(text,vector); }
        @Override public int dimensions(){return 1024;}
        @Override public float[] embed(String text){return Objects.requireNonNull(vectors.get(text),"Unprepared embedding text");}
        @Override public float[] embed(Document document){return embed(document.getText());}
        @Override public List<float[]> embed(List<String> texts){return texts.stream().map(this::embed).toList();}
        @Override public EmbeddingResponse call(EmbeddingRequest request){
            List<Embedding> values=new ArrayList<>();for(int i=0;i<request.getInstructions().size();i++)values.add(new Embedding(embed(request.getInstructions().get(i)),i));
            return new EmbeddingResponse(values);
        }
    }
    private Index index(JedisPooled jedis,CachedModel model,String variant) {
        String name=RUN+"-"+variant.toLowerCase(Locale.ROOT),prefix="orange:rag:b:experiment:"+RUN+":"+variant+":";
        assertThrows(RuntimeException.class,()->jedis.ftInfo(name),"Refuse to overwrite an existing experiment index");
        var store=RedisVectorStore.builder(jedis,model).indexName(name).prefix(prefix).initializeSchema(true)
                .vectorAlgorithm(RedisVectorStore.Algorithm.HSNW).metadataFields(
                        RedisVectorStore.MetadataField.text("title"),RedisVectorStore.MetadataField.text("sourceRef"),
                        RedisVectorStore.MetadataField.tag("documentId"),RedisVectorStore.MetadataField.tag("variant"),
                        RedisVectorStore.MetadataField.tag("unitId")).build();
        store.afterPropertiesSet(); return new Index(store,name,prefix);
    }
    private List<Document> lexical(Index index,String query,List<String> fields,Map<String,Document> documents) {
        var terms=RagBQueryTokenizer.tokenize(query);if(terms.isEmpty())return List.of();
        String alternatives=String.join(" | ",terms.stream().map(RagBQueryTokenizer::escapeQueryTerm).toList());
        String expression="("+String.join(" | ",fields.stream().map(f->"@"+f+":("+alternatives+")").toList())+")";
        return index.store().getJedis().ftSearch(index.name(),new Query(expression).limit(0,128).dialect(2)).getDocuments().stream()
                .map(d->Objects.requireNonNull(documents.get(d.getId().substring(index.prefix().length())))).toList();
    }
    private void ranks(Map<String,int[]> ranks,List<Document> docs,int channel) {
        for(int i=0;i<docs.size();i++)ranks.computeIfAbsent(docs.get(i).getId(),k->new int[3])[channel]=i+1;
    }
    private List<Map<String,Object>> search(Index index,String query,Map<String,Document> documents,int repetition) {
        long started=System.nanoTime();
        List<Document> vector=index.store().similaritySearch(SearchRequest.builder().query(query).topK(128).similarityThreshold(0).build());
        long vectorDone=System.nanoTime(); var lexical=lexical(index,query,List.of("content"),documents);long lexicalDone=System.nanoTime();
        var names=lexical(index,query,List.of("title","sourceRef"),documents);long namesDone=System.nanoTime();
        Map<String,int[]> ranks=new LinkedHashMap<>();ranks(ranks,vector,0);ranks(ranks,lexical,1);ranks(ranks,names,2);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var e:ranks.entrySet()) {
            var doc=documents.get(e.getKey());var m=doc.getMetadata();int[] r=e.getValue();
            double score=RagBFusionScorer.score(query,m.get("title").toString(),m.get("sourceRef").toString(),r[0],r[1],r[2]);
            Map<String,Object> hit=new LinkedHashMap<>();hit.put("id",e.getKey());hit.put("documentId",m.get("documentId"));hit.put("repository",m.get("sourceRef"));
            hit.put("score",score);hit.put("vectorRank",r[0]);hit.put("lexicalRank",r[1]);hit.put("nameRank",r[2]);
            hit.put("vectorSimilarity",vector.stream().filter(d->d.getId().equals(e.getKey())).map(Document::getScore).findFirst().orElse(-1.0));
            rows.add(hit);
        }
        rows.sort(Comparator.<Map<String,Object>>comparingDouble(m->((Number)m.get("score")).doubleValue()).reversed().thenComparing(m->m.get("id").toString()));
        var top=rows.stream().limit(5).toList();long finished=System.nanoTime();
        Map<String,Object> timing=Map.of("repetition",repetition,"vectorMs",(vectorDone-started)/1e6,"lexicalMs",(lexicalDone-vectorDone)/1e6,
                "nameMs",(namesDone-lexicalDone)/1e6,"fusionMs",(finished-namesDone)/1e6,"retrievalMs",(finished-started)/1e6);
        List<Map<String,Object>> result=new ArrayList<>();result.add(Map.of("timing",timing,"hits",top)); return result;
    }
    @Test @EnabledIfSystemProperty(named="readme.retrieval.live",matches="true")
    void compareTwoIsolatedIndexesUsingOnlyPinnedLocalEmbeddings() throws Exception {
        var manifest=read(out.resolve("experiment-manifest.json"));
        for(var it=manifest.path("files").fields();it.hasNext();) {var field=it.next();
            assertEquals(field.getValue().asText(),ReadmeKnowledgeFilter.sha256(Files.readString(root.resolve(field.getKey()))));}
        assertEquals("/workspace/docs/rag-b/readme-filter-v1/retrieval-v1/local-embedding-calls.jsonl",System.getProperty("orange.acceptance.ledger"));
        assertEquals("50",System.getProperty("orange.acceptance.limit"));
        assertFalse(Files.exists(out.resolve("live-started.json")),"Live run is one-shot; never silently repeat partial requests");
        write(out.resolve("live-started.json"),Map.of("runId",RUN,"provider","LOCAL_OLLAMA","maximumInferenceRequests",50));
        var config=new RagBProperties();config.setEmbeddingProvider("OLLAMA");config.setEmbeddingRevision(REVISION);
        config.setOllamaBaseUrl("http://host.docker.internal:11434");config.setOllamaModel("bge-m3:orangejd-v1");
        List<Map<String,Object>> corpus=JSON.convertValue(read(out.resolve("corpus.json")),new TypeReference<>(){});
        CachedModel cached=new CachedModel(); List<String> unique=corpus.stream().map(r->r.get("text").toString()).distinct().toList();
        assertTrue(1+(unique.size()+7)/8+28<=50,"Frozen plan exceeds local inference cap");
        List<Map<String,Object>> ingest=new ArrayList<>();List<Map<String,Object>> queryResults=new ArrayList<>();
        try(var live=new RagBOllamaEmbeddingModel(config);var jedis=new JedisPooled("127.0.0.1",6379)) {
            assertEquals("PONG",jedis.ping());
            long warm=System.nanoTime();live.embed(List.of("OrangeJD isolated retrieval warmup"));
            write(out.resolve("warmup.json"),Map.of("ms",(System.nanoTime()-warm)/1e6));
            for(int i=0;i<unique.size();i+=8) {
                var batch=unique.subList(i,Math.min(i+8,unique.size()));long started=System.nanoTime();var values=live.embed(batch);
                for(int j=0;j<batch.size();j++)cached.put(batch.get(j),values.get(j));
                ingest.add(Map.of("offset",i,"inputs",batch.size(),"ms",(System.nanoTime()-started)/1e6));write(out.resolve("ingestion-embedding.json"),ingest);
            }
            Map<String,Index> indexes=new LinkedHashMap<>();Map<String,Map<String,Document>> documents=new HashMap<>();
            for(String variant:List.of("RAW_STRUCT","KNOWLEDGE_V2")) {
                Index index=index(jedis,cached,variant);indexes.put(variant,index);Map<String,Document> docs=new LinkedHashMap<>();
                for(var row:corpus)if(row.get("variant").equals(variant)) {
                    String repo=row.get("repository").toString();String title=repo.substring(repo.lastIndexOf('/')+1);
                    Document doc=Document.builder().id(row.get("id").toString()).text(row.get("text").toString()).metadata(Map.of(
                            "title",title,"sourceRef",repo,"documentId",row.get("documentId"),"variant",variant,"unitId",row.get("unitId").toString().isBlank()?"RAW":row.get("unitId"))).build();
                    docs.put(doc.getId(),doc);
                }
                index.store().add(new ArrayList<>(docs.values()));documents.put(variant,docs);
                for(int attempt=0;attempt<60;attempt++) {
                    if(jedis.ftSearch(index.name(),new Query("*").limit(0,0)).getTotalResults()==docs.size())break;
                    Thread.sleep(50);
                }
                assertEquals(docs.size(),jedis.ftSearch(index.name(),new Query("*").limit(0,0)).getTotalResults());
            }
            for(var c:read(out.resolve("cases.json")).path("cases")) {
                String query=c.path("query").asText();long started=System.nanoTime();float[] vector=live.embed(List.of(query)).getFirst();
                double embeddingMs=(System.nanoTime()-started)/1e6;cached.put(query,vector);
                Map<String,Object> result=new LinkedHashMap<>();result.put("id",c.path("id").asText());result.put("query",query);result.put("embeddingMs",embeddingMs);
                Map<String,Object> variants=new LinkedHashMap<>();
                // Alternate order to avoid always warming Redis with the same variant first.
                var order=queryResults.size()%2==0?List.of("RAW_STRUCT","KNOWLEDGE_V2"):List.of("KNOWLEDGE_V2","RAW_STRUCT");
                for(String variant:order) {List<Object> reps=new ArrayList<>();for(int repetition=0;repetition<3;repetition++)
                    reps.add(search(indexes.get(variant),query,documents.get(variant),repetition).getFirst());variants.put(variant,reps);}
                result.put("variants",variants);queryResults.add(result);write(out.resolve("retrieval-results.json"),queryResults);
            }
            List<Map<String,Object>> indexStats=new ArrayList<>();
            for(var entry:indexes.entrySet())indexStats.add(Map.of("variant",entry.getKey(),"indexName",entry.getValue().name(),
                    "prefix",entry.getValue().prefix(),"documents",jedis.ftSearch(entry.getValue().name(),new Query("*").limit(0,0)).getTotalResults()));
            write(out.resolve("live-completion.json"),Map.ofEntries(Map.entry("cases",28),Map.entry("repetitions",3),Map.entry("queryEmbeddingCalls",28),Map.entry("modelRevision",REVISION),
                    Map.entry("indexes",indexStats),Map.entry("chatModelCalls",0),Map.entry("cloudEmbeddingCalls",0),Map.entry("productionIndexWrites",0),Map.entry("businessDatabaseWrites",0),
                    Map.entry("reranker","NATIVE_RRF_ONLY_NO_CROSS_ENCODER"),Map.entry("candidateLimit",128),Map.entry("topK",5),Map.entry("similarityThreshold",0)));
        }
    }
}
