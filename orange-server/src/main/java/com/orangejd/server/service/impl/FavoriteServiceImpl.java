package com.orangejd.server.service.impl;

import com.orangejd.server.service.FavoriteService;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.Favorite;
import com.orangejd.pojo.vo.FavoriteVO;
import com.orangejd.server.mapper.FavoriteMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import com.orangejd.server.service.NotificationService;
import com.orangejd.server.service.ContentAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired private FavoriteMapper favoriteMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private RequirementMapper requirementMapper;
    @Autowired private NotificationService notificationService;
    @Autowired private ContentAccessService contentAccessService;

    public void favorite(String targetType, Long targetId) {
        Long currentId = BaseContext.getCurrentId();
        Long ownerId = contentAccessService.requireAccessibleOwner(targetType, targetId, currentId);
        if (favoriteMapper.exists(currentId, targetType, targetId)) return;
        Favorite f = new Favorite();
        f.setAccountId(currentId);
        f.setTargetType(targetType);
        f.setTargetId(targetId);
        favoriteMapper.insert(f);
        // 通知内容作者
        String title = "";
        if ("WORK".equals(targetType)) {
            com.orangejd.pojo.entity.Work w = workMapper.selectById(targetId);
            if (w != null) title = w.getTitle();
        } else if ("REQUIREMENT".equals(targetType)) {
            com.orangejd.pojo.entity.Requirement r = requirementMapper.selectById(targetId);
            if (r != null) title = r.getTitle();
        }
        if (!currentId.equals(ownerId)) {
            notificationService.send(ownerId, "FAVORITE", "有人收藏了你的" + ("WORK".equals(targetType) ? "作品《" : "需求《") + title + "》", targetId);
        }
    }

    public void unfavorite(String targetType, Long targetId) {
        favoriteMapper.delete(BaseContext.getCurrentId(), targetType, targetId);
    }

    public void unfavoriteById(Long id) {
        favoriteMapper.deleteById(id, BaseContext.getCurrentId());
    }

    public PageResult<FavoriteVO> listMyFavorites(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = com.orangejd.common.util.PageUtil.offset(page, size);
        int total = favoriteMapper.countByAccountId(currentId);
        List<FavoriteVO> records = favoriteMapper.selectVOByAccountId(currentId, offset, size);
        return new PageResult<>(total, records);
    }
}
