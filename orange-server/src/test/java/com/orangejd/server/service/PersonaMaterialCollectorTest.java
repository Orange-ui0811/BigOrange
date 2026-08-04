package com.orangejd.server.service;

import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PersonaMaterialCollectorTest {

    @Test
    void createsStableDeduplicatedPendingMaterialWithoutConfirmingItAsProfileData() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        PersonaMaterialCollector collector = new PersonaMaterialCollector(mapper);

        collector.capture(5L, PersonaDimension.GROWTH, "AI_RESULT_COMPLETED",
                "AI_RESULT", "request-1", "  completed   a trend analysis  ",
                "  I completed a trend analysis  ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> material = ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertMaterial(material.capture());
        assertEquals("GROWTH", material.getValue().get("dimensionCode"));
        assertEquals("completed a trend analysis", material.getValue().get("evidenceSummary"));
        assertEquals(64, String.valueOf(material.getValue().get("dedupeKey")).length());
    }
}
