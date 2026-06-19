package com.orangejd.server.service;

import com.orangejd.pojo.dto.QuoteCreateDTO;
import com.orangejd.pojo.entity.Quote;

import java.util.List;

public interface QuoteService {
    Quote create(QuoteCreateDTO dto);
    void accept(Long id);
    void reject(Long id);
    void cancel(Long id);
    Quote getDetail(Long id);
    List<Quote> getByConversation(Long conversationId);
}
