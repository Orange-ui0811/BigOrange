package com.orangejd.server.service.impl;

import com.orangejd.server.service.ICommentService;
import com.orangejd.server.service.INotificationService;
import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.CommentCreateDTO;
import com.orangejd.pojo.entity.Comment;
import com.orangejd.pojo.vo.CommentVO;
import com.orangejd.server.mapper.CommentMapper;
import com.orangejd.server.mapper.RequirementMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements ICommentService {

    @Autowired private CommentMapper commentMapper;
    @Autowired private WorkMapper workMapper;
    @Autowired private RequirementMapper requirementMapper;
    @Autowired private INotificationService notificationService;

    public void create(CommentCreateDTO dto) {
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) throw new BaseException(MessageConstant.COMMENT_EMPTY);
        if (dto.getContent().length() > 1024) throw new BaseException(MessageConstant.COMMENT_TOO_LONG);
        Long currentId = BaseContext.getCurrentId();
        Comment c = new Comment();
        c.setAccountId(currentId);
        c.setTargetType(dto.getTargetType());
        c.setTargetId(dto.getTargetId());
        c.setContent(dto.getContent().trim());
        commentMapper.insert(c);
        // 通知内容作者
        Long ownerId = null; String title = "";
        if ("WORK".equals(dto.getTargetType())) {
            com.orangejd.pojo.entity.Work w = workMapper.selectById(dto.getTargetId());
            if (w != null && !currentId.equals(w.getOwnerId())) { ownerId = w.getOwnerId(); title = w.getTitle(); }
        } else if ("REQUIREMENT".equals(dto.getTargetType())) {
            com.orangejd.pojo.entity.Requirement r = requirementMapper.selectById(dto.getTargetId());
            if (r != null && !currentId.equals(r.getPublisherId())) { ownerId = r.getPublisherId(); title = r.getTitle(); }
        }
        if (ownerId != null) {
            notificationService.send(ownerId, "COMMENT", "有人评论了你的" + ("WORK".equals(dto.getTargetType()) ? "作品《" : "需求《") + title + "》", dto.getTargetId());
        }
    }

    public void delete(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) return;
        Long currentId = BaseContext.getCurrentId();
        // 评论作者可删
        if (currentId.equals(comment.getAccountId())) { commentMapper.deleteById(id); return; }
        // 作品主人可删
        if ("WORK".equals(comment.getTargetType())) {
            com.orangejd.pojo.entity.Work w = workMapper.selectById(comment.getTargetId());
            if (w != null && currentId.equals(w.getOwnerId())) { commentMapper.deleteById(id); return; }
        } else if ("REQUIREMENT".equals(comment.getTargetType())) {
            com.orangejd.pojo.entity.Requirement r = requirementMapper.selectById(comment.getTargetId());
            if (r != null && currentId.equals(r.getPublisherId())) { commentMapper.deleteById(id); return; }
        }
        throw new BaseException("无权删除此评论");
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
