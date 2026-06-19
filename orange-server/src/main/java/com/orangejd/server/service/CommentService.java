package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.CommentCreateDTO;
import com.orangejd.pojo.vo.CommentVO;

public interface CommentService {
    void create(CommentCreateDTO dto);
    void delete(Long id);
    PageResult<CommentVO> listByTarget(String targetType, Long targetId, int page, int size);
    PageResult<CommentVO> listMyComments(int page, int size);
    PageResult<CommentVO> listCommentsOnMyContent(int page, int size);
    PageResult<CommentVO> listAll(int page, int size);
}
