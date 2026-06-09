package com.orangejd.server.service;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.CommentCreateDTO;
import com.orangejd.pojo.entity.Comment;
import com.orangejd.pojo.vo.CommentVO;
import com.orangejd.server.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService implements ICommentService {

    @Autowired private CommentMapper commentMapper;

    public void create(CommentCreateDTO dto) {
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) throw new BaseException(MessageConstant.COMMENT_EMPTY);
        if (dto.getContent().length() > 1024) throw new BaseException(MessageConstant.COMMENT_TOO_LONG);
        Comment c = new Comment();
        c.setAccountId(BaseContext.getCurrentId());
        c.setTargetType(dto.getTargetType());
        c.setTargetId(dto.getTargetId());
        c.setContent(dto.getContent().trim());
        commentMapper.insert(c);
        // TODO Phase 5: 通知内容作者「xxx 评论了你的作品/需求」
    }

    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    public PageResult<CommentVO> listByTarget(String targetType, Long targetId, int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * size;
        int total = commentMapper.countByTarget(targetType, targetId);
        List<CommentVO> records = commentMapper.selectByTarget(targetType, targetId, offset, size);
        return new PageResult<>(total, records);
    }

    public PageResult<CommentVO> listMyComments(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * size;
        Long currentId = BaseContext.getCurrentId();
        int total = commentMapper.countByAccountId(currentId);
        List<CommentVO> records = commentMapper.selectByAccountId(currentId, offset, size);
        return new PageResult<>(total, records);
    }

    public PageResult<CommentVO> listCommentsOnMyContent(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * size;
        Long currentId = BaseContext.getCurrentId();
        int total = commentMapper.countOnMyContent(currentId);
        List<CommentVO> records = commentMapper.selectOnMyContent(currentId, offset, size);
        return new PageResult<>(total, records);
    }

    public PageResult<CommentVO> listAll(int page, int size) {
        size = com.orangejd.common.util.PageUtil.capSize(size);
        int offset = (page - 1) * size;
        int total = commentMapper.countAll();
        List<CommentVO> records = commentMapper.selectAll(offset, size);
        return new PageResult<>(total, records);
    }
}
