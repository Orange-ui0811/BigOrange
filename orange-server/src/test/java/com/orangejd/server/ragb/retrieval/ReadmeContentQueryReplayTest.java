package com.orangejd.server.ragb.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Model-free replay: frozen real vector/name ranks, fresh real Redis body queries. */
@EnabledIfSystemProperty(named="readme.content.root",matches=".+")
class ReadmeContentQueryReplayTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path root=Path.of(System.getProperty("readme.content.root"));
    private final Path previous=root.resolve("docs/rag-b/readme-filter-v1/ranking-v2");
    private final Path corpusPath=root.resolve("docs/rag-b/readme-filter-v1/retrieval-v1/corpus.json");
    private final Path casesPath=root.resolve("docs/rag-b/readme-filter-v1/retrieval-v1/cases.json");
    private final Path out=root.resolve("docs/rag-b/readme-filter-v1/ranking-v3");
    private JsonNode read(Path p)throws Exception{return JSON.readTree(Files.readString(p));}
    private void write(Path p,Object value)throws Exception{Files.createDirectories(p.getParent());Files.writeString(p,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    @Test void replayUsesAllTwentyEightUnchangedQuestionsWithoutAModel()throws Exception{
        assertEquals(28,read(casesPath).path("cases").size());assertEquals(28,read(previous.resolve("retrieval-results.json")).size());
        assertEquals(29,Files.readString(previous.resolve("local-embedding-calls.jsonl")).lines().filter(s->!s.isBlank()).count());
    }
    private List<String> lexical(JedisPooled jedis,String index,String prefix,List<String> terms){
        if(terms.isEmpty())return List.of();
        String alternatives=String.join(" | ",terms.stream().map(RagBQueryTokenizer::escapeQueryTerm).toList());
        return jedis.ftSearch(index,new Query("@content:("+alternatives+")").limit(0,128).dialect(2)).getDocuments().stream()
                .map(d->d.getId().substring(prefix.length())).toList();
    }
    @Test @EnabledIfSystemProperty(named="readme.content.live",matches="true")
    void compareBodyQueriesOnFrozenVectorRoutesWithoutAnyEmbeddingRequests()throws Exception{
        assertFalse(Files.exists(out.resolve("replay-results.json")),"Never overwrite completed replay evidence");
        Map<String,Map<String,Object>> corpus=new LinkedHashMap<>();
        for(var c:read(corpusPath))corpus.put(c.path("id").asText(),JSON.convertValue(c,new TypeReference<>(){}));
        Map<String,String> questions=new HashMap<>();for(var c:read(casesPath).path("cases"))questions.put(c.path("id").asText(),c.path("query").asText());
        List<Map<String,Object>> output=new ArrayList<>();
        try(var jedis=new JedisPooled("127.0.0.1",6379)){
            for(String variant:List.of("RAW_STRUCT","KNOWLEDGE_V2")){
                String index="readme-retrieval-v1-20261031-"+variant.toLowerCase(Locale.ROOT);
                String prefix="orange:rag:b:experiment:readme-retrieval-v1-20261031:"+variant+":";
                var stored=jedis.ftSearch(index,new Query("*").returnFields("content").limit(0,200));
                assertEquals(corpus.values().stream().filter(c->c.get("variant").equals(variant)).count(),stored.getTotalResults());
                for(var d:stored.getDocuments())assertEquals(corpus.get(d.getId().substring(prefix.length())).get("text"),d.getString("content"));
            }
            for(var original:read(previous.resolve("retrieval-results.json"))){
                String id=original.path("id").asText(),query=questions.get(id);Map<String,Object> variants=new LinkedHashMap<>();
                for(String variant:List.of("RAW_STRUCT","KNOWLEDGE_V2")){
                    List<Map<String,Object>> frozen=JSON.convertValue(original.path("variants").path(variant).get(0).path("candidates"),new TypeReference<>(){});
                    List<String> aliases=frozen.stream().flatMap(c->{String ref=c.get("repository").toString();return java.util.stream.Stream.of(ref,ref.substring(ref.lastIndexOf('/')+1));}).distinct().toList();
                    List<String> terms=RagBContentQuery.terms(query,aliases);
                    String index="readme-retrieval-v1-20261031-"+variant.toLowerCase(Locale.ROOT);
                    String prefix="orange:rag:b:experiment:readme-retrieval-v1-20261031:"+variant+":";
                    List<Object> reps=new ArrayList<>();
                    for(int rep=0;rep<3;rep++){
                        long start=System.nanoTime();List<String> lexical=lexical(jedis,index,prefix,terms);long lexEnd=System.nanoTime();
                        Map<String,Map<String,Object>> revised=new LinkedHashMap<>();
                        for(var candidate:frozen){var c=new LinkedHashMap<>(candidate);c.put("lexicalRank",0);revised.put(c.get("id").toString(),c);}
                        for(int position=0;position<lexical.size();position++){
                            String key=lexical.get(position);Map<String,Object> c=revised.get(key);
                            if(c==null){var doc=corpus.get(key);c=new LinkedHashMap<>();c.put("id",key);c.put("documentId",doc.get("documentId"));c.put("repository",doc.get("repository"));c.put("vectorRank",0);c.put("nameRank",0);revised.put(key,c);}
                            c.put("lexicalRank",position+1);
                        }
                        for(var c:revised.values()){
                            String ref=c.get("repository").toString(),title=ref.substring(ref.lastIndexOf('/')+1);
                            c.put("score",RagBProjectEvidenceScorer.score(query,title,ref,((Number)c.get("vectorRank")).intValue(),((Number)c.get("lexicalRank")).intValue()));
                        }
                        var hits=revised.values().stream().filter(c->((Number)c.get("score")).doubleValue()>0)
                                .sorted(Comparator.<Map<String,Object>>comparingDouble(c->((Number)c.get("score")).doubleValue()).reversed().thenComparing(c->c.get("id").toString())).limit(5).toList();
                        reps.add(Map.of("hits",hits,"candidates",List.copyOf(revised.values()),"lexicalMs",(lexEnd-start)/1e6,"fusionMs",(System.nanoTime()-lexEnd)/1e6));
                    }
                    variants.put(variant,Map.of("contentTerms",terms,"repetitions",reps));
                }
                output.add(Map.of("id",id,"variants",variants));
            }
        }
        write(out.resolve("replay-results.json"),output);
        write(out.resolve("replay-completion.json"),Map.of("cases",28,"repetitions",3,"embeddingCalls",0,"indexWrites",0,
                "method","Frozen real vector/name routes plus fresh Redis lexical queries; not new end-to-end latency measurement"));
    }
}
