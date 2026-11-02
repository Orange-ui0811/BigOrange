package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
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

/** Reuses frozen corpus and real Redis vectors; re-ranks full routes without touching previous outputs. */
@EnabledIfSystemProperty(named="readme.ranking.root",matches=".+")
class ReadmeEvidenceRankingAcceptanceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String REVISION="sha256:7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private final Path root=Path.of(System.getProperty("readme.ranking.root"));
    private final Path previous=root.resolve("docs/rag-b/readme-filter-v1/retrieval-v1");
    private final Path out=root.resolve("docs/rag-b/readme-filter-v1/ranking-v2");
    private JsonNode read(Path path)throws Exception{return JSON.readTree(Files.readString(path));}
    private void write(Path path,Object value)throws Exception{
        Files.createDirectories(path.getParent());Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");
    }
    private List<Map<String,Object>> corpus()throws Exception{return JSON.convertValue(read(previous.resolve("corpus.json")),new TypeReference<>(){});}
    private Map<String,Document> documents(String variant)throws Exception{
        Map<String,Document> docs=new LinkedHashMap<>();
        for(var row:corpus())if(row.get("variant").equals(variant)){
            String ref=row.get("repository").toString();
            var doc=Document.builder().id(row.get("id").toString()).text(row.get("text").toString()).metadata(Map.of(
                    "title",ref.substring(ref.lastIndexOf('/')+1),"sourceRef",ref,"documentId",row.get("documentId"))).build();docs.put(doc.getId(),doc);
        }
        return docs;
    }
    @Test void freezesTheSameQueriesAndKnowledgeAndValidatesBothScoringPolicies()throws Exception{
        assertEquals(28,read(previous.resolve("cases.json")).path("cases").size());
        assertEquals(28,documents("KNOWLEDGE_V2").size());assertEquals(86,documents("RAW_STRUCT").size());
        assertEquals(44,Files.readString(previous.resolve("local-embedding-calls.jsonl")).lines().filter(s->!s.isBlank()).count());
        double content=RagBProjectEvidenceScorer.score("Flowise源码构建","Flowise","owner/Flowise",1,1);
        assertTrue(content>RagBProjectEvidenceScorer.score("Flowise源码构建","Flowise","owner/Flowise",9,8));
        assertEquals(0,RagBProjectEvidenceScorer.score("Flowise源码构建","Flowise","owner/Flowise",0,0));
    }
    private static class QueryVectorModel extends AbstractEmbeddingModel{
        private final Map<String,float[]> vectors=new HashMap<>();
        @Override public int dimensions(){return 1024;}
        @Override public float[] embed(String text){return Objects.requireNonNull(vectors.get(text),"Query vector not prepared");}
        @Override public float[] embed(Document document){return embed(document.getText());}
        @Override public List<float[]> embed(List<String> texts){return texts.stream().map(this::embed).toList();}
        @Override public EmbeddingResponse call(EmbeddingRequest req){throw new UnsupportedOperationException("No ingestion in ranking experiment");}
    }
    private record Index(RedisVectorStore store,String name,String prefix,Map<String,Document> documents){}
    private Index existing(JedisPooled jedis,QueryVectorModel model,String variant)throws Exception{
        String name="readme-retrieval-v1-20261031-"+variant.toLowerCase(Locale.ROOT);
        String prefix="orange:rag:b:experiment:readme-retrieval-v1-20261031:"+variant+":";
        Map<String,Document> docs=documents(variant);
        var found=jedis.ftSearch(name,new Query("*").returnFields("content").limit(0,200));assertEquals(docs.size(),found.getTotalResults());
        for(var hit:found.getDocuments())assertEquals(docs.get(hit.getId().substring(prefix.length())).getText(),hit.getString("content"),"Stored content differs from frozen corpus");
        var store=RedisVectorStore.builder(jedis,model).indexName(name).prefix(prefix).initializeSchema(false).build();
        store.afterPropertiesSet();return new Index(store,name,prefix,docs);
    }
    private List<Document> lexical(Index index,String query,List<String> fields){
        var terms=RagBQueryTokenizer.tokenize(query);if(terms.isEmpty())return List.of();
        String alternatives=String.join(" | ",terms.stream().map(RagBQueryTokenizer::escapeQueryTerm).toList());
        String expression="("+String.join(" | ",fields.stream().map(f->"@"+f+":("+alternatives+")").toList())+")";
        return index.store().getJedis().ftSearch(index.name(),new Query(expression).limit(0,128).dialect(2)).getDocuments().stream()
                .map(d->index.documents().get(d.getId().substring(index.prefix().length()))).toList();
    }
    private void rank(Map<String,int[]> ranks,List<Document> docs,int channel){for(int i=0;i<docs.size();i++)ranks.computeIfAbsent(docs.get(i).getId(),k->new int[3])[channel]=i+1;}
    private Map<String,Object> retrieve(Index index,String query,int repetition){
        long started=System.nanoTime();var vector=index.store().similaritySearch(SearchRequest.builder().query(query).topK(128).similarityThreshold(0).build());
        long vectorEnd=System.nanoTime();var text=lexical(index,query,List.of("content"));long textEnd=System.nanoTime();
        var names=lexical(index,query,List.of("title","sourceRef"));long nameEnd=System.nanoTime();
        Map<String,int[]> ranks=new LinkedHashMap<>();rank(ranks,vector,0);rank(ranks,text,1);rank(ranks,names,2);
        List<Map<String,Object>> candidates=new ArrayList<>();Set<String> named=new HashSet<>();
        for(var entry:ranks.entrySet()){
            var doc=index.documents().get(entry.getKey());var meta=doc.getMetadata();int[] r=entry.getValue();
            String title=meta.get("title").toString(),ref=meta.get("sourceRef").toString();
            if(RagBFusionScorer.exactNameMatch(query,title,ref))named.add(meta.get("documentId").toString());
            Map<String,Object> hit=new LinkedHashMap<>();hit.put("id",doc.getId());hit.put("documentId",meta.get("documentId"));hit.put("repository",ref);
            hit.put("vectorRank",r[0]);hit.put("lexicalRank",r[1]);hit.put("nameRank",r[2]);
            hit.put("legacyScore",RagBFusionScorer.score(query,title,ref,r[0],r[1],r[2]));
            hit.put("contentScore",RagBProjectEvidenceScorer.score(query,title,ref,r[0],r[1]));
            hit.put("vectorSimilarity",vector.stream().filter(d->d.getId().equals(doc.getId())).map(Document::getScore).findFirst().orElse(-1.0));candidates.add(hit);
        }
        Map<String,Object> policies=new LinkedHashMap<>();
        for(String policy:List.of("LEGACY_RRF","PROJECT_CONTENT","SCOPED_CONTENT")){
            String score=policy.equals("LEGACY_RRF")?"legacyScore":"contentScore";long fusionStart=System.nanoTime();
            var hits=candidates.stream().filter(h->((Number)h.get(score)).doubleValue()>0)
                    .filter(h->!policy.equals("SCOPED_CONTENT")||named.isEmpty()||named.contains(h.get("documentId").toString()))
                    .sorted(Comparator.<Map<String,Object>>comparingDouble(h->((Number)h.get(score)).doubleValue()).reversed().thenComparing(h->h.get("id").toString())).limit(5).toList();
            policies.put(policy,Map.of("hits",hits,"fusionMs",(System.nanoTime()-fusionStart)/1e6));
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("repetition",repetition);result.put("candidates",candidates);result.put("policies",policies);
        result.put("timing",Map.of("vectorMs",(vectorEnd-started)/1e6,"lexicalMs",(textEnd-vectorEnd)/1e6,"nameMs",(nameEnd-textEnd)/1e6,
                "sharedRetrievalMs",(nameEnd-started)/1e6));return result;
    }
    @Test @EnabledIfSystemProperty(named="readme.ranking.live",matches="true")
    void runAllPoliciesOnIdenticalFullRoutesWithoutReembeddingDocuments()throws Exception{
        for(var it=read(out.resolve("experiment-manifest.json")).path("files").fields();it.hasNext();){var f=it.next();
            String source=Files.readString(root.resolve(f.getKey()));
            assertEquals(f.getValue().asText(),ReadmeKnowledgeFilter.sha256(source));}
        assertFalse(Files.exists(out.resolve("live-started.json")),"One-shot acquisition, no hidden repeats");
        assertEquals("30",System.getProperty("orange.acceptance.limit"));
        assertEquals("/workspace/docs/rag-b/readme-filter-v1/ranking-v2/local-embedding-calls.jsonl",System.getProperty("orange.acceptance.ledger"));
        write(out.resolve("live-started.json"),Map.of("maximumLocalInferenceRequests",30,"documentReembedding",false));
        var config=new RagBProperties();config.setEmbeddingProvider("OLLAMA");config.setEmbeddingRevision(REVISION);
        config.setOllamaBaseUrl("http://host.docker.internal:11434");config.setOllamaModel("bge-m3:orangejd-v1");
        QueryVectorModel model=new QueryVectorModel();List<Map<String,Object>> queries=new ArrayList<>(),vectors=new ArrayList<>();
        try(var live=new RagBOllamaEmbeddingModel(config);var jedis=new JedisPooled("127.0.0.1",6379)){
            Map<String,Index> indexes=new LinkedHashMap<>();for(String variant:List.of("RAW_STRUCT","KNOWLEDGE_V2"))indexes.put(variant,existing(jedis,model,variant));
            live.embed(List.of("OrangeJD evidence ranking warmup"));
            for(var c:read(previous.resolve("cases.json")).path("cases")){
                String query=c.path("query").asText();long start=System.nanoTime();float[] vector=live.embed(List.of(query)).getFirst();double ms=(System.nanoTime()-start)/1e6;
                model.vectors.put(query,vector);vectors.add(Map.of("id",c.path("id").asText(),"querySha256",ReadmeKnowledgeFilter.sha256(query),"vector",vector));
                write(out.resolve("query-vectors.json"),Map.of("modelRevision",REVISION,"vectors",vectors));
                Map<String,Object> result=new LinkedHashMap<>();result.put("id",c.path("id").asText());result.put("embeddingMs",ms);
                Map<String,Object> variants=new LinkedHashMap<>();
                for(String variant:queries.size()%2==0?List.of("RAW_STRUCT","KNOWLEDGE_V2"):List.of("KNOWLEDGE_V2","RAW_STRUCT")){
                    List<Object> repetitions=new ArrayList<>();for(int rep=0;rep<3;rep++)repetitions.add(retrieve(indexes.get(variant),query,rep));variants.put(variant,repetitions);
                }
                result.put("variants",variants);queries.add(result);write(out.resolve("retrieval-results.json"),queries);
            }
            write(out.resolve("live-completion.json"),Map.of("cases",28,"policies",3,"repetitions",3,"documentEmbeddingCalls",0,
                    "localQueryEmbeddingCalls",28,"cloudRequests",0,"productionIndexWrites",0,"indexWrites",0));
        }
    }
}
