package com.orangejd.server.task;
import com.orangejd.server.service.DocumentService;
import org.slf4j.*; import org.springframework.beans.factory.ObjectProvider; import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*; import org.springframework.stereotype.Component; import org.springframework.web.client.RestTemplate;
import java.util.*;
@Component
class LeaderboardReadmeIndexer {
 private static final Logger log=LoggerFactory.getLogger(LeaderboardReadmeIndexer.class);
 private final DocumentService documentService; private final String githubToken; private final RestTemplate restTemplate; private final LeaderboardProgressReporter progress;
 LeaderboardReadmeIndexer(ObjectProvider<DocumentService> documents,@Value("${orange.leaderboard.github-token:}") String githubToken,LeaderboardProgressReporter progress){
  this.documentService=documents.getIfAvailable();this.githubToken=githubToken;this.progress=progress;
  var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(10000);this.restTemplate=new RestTemplate(factory);
 }
 boolean available(){return documentService!=null;}
 int cleanExpiredWeekly(String before){return documentService instanceof com.orangejd.server.service.impl.DocumentServiceImpl impl?impl.cleanExpiredWeekly(before):0;}
void fetchAndIndexReadme(String topicName, String period,
                                      List<Map<String, Object>> records, int topN) {
        int imported = 0;
        for (int i = 0; i < Math.min(topN, records.size()); i++) {
            String fullName = (String) records.get(i).get("fullName");
            if (fullName == null) continue;

            String sourceRef = ("new".equals(period) || "week".equals(period))
                    ? fullName + "|" + period : fullName;
            if (documentService.existsBySourceRef(sourceRef)) continue;

            String content = fetchReadme(fullName);
            if (content == null) continue;

            try {
                documentService.importFromGithub(fullName, content, period, progress.current().configVersion);
                imported++;
                progress.itemSucceeded("README", sourceRef, null);
            } catch (Exception e) {
                progress.itemFailed("README", sourceRef, e);
                log.warn("[RAG] README 入库失败 {}: {}", fullName, e.getMessage());
            }
        }
        if (imported > 0) {
            log.info("[RAG] topic={} period={} 自动采集 README {} 篇", topicName, period, imported);
        }
    }

private String fetchReadme(String fullName) {
        // 先试 master 分支
        String content = fetchUrl("https://raw.githubusercontent.com/" + fullName + "/master/README.md");
        if (content != null) return content;
        // 再试 main 分支
        return fetchUrl("https://raw.githubusercontent.com/" + fullName + "/main/README.md");
    }

private String fetchUrl(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (githubToken != null && !githubToken.isEmpty()) {
                headers.set("Authorization", "Bearer " + githubToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                return resp.getBody();
            }
        } catch (Exception ignored) {
            // README 拉取失败静默跳过
        }
        return null;
    }
}

