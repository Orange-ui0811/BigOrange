package com.orangejd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.PersonaAppearanceShareMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaAppearanceShareServiceTest {

    @Test
    void exportContainsOnlyVisualStateAndGeneratesAReadableCode() {
        PersonaAppearanceShareMapper shares = mock(PersonaAppearanceShareMapper.class);
        PersonaWorkshopMapper workshop = mock(PersonaWorkshopMapper.class);
        PersonaSettingsService settings = mock(PersonaSettingsService.class);
        PersonaEquipmentService equipment = mock(PersonaEquipmentService.class);
        when(workshop.selectEntries(7L)).thenReturn(completeEntries());
        when(settings.load(7L)).thenReturn(Map.of("showcaseEnabled", true));
        when(settings.appearance(any())).thenReturn(Map.of("bodyColor", "#F37021"));
        when(equipment.view(any(), any())).thenReturn(List.of());
        when(shares.insert(any())).thenReturn(1);
        PersonaAppearanceShareService service = service(shares, workshop, settings, equipment);

        Map<String, Object> result = service.exportCode(7L);

        assertTrue(String.valueOf(result.get("code")).matches("^OVC-[A-Z2-9]{4}-[A-Z2-9]{4}$"));
        ArgumentCaptor<Map<String, Object>> saved = mapCaptor();
        verify(shares).insert(saved.capture());
        String payload = String.valueOf(saved.getValue().get("payloadJson"));
        assertTrue(payload.contains("bodyColor"));
        assertFalse(payload.contains("画像内容"));
        assertFalse(payload.contains("accountId"));
    }

    @Test
    void incompleteUsersCannotApplyACodeOrBypassUnlocks() {
        PersonaAppearanceShareMapper shares = mock(PersonaAppearanceShareMapper.class);
        PersonaWorkshopMapper workshop = mock(PersonaWorkshopMapper.class);
        when(workshop.selectEntries(8L)).thenReturn(List.of(Map.of("dimensionCode", "GOAL")));
        PersonaSettingsService settings = mock(PersonaSettingsService.class);
        PersonaEquipmentService equipment = mock(PersonaEquipmentService.class);
        PersonaAppearanceShareService service = service(shares, workshop, settings, equipment);

        assertThrows(BaseException.class, () -> service.apply(8L, "OVC-ABCD-2345"));
        verify(shares, never()).recordImport(any());
        verify(equipment, never()).update(any(), any(), any());
    }

    @Test
    void previewMarksEquipmentWhosePersonaDimensionIsStillLocked() throws Exception {
        PersonaAppearanceShareMapper shares = mock(PersonaAppearanceShareMapper.class);
        PersonaWorkshopMapper workshop = mock(PersonaWorkshopMapper.class);
        PersonaSettingsService settings = mock(PersonaSettingsService.class);
        PersonaEquipmentService equipment = mock(PersonaEquipmentService.class);
        when(workshop.selectEntries(8L)).thenReturn(List.of());
        String payload = new ObjectMapper().writeValueAsString(Map.of(
                "schemaVersion", 1,
                "appearance", Map.of("bodyColor", "#F37021"),
                "equipment", List.of(Map.of("slotCode", "GOAL_CROWN", "itemCode", "COMPASS_CROWN",
                        "variantCode", "CLASSIC", "colorHex", "#F59E0B", "equipped", true,
                        "transform", Map.of()))));
        when(shares.selectActiveByCode("OVC-ABCD-2345")).thenReturn(Map.of(
                "code", "OVC-ABCD-2345", "schemaVersion", 1, "payloadJson", payload));
        PersonaAppearanceShareService service = service(shares, workshop, settings, equipment);

        Map<String, Object> preview = service.preview(8L, "ovc-abcd-2345");

        assertEquals(false, preview.get("readyToImport"));
        assertEquals(List.of("方向罗盘"), preview.get("blockedEquipment"));
    }

    private PersonaAppearanceShareService service(PersonaAppearanceShareMapper shares,
                                                   PersonaWorkshopMapper workshop,
                                                   PersonaSettingsService settings,
                                                   PersonaEquipmentService equipment) {
        return new PersonaAppearanceShareService(shares, workshop, settings, equipment, new ObjectMapper());
    }

    private List<Map<String, Object>> completeEntries() {
        return Arrays.stream(PersonaDimension.values())
                .map(dimension -> Map.<String, Object>of("dimensionCode", dimension.name(), "content", "private"))
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
