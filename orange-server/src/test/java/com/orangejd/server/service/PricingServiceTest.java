package com.orangejd.server.service;

import com.orangejd.pojo.entity.AiServicePricing;
import com.orangejd.server.mapper.PricingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private PricingMapper mapper;
    private PricingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PricingMapper.class);
        service = new PricingService();
        ReflectionTestUtils.setField(service, "pricingMapper", mapper);
    }

    @Test
    void savedCostRemainsAfterReloadFromDatabase() {
        when(mapper.selectAll()).thenReturn(List.of(pricing("ai:plan", "方案设计", 40)));
        service.loadFromDB();

        service.updateCost("ai:plan", 75, 3L, "方案设计");
        assertEquals(75, service.getCost("ai:plan"));
        verify(mapper).upsert(any(AiServicePricing.class));

        when(mapper.selectAll()).thenReturn(List.of(pricing("ai:plan", "方案设计", 75)));
        service.loadFromDB();
        assertEquals(75, service.getCost("ai:plan"));
    }

    private AiServicePricing pricing(String key, String name, int cost) {
        AiServicePricing pricing = new AiServicePricing();
        pricing.setServiceKey(key);
        pricing.setServiceName(name);
        pricing.setSegmentCost(cost);
        return pricing;
    }
}
