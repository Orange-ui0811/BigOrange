package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaSettingsRequest;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaWorkshopServiceTest {

    @Test
    void lockedEquipmentCannotBeEquippedBeforeItsDimensionHasConfirmedContent() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectEntries(7L)).thenReturn(List.of());
        PersonaWorkshopService service = service(mapper);

        assertThrows(BaseException.class, () -> service.updateEquipment(7L, "GOAL_CROWN",
                new PersonaEquipmentRequest("COMPASS_CROWN", "CLASSIC", "#F59E0B", true)));
    }

    @Test
    void acceptingMaterialCreatesAConfirmedEntryUsingTheUsersEditedText() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectMaterialOwned(11L, 7L)).thenReturn(Map.of(
                "id", 11L,
                "dimensionCode", "GROWTH",
                "suggestedContent", "suggested",
                "status", "PENDING"));
        when(mapper.selectEntryByMaterial(7L, 11L)).thenReturn(null);
        when(mapper.selectEntries(7L)).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<Map<String, Object>>getArgument(0).put("id", 21L);
            return 1;
        }).when(mapper).insertEntry(any());
        when(mapper.acceptMaterial(11L, 7L)).thenReturn(1);
        when(mapper.selectEntryOwned(21L, 7L)).thenReturn(Map.of("id", 21L, "content", "edited by user"));
        PersonaWorkshopService service = service(mapper);

        Map<String, Object> accepted = service.acceptMaterial(7L, 11L, "edited by user");

        ArgumentCaptor<Map<String, Object>> entry = mapCaptor();
        verify(mapper).insertEntry(entry.capture());
        assertEquals("edited by user", entry.getValue().get("content"));
        assertEquals("MATERIAL", entry.getValue().get("sourceType"));
        assertEquals(11L, entry.getValue().get("sourceMaterialId"));
        assertEquals(21L, accepted.get("id"));
    }

    @Test
    void equipmentStateIsStoredOnlyAfterSlotItemAndVariantValidation() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(Map.of("dimensionCode", "SKILL", "content", "Built a Vue application"));
        when(mapper.selectEntries(7L)).thenReturn(entries);
        PersonaWorkshopService service = service(mapper);

        service.updateEquipment(7L, "SKILL_VISOR",
                new PersonaEquipmentRequest("CRAFT_VISOR", "ROUND", "#0ea5e9", true));

        ArgumentCaptor<Map<String, Object>> equipment = mapCaptor();
        verify(mapper).upsertEquipment(equipment.capture());
        assertEquals("SKILL_VISOR", equipment.getValue().get("slotCode"));
        assertEquals("ROUND", equipment.getValue().get("variantCode"));
        assertEquals("#0EA5E9", equipment.getValue().get("colorHex"));
        assertEquals(true, equipment.getValue().get("equipped"));
    }

    @Test
    void avatarCannotRemainEnabledWhenPublicShowcaseIsDisabled() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectSettings(7L)).thenReturn(Map.of(
                "showcaseEnabled", false,
                "avatarEnabled", false));
        PersonaWorkshopService service = service(mapper);

        service.updateSettings(7L, new PersonaSettingsRequest(false, true));

        ArgumentCaptor<Map<String, Object>> settings = mapCaptor();
        verify(mapper).upsertSettings(settings.capture());
        assertEquals(false, settings.getValue().get("showcaseEnabled"));
        assertEquals(false, settings.getValue().get("avatarEnabled"));
    }

    @Test
    void faceAndEquipmentDiyParametersAreValidatedAndSerialized() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectSettings(7L)).thenReturn(Map.of("showcaseEnabled", false, "avatarEnabled", false));
        when(mapper.selectEntries(7L)).thenReturn(List.of(Map.of("dimensionCode", "SKILL")));
        when(mapper.selectEquipment(7L)).thenReturn(List.of());
        PersonaWorkshopService service = service(mapper);

        service.updateSettings(7L, new PersonaSettingsRequest(false, false, "#F37021",
                "FRIENDLY", "CLASSIC", "NATURAL", Map.of("eyeSize", 1.25, "mouthCurve", -0.4)));
        service.updateEquipment(7L, "SKILL_VISOR", new PersonaEquipmentRequest(
                "CRAFT_VISOR", "ROUND", "#0EA5E9", true,
                Map.of("scale", 1.3, "offsetY", 0.2, "rotationZ", 18.0)));

        ArgumentCaptor<Map<String, Object>> settings = mapCaptor();
        verify(mapper).upsertSettings(settings.capture());
        assertTrue(String.valueOf(settings.getValue().get("faceConfigJson")).contains("\"eyeSize\":1.25"));
        ArgumentCaptor<Map<String, Object>> equipment = mapCaptor();
        verify(mapper).upsertEquipment(equipment.capture());
        assertTrue(String.valueOf(equipment.getValue().get("transformJson")).contains("\"scale\":1.3"));

        assertThrows(BaseException.class, () -> service.updateSettings(7L,
                new PersonaSettingsRequest(false, false, "#F37021", "FRIENDLY", "CLASSIC",
                        "NATURAL", Map.of("eyeSize", 9.0))));
    }

    @Test
    void accessoryBlueprintIsRestrictedToSafeShapesAndPartLimits() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectEntries(7L)).thenReturn(List.of(Map.of("dimensionCode", "GOAL")));
        PersonaWorkshopService service = service(mapper);
        PersonaAccessoryBlueprintRequest.Material material = new PersonaAccessoryBlueprintRequest.Material(
                "#F59E0B", 0.9, 0.4, 0.3, 0.2);
        PersonaAccessoryBlueprintRequest.Part part = new PersonaAccessoryBlueprintRequest.Part(
                "ring-1", "主圆环", "TORUS", true, false,
                Map.of("radius", 0.75, "tube", 0.06),
                Map.of("x", 0.0, "y", 1.95, "z", 0.0, "scaleX", 1.0, "scaleY", 1.0,
                        "scaleZ", 1.0, "rotationX", 90.0, "rotationY", 0.0, "rotationZ", 0.0),
                material);

        service.updateAccessoryBlueprint(7L, "GOAL_CROWN",
                new PersonaAccessoryBlueprintRequest("我的罗盘", List.of(part)));

        ArgumentCaptor<Map<String, Object>> blueprint = mapCaptor();
        verify(mapper).upsertAccessoryBlueprint(blueprint.capture());
        assertTrue(String.valueOf(blueprint.getValue().get("blueprintJson")).contains("\"shape\":\"TORUS\""));

        PersonaAccessoryBlueprintRequest.Part illegal = new PersonaAccessoryBlueprintRequest.Part(
                "bad", "非法", "BOX", true, false, Map.of(), Map.of(), material);
        assertThrows(BaseException.class, () -> service.updateAccessoryBlueprint(7L, "GOAL_CROWN",
                new PersonaAccessoryBlueprintRequest("非法饰品", List.of(illegal))));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private PersonaWorkshopService service(PersonaWorkshopMapper mapper) {
        return new PersonaWorkshopService(mapper, new PersonaMaterialCollector(mapper),
                mock(UserProfileMapper.class), mock(WorkMapper.class), mock(AiResultRecordMapper.class));
    }
}
