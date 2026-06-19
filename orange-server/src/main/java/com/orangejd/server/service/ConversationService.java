package com.orangejd.server.service;

import com.orangejd.common.result.PageResult;
import com.orangejd.pojo.dto.ConversationCreateDTO;
import com.orangejd.pojo.vo.ConversationVO;

public interface ConversationService {
    ConversationVO create(ConversationCreateDTO dto);
    ConversationVO getDetail(Long id);
    void deleteConversation(Long id);
    void hideConversation(Long id);
    PageResult<ConversationVO> listMyConversations(int page, int size);
}
