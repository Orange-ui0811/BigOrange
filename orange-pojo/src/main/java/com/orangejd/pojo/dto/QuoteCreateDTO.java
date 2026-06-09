package com.orangejd.pojo.dto;

import java.math.BigDecimal;

public class QuoteCreateDTO {
    private Long conversationId;
    private BigDecimal price;
    private String description;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
