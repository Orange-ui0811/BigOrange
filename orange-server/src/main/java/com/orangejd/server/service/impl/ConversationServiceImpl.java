package com.orangejd.server.service.impl;

import com.orangejd.common.constant.MessageConstant;
import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.common.result.PageResult;
import com.orangejd.common.util.PageUtil;
import com.orangejd.pojo.dto.ConversationCreateDTO;
import com.orangejd.pojo.entity.Conversation;
import com.orangejd.pojo.vo.ConversationVO;
import com.orangejd.server.mapper.ConversationMapper;
import com.orangejd.server.service.ConversationService;
import com.orangejd.server.service.ContentAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired private ConversationMapper conversationMapper;
    @Autowired private ContentAccessService contentAccessService;

    @Override
    public ConversationVO create(ConversationCreateDTO dto) {
        Long currentId = BaseContext.getCurrentId();

        // 规则1: 不能向自己发起会话
        if (currentId.equals(dto.getReceiverId()))
            throw new BaseException("不能向自己发起会话");

        // Exactly one existing, accessible content item must be linked, and its owner is the receiver.
        if ((dto.getWorkId() == null) == (dto.getRequirementId() == null))
            throw new BaseException("必须且只能关联一个作品或需求");
        if (dto.getReceiverId() == null) throw new BaseException("接收方不能为空");
        String targetType = dto.getWorkId() != null ? "WORK" : "REQUIREMENT";
        Long targetId = dto.getWorkId() != null ? dto.getWorkId() : dto.getRequirementId();
        Long ownerId = contentAccessService.requireAccessibleOwner(targetType, targetId, currentId);
        if (!ownerId.equals(dto.getReceiverId())) throw new BaseException("接收方必须是内容发布者");

        // 规则2: 同一作品/需求同两人不能重复创建会话
        Conversation existing = conversationMapper.selectExisting(currentId, dto.getReceiverId(),
                dto.getWorkId(), dto.getRequirementId());
        // 同时检查反向（对方发起过）
        if (existing == null) {
            existing = conversationMapper.selectExisting(dto.getReceiverId(), currentId,
                    dto.getWorkId(), dto.getRequirementId());
        }
        if (existing != null) {
            // 返回已有会话
            return toVO(existing);
        }

        Conversation c = new Conversation();
        c.setWorkId(dto.getWorkId());
        c.setRequirementId(dto.getRequirementId());
        c.setInitiatorId(currentId);
        c.setReceiverId(dto.getReceiverId());
        conversationMapper.insert(c);
        return toVO(conversationMapper.selectById(c.getId()));
    }

    @Override
    public ConversationVO getDetail(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) throw new BaseException("会话不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(c.getInitiatorId()) && !currentId.equals(c.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        return toVO(c);
    }

    @Override
    public void hideConversation(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) throw new BaseException("会话不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(c.getInitiatorId()) && !currentId.equals(c.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        conversationMapper.hideByUser(id, currentId, currentId.equals(c.getInitiatorId()));
    }

    @Override
    public void deleteConversation(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) throw new BaseException("会话不存在");
        Long currentId = BaseContext.getCurrentId();
        if (!currentId.equals(c.getInitiatorId()) && !currentId.equals(c.getReceiverId()))
            throw new BaseException(MessageConstant.NO_PERMISSION);
        conversationMapper.hideByUser(id, currentId, currentId.equals(c.getInitiatorId()));
    }

    @Override
    public PageResult<ConversationVO> listMyConversations(int page, int size) {
        size = PageUtil.capSize(size);
        Long currentId = BaseContext.getCurrentId();
        int offset = PageUtil.offset(page, size);
        int total = conversationMapper.countByUserId(currentId);
        List<ConversationVO> records = conversationMapper.selectByUserId(currentId, offset, size);
        return new PageResult<>(total, records);
    }

    private ConversationVO toVO(Conversation c) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId()); vo.setWorkId(c.getWorkId()); vo.setRequirementId(c.getRequirementId());
        vo.setInitiatorId(c.getInitiatorId()); vo.setReceiverId(c.getReceiverId());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
