package com.orangejd.server.service;
import java.util.*;
import org.springframework.ai.vectorstore.*;
import org.springframework.ai.vectorstore.redis.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.orangejd.server.config.AiEmbeddingProperties;
import com.orangejd.server.ai.GiteeAiEmbeddingModel;
import org.springframework.web.client.RestTemplate;
import redis.clients.jedis.JedisPooled;
import com.fasterxml.jackson.databind.ObjectMapper;
public class RagAudit {
 public static void main(String[] args) throws Exception {
  var p=new AiEmbeddingProperties(); p.setEndpoint(System.getenv("AI_EMBEDDING_ENDPOINT")); p.setApiKey(System.getenv("AI_EMBEDDING_KEY")); p.setModel(System.getenv("AI_EMBEDDING_MODEL"));
  var rt=new RestTemplate(); var f=new org.springframework.http.client.SimpleClientHttpRequestFactory(); f.setConnectTimeout(30000);f.setReadTimeout(30000);rt.setRequestFactory(f);
  var vs=RedisVectorStore.builder(new JedisPooled("redis",6379),new GiteeAiEmbeddingModel(rt,p)).indexName("default-index").prefix("default:").initializeSchema(false).build();
  var beans=new DefaultListableBeanFactory();beans.registerSingleton("vectorStore",vs);
  var b=new TrendContextBuilder(beans.getBeanProvider(VectorStore.class),beans.getBeanProvider(PersonaPromptContextService.class));
  var mapper=new ObjectMapper();var results=new ArrayList<Object>();
  var questions=List.of("有没有适合 Java 初学者、两周内可以完成的项目？","RAG-DEMO-JAVA-001","什么是 RAG？","如何重置 OrangeJD 的登录密码？");
  for(var q:questions) for(int round=1;round<=(q.equals(questions.get(0))?3:1);round++){
   long t=System.nanoTime();var raw=vs.similaritySearch(SearchRequest.builder().query(q).topK(3).similarityThreshold(0.0).build());long ms=(System.nanoTime()-t)/1000000;
   var docs=TrendRedisMetadata.restore(vs,"default:",raw);var out=new LinkedHashMap<String,Object>();out.put("question",q);out.put("round",round);out.put("elapsedMs",ms);
   var rows=new ArrayList<Object>();for(int i=0;i<docs.size();i++){var d=docs.get(i);var row=new LinkedHashMap<String,Object>();row.put("id",d.getId());row.put("text",d.getText());row.put("metadata",d.getMetadata());row.put("rawMetadata",raw.get(i).getMetadata());row.put("score",d.getScore());rows.add(row);}out.put("documents",rows);results.add(out);
  }
  var in=new TrendAnalysisInput(List.of("Java"),"学习并完成项目","初学者",List.of("Java"),"两周","","适合 Java 初学者、两周内可以完成的项目","","","",true,false,true,false);
  var retrieved=b.retrieve(in);results.add(Map.of("kind","productionRetrieval","trace",retrieved.trace(),"text",retrieved.text()));
  var faq=new TrendAnalysisInput(List.of(),"知识问答","初学者",List.of(),"","","什么是 RAG？","","","",true,false,false,false);
  results.add(Map.of("kind","faqWithoutTopics","trace",b.retrieve(faq).trace()));
  System.out.println("AUDIT_JSON="+mapper.writeValueAsString(results));
 }
}
