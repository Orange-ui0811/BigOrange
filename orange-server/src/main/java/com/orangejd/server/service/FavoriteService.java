package com.orangejd.server.service;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.entity.Favorite;
import com.orangejd.pojo.vo.FavoriteVO;
import com.orangejd.server.mapper.FavoriteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService implements IFavoriteService {

    @Autowired private FavoriteMapper favoriteMapper;

    public void favorite(String targetType, Long targetId) {
        Long currentId = BaseContext.getCurrentId();
        if (favoriteMapper.exists(currentId, targetType, targetId)) return; // 已收藏则忽略
        Favorite f = new Favorite();
        f.setAccountId(currentId);
        f.setTargetType(targetType);
        f.setTargetId(targetId);
        favoriteMapper.insert(f);
        // TODO Phase 5: 通知内容作者「xxx 收藏了你的作品」
    }

    public void unfavorite(String targetType, Long targetId) {
        favoriteMapper.delete(BaseContext.getCurrentId(), targetType, targetId);
    }

    public void unfavoriteById(Long id) {
        favoriteMapper.deleteById(id);
    }

    public PageResult<FavoriteVO> listMyFavorites(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = (page - 1) * size;
        int total = favoriteMapper.countByAccountId(currentId);
        List<FavoriteVO> records = favoriteMapper.selectVOByAccountId(currentId, offset, size);
        return new PageResult<>(total, records);
    }
}
