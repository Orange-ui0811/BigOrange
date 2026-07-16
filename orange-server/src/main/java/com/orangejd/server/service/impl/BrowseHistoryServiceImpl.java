package com.orangejd.server.service.impl;

import com.orangejd.server.service.BrowseHistoryService;
import com.orangejd.server.service.ContentAccessService;
import com.orangejd.common.context.BaseContext;
import com.orangejd.pojo.entity.BrowseHistory;
import com.orangejd.server.mapper.BrowseHistoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private static final int MAX_HISTORY_PER_USER = 200;

    @Autowired private BrowseHistoryMapper browseHistoryMapper;
    @Autowired private ContentAccessService contentAccessService;

    public void record(String targetType, Long targetId) {
        Long accountId = BaseContext.getCurrentId();
        contentAccessService.requireAccessibleOwner(targetType, targetId, accountId);
        BrowseHistory h = new BrowseHistory();
        h.setAccountId(accountId);
        h.setTargetType(targetType);
        h.setTargetId(targetId);
        browseHistoryMapper.insert(h);
        // 每人最多保留 200 条，超出删旧
        browseHistoryMapper.deleteOldest(accountId, MAX_HISTORY_PER_USER);
    }

    public List<Long> getHistoryIds(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        return browseHistoryMapper.selectByAccountId(BaseContext.getCurrentId(), offset, size)
                .stream().map(BrowseHistory::getTargetId).collect(Collectors.toList());
    }

    public com.orangejd.common.result.PageResult<com.orangejd.pojo.vo.HistoryVO> getHistory(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        List<com.orangejd.pojo.vo.HistoryVO> records = browseHistoryMapper.selectVOByAccountId(BaseContext.getCurrentId(), offset, size);
        return new com.orangejd.common.result.PageResult<>((long) records.size(), records);
    }
}
