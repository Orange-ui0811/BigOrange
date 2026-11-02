package com.orangejd.server.service;
import com.orangejd.common.constant.QuoteStatus;
import com.orangejd.server.mapper.QuoteMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service
public class QuoteTimeoutService {
    private final QuoteMapper mapper;
    public QuoteTimeoutService(QuoteMapper mapper) { this.mapper = mapper; }
    public void process() { mapper.selectExpired(QuoteStatus.PENDING_CONFIRM, LocalDateTime.now())
            .forEach(value -> mapper.updateStatus(value.getId(), QuoteStatus.EXPIRED)); }
}
