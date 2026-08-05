package com.orangejd.server.service;
import com.fasterxml.jackson.databind.ObjectMapper; import com.orangejd.server.mapper.AgentActionMapper; import jakarta.annotation.PostConstruct; import jakarta.annotation.PreDestroy; import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class AgentActionService {
 private final AgentActionRepository repository; private final AgentActionPreparationService preparation; private final AgentActionExecutionService execution;
 public AgentActionService(AgentActionMapper mapper,LaunchCheckService launchCheckService,QuotaService quotaService,PricingService pricingService,OpenSourceContributionService openSourceService,TrendAnalysisService trendAnalysisService,TrendProfileAnalysisService trendProfileAnalysisService,PlanGenerationService planGenerationService,NotificationService notificationService,LeaderboardService leaderboardService,ObjectMapper objectMapper){
  var support=new AgentActionSupport(objectMapper);this.repository=new AgentActionRepository(mapper,support);
  this.preparation=new AgentActionPreparationService(quotaService,pricingService,launchCheckService,openSourceService,trendProfileAnalysisService,planGenerationService,notificationService,leaderboardService,repository,support);
  this.execution=new AgentActionExecutionService(mapper,launchCheckService,quotaService,openSourceService,trendAnalysisService,trendProfileAnalysisService,planGenerationService,repository,support);
 }
 @PostConstruct public void recoverInterruptedTasks(){repository.recoverInterruptedTasks();}
 public Map<String,Object> prepare(Long accountId,Long credentialId,String scopes,Map<String,Object> body){return preparation.prepare(accountId,credentialId,scopes,body);}
 public Map<String,Object> confirm(Long accountId,Long credentialId,String taskId){return execution.confirm(accountId,credentialId,taskId);}
 public Map<String,Object> status(Long accountId,Long credentialId,String taskId){return repository.status(accountId,credentialId,taskId);}
 public Map<String,Object> cancel(Long accountId,Long credentialId,String taskId){return repository.cancel(accountId,credentialId,taskId);}
 public List<Map<String,Object>> history(Long accountId,int limit){return repository.history(accountId,limit);}
 public void deleteHistory(Long accountId,String taskId){repository.deleteHistory(accountId,taskId);}
 @PreDestroy public void shutdown(){execution.shutdown();}
}
