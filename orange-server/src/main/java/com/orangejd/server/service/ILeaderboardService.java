package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;

import java.util.List;
import java.util.Map;

public interface ILeaderboardService {
    PageResult<Map<String, Object>> getWorks(Long categoryId, String period, int page, int size);
    PageResult<Map<String, Object>> getCreators(String period, int page, int size);
    Map<String, Object> getGithubRepos(String topicName, String period, int page, int size);
}
