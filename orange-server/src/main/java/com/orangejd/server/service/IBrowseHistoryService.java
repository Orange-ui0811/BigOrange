package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.vo.HistoryVO;

public interface IBrowseHistoryService {
    void record(String targetType, Long targetId);
    PageResult<HistoryVO> getHistory(int page, int size);
}
