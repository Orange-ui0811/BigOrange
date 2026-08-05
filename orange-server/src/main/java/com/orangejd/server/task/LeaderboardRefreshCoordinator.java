package com.orangejd.server.task;
import com.orangejd.pojo.entity.LeaderboardSnapshot; import com.orangejd.server.mapper.LeaderboardSnapshotMapper; import org.slf4j.*; import org.springframework.stereotype.Component;
import java.time.*; import java.time.format.*; import java.time.temporal.*; import java.util.concurrent.atomic.AtomicBoolean;
@Component
class LeaderboardRefreshCoordinator {
 static final String[] TOPICS={"creative-coding","game-development","WebGL","Canvas","Web-Audio","ai-agents","RAG","self-hosted","home-assistant","awesome-self-hosted","ESP32","raspberry-pi","home-automation","3d-printing","other"};
 static final String ALL_TOPICS="__ALL__"; private static final Logger log=LoggerFactory.getLogger(LeaderboardRefreshCoordinator.class);
 private final LeaderboardSnapshotMapper snapshots; private final LeaderboardSnapshotRefresher refresher; private final LeaderboardTranslationPipeline translations; private final LeaderboardReadmeIndexer readmes; private final LeaderboardProgressReporter progress; private final AtomicBoolean running=new AtomicBoolean();
 LeaderboardRefreshCoordinator(LeaderboardSnapshotMapper snapshots,LeaderboardSnapshotRefresher refresher,LeaderboardTranslationPipeline translations,LeaderboardReadmeIndexer readmes,LeaderboardProgressReporter progress){this.snapshots=snapshots;this.refresher=refresher;this.translations=translations;this.readmes=readmes;this.progress=progress;}
 void refreshAll(Long runId,String version){
  if(!running.compareAndSet(false,true)){log.info("LeaderboardRefreshTask: 已有刷新任务运行，跳过重复触发");return;}
  var context=progress.begin(runId,version);
  try{var today=LocalDate.now();var monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));if(monday.equals(today))monday=monday.minusWeeks(1);context.total=(TOPICS.length+1)*3;
   for(String topic:TOPICS){progress.checkCancelled();refresher.refreshAndSave(topic,"all",today,null);pause();refresher.refreshAndSave(topic,"new",today,monday.format(DateTimeFormatter.ISO_LOCAL_DATE));pause();refresher.refreshWeekStars(topic,today);pause();}
   refresher.refreshAndSave(ALL_TOPICS,"all",today,null);pause();refresher.refreshAndSave(ALL_TOPICS,"new",today,monday.format(DateTimeFormatter.ISO_LOCAL_DATE));pause();refresher.refreshWeekStars(ALL_TOPICS,today);
   translations.translateAllToday(today);String before=today.minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);log.info("榜单刷新完成，清理 {} 条快照、{} 篇 README",snapshots.deleteBefore(before),readmes.cleanExpiredWeekly(before));
  }finally{running.set(false);progress.end();}
 }
 boolean snapshotsMissingThisWeek(){var week=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));return before(latest("all"),week)||before(latest("week"),week)||before(latest("new"),week);}
 private LeaderboardSnapshot latest(String period){var value=snapshots.selectLatest("github",ALL_TOPICS,period);return value!=null?value:snapshots.selectLatest("github",null,period);}
 private boolean before(LeaderboardSnapshot value,LocalDate week){return value==null||value.getSnapshotDate()==null||value.getSnapshotDate().isBefore(week);}
 private void pause(){try{Thread.sleep(6000);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("榜单刷新被中断",error);}}
}

