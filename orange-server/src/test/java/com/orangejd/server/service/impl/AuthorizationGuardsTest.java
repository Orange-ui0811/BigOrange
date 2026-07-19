package com.orangejd.server.service.impl;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.exception.BaseException;
import com.orangejd.pojo.entity.Conversation;
import com.orangejd.pojo.dto.MessageSendDTO;
import com.orangejd.server.mapper.ConversationMapper;
import com.orangejd.server.mapper.FavoriteMapper;
import com.orangejd.server.mapper.MessageMapper;
import com.orangejd.server.mapper.QuoteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthorizationGuardsTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void nonParticipantCannotMarkConversationRead() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        Conversation conversation = conversation(1L, 2L);
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        MessageServiceImpl service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        BaseContext.setCurrentId(3L);

        assertThrows(BaseException.class, () -> service.markAsRead(9L));
        verify(messageMapper, never()).markAsRead(anyLong(), anyLong());
    }

    @Test
    void nonParticipantCannotListConversationQuotes() {
        QuoteMapper quoteMapper = mock(QuoteMapper.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        when(conversationMapper.selectById(9L)).thenReturn(conversation(1L, 2L));
        QuoteServiceImpl service = new QuoteServiceImpl();
        ReflectionTestUtils.setField(service, "quoteMapper", quoteMapper);
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        BaseContext.setCurrentId(3L);

        assertThrows(BaseException.class, () -> service.getByConversation(9L));
        verify(quoteMapper, never()).selectByConversationId(anyLong());
    }

    @Test
    void favoriteDeletionIsScopedToCurrentAccount() {
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FavoriteServiceImpl service = new FavoriteServiceImpl();
        ReflectionTestUtils.setField(service, "favoriteMapper", favoriteMapper);
        BaseContext.setCurrentId(42L);

        service.unfavoriteById(8L);

        verify(favoriteMapper).deleteById(8L, 42L);
    }

    @Test
    void deletingConversationOnlyHidesItForCurrentParticipant() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        when(conversationMapper.selectById(9L)).thenReturn(conversation(1L, 2L));
        ConversationServiceImpl service = new ConversationServiceImpl();
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        BaseContext.setCurrentId(1L);

        service.deleteConversation(9L);

        verify(conversationMapper).hideByUser(9L, 1L, true);
        verify(conversationMapper, never()).deleteMessagesByConvId(anyLong());
        verify(conversationMapper, never()).deleteById(anyLong());
    }

    @Test
    void hidingConversationOnlyAffectsCurrentParticipant() {
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        when(conversationMapper.selectById(9L)).thenReturn(conversation(1L, 2L));
        ConversationServiceImpl service = new ConversationServiceImpl();
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        BaseContext.setCurrentId(2L);

        service.hideConversation(9L);

        verify(conversationMapper).hideByUser(9L, 2L, false);
        verify(conversationMapper, never()).deleteMessagesByConvId(anyLong());
    }

    @Test
    void newMessageMakesConversationVisibleToBothParticipants() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        when(conversationMapper.selectById(9L)).thenReturn(conversation(1L, 2L));
        MessageServiceImpl service = new MessageServiceImpl();
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        BaseContext.setCurrentId(1L);
        MessageSendDTO dto = new MessageSendDTO();
        dto.setConversationId(9L);
        dto.setContent("重新联系");

        service.send(dto);

        verify(conversationMapper).showForAll(9L);
        verify(conversationMapper).updateTime(9L);
    }

    private Conversation conversation(Long initiator, Long receiver) {
        Conversation conversation = new Conversation();
        conversation.setInitiatorId(initiator);
        conversation.setReceiverId(receiver);
        return conversation;
    }
}
