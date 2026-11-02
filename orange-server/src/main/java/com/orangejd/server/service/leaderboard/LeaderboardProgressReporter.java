package com.orangejd.server.service.leaderboard;
import com.orangejd.server.foundation.ingestion.IngestionRunService;
import org.springframework.stereotype.Component;
@Component
class LeaderboardProgressReporter {
 private final IngestionRunService runs; private final ThreadLocal<LeaderboardRunContext> current=new ThreadLocal<>();
 LeaderboardProgressReporter(IngestionRunService runs){this.runs=runs;}
 LeaderboardRunContext begin(Long id,String version){var value=new LeaderboardRunContext(id,version);current.set(value);return value;}
 void end(){current.remove();}
 LeaderboardRunContext current(){var value=current.get();return value==null?new LeaderboardRunContext(null,null):value;}
 void checkCancelled(){var value=current();if(value.runId!=null&&runs.cancelled(value.runId))throw new IllegalStateException("抓取任务已取消");}
 void itemSucceeded(String stage,String key,Object result){var value=current();value.success++;runs.item(value.runId,stage,key,"SUCCEEDED",result,null);publish(value);}
 void itemFailed(String stage,String key,Exception error){var value=current();value.failed++;runs.item(value.runId,stage,key,"FAILED",null,shortMessage(error.getMessage()));publish(value);}
 private void publish(LeaderboardRunContext value){runs.progress(value.runId,Math.max(value.total,value.success+value.failed),value.success,value.failed,value.skipped,value.failed>0?"存在失败项，可在管理端查看并重试":null);}
 private String shortMessage(String value){if(value==null||value.isBlank())return "no details";return value.length()<=500?value:value.substring(0,500);}
}
