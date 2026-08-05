package com.orangejd.server.task;
final class LeaderboardRunContext {
 final Long runId; final String configVersion; int total; int success; int failed; int skipped;
 LeaderboardRunContext(Long runId,String configVersion){this.runId=runId;this.configVersion=configVersion;}
}

