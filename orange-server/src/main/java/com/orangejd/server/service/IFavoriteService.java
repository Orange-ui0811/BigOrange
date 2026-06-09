package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.vo.FavoriteVO;

public interface IFavoriteService {
    void favorite(String targetType, Long targetId);
    void unfavorite(String targetType, Long targetId);
    void unfavoriteById(Long id);
    PageResult<FavoriteVO> listMyFavorites(int page, int size);
}
