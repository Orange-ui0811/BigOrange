package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEntryRequest;
import com.orangejd.server.foundation.aitool.AiResultRecordMapper;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import com.orangejd.server.mapper.UserProfileMapper;
import com.orangejd.server.mapper.WorkMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaArchitectureServicesTest {
    @Test
    void snapshotAssemblerPreservesThePublicFieldOrderAndDefaults() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        WorkMapper works = mock(WorkMapper.class);
        AiResultRecordMapper records = mock(AiResultRecordMapper.class);
        when(mapper.selectEntries(7L)).thenReturn(List.of(Map.of(
                "dimensionCode", "GOAL", "content", "完成项目")));
        when(mapper.selectMaterials(7L, "PENDING", 50)).thenReturn(List.of());
        when(mapper.selectEquipment(7L)).thenReturn(List.of());
        when(mapper.selectSettings(7L)).thenReturn(null);
        when(works.selectByOwnerId(7L, 0, 5)).thenReturn(List.of());
        when(records.selectRecentForPersona(7L, 5)).thenReturn(List.of());
        PersonaSettingsService settings = new PersonaSettingsService(mapper);
        PersonaSnapshotAssembler assembler = new PersonaSnapshotAssembler(mapper,
                new PersonaMaterialCollector(mapper), profiles, works, records, settings,
                new PersonaEquipmentService(mapper, new PersonaBlueprintValidator()),
                new PersonaRecommendationPolicy());

        Map<String, Object> snapshot = assembler.snapshot(7L);

        assertEquals(List.of("dimensions", "materials", "materialCount", "equipment", "settings",
                "completion", "recommendations"), new ArrayList<>(snapshot.keySet()));
        assertEquals(100 / PersonaDimension.values().length,
                ((Map<?, ?>) snapshot.get("completion")).get("percent"));
        assertEquals("#F37021", ((Map<?, ?>) snapshot.get("settings")).get("bodyColor"));
    }

    @Test
    void publicAndAvatarSnapshotsDoNotLeakPrivateEntriesWhenShowcaseIsDisabled() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectSettings(7L)).thenReturn(Map.of("showcaseEnabled", false, "avatarEnabled", true));
        PersonaSnapshotAssembler assembler = assembler(mapper);

        Map<String, Object> publicValue = assembler.publicSnapshot(7L);
        Map<String, Object> avatar = assembler.avatarSnapshot(7L);

        assertFalse((Boolean) publicValue.get("enabled"));
        assertFalse(publicValue.containsKey("dimensions"));
        assertEquals(false, avatar.get("enabled"));
        assertEquals(List.of(), avatar.get("equipment"));
    }

    @Test
    void entryAndMaterialServicesEnforceLimitsOwnershipAndStatuses() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        PersonaEntryService entries = new PersonaEntryService(mapper);
        PersonaMaterialService materials = new PersonaMaterialService(mapper);
        List<Map<String, Object>> full = new ArrayList<>();
        for (int i = 0; i < 8; i++) full.add(Map.of("dimensionCode", "GOAL"));
        when(mapper.selectEntries(7L)).thenReturn(full);
        assertThrows(BaseException.class,
                () -> entries.create(7L, new PersonaEntryRequest("GOAL", "目标")));
        when(mapper.archiveEntry(3L, 7L)).thenReturn(0);
        assertThrows(BaseException.class, () -> entries.archive(7L, 3L));
        assertThrows(BaseException.class, () -> materials.list(7L, "UNKNOWN"));
        when(mapper.selectMaterialOwned(9L, 7L)).thenReturn(null);
        assertThrows(BaseException.class, () -> materials.reject(7L, 9L));
    }

    @Test
    void blueprintValidatorRejectsDuplicateIdsAndBoundsMaterialValues() {
        PersonaBlueprintValidator validator = new PersonaBlueprintValidator();
        PersonaVisualLanguage.EquipmentDefinition definition = PersonaVisualLanguage.requireSlot("GOAL_CROWN");
        PersonaAccessoryBlueprintRequest.Part part = new PersonaAccessoryBlueprintRequest.Part(
                "same", "圆环", "TORUS", true, false, Map.of("radius", .8), Map.of(),
                new PersonaAccessoryBlueprintRequest.Material("#F59E0B", .9, .2, .4, .1));
        assertThrows(BaseException.class, () -> validator.validate("GOAL_CROWN", definition,
                new PersonaAccessoryBlueprintRequest("重复", List.of(part, part))));
        PersonaAccessoryBlueprintRequest.Part invalidMaterial = new PersonaAccessoryBlueprintRequest.Part(
                "one", "圆环", "TORUS", true, false, Map.of(), Map.of(),
                new PersonaAccessoryBlueprintRequest.Material("bad", 1.0, .2, .4, .1));
        assertThrows(BaseException.class, () -> validator.validate("GOAL_CROWN", definition,
                new PersonaAccessoryBlueprintRequest("颜色", List.of(invalidMaterial))));
    }

    @Test
    void entryCreationKeepsMapperPayloadAndTruncatesLongContent() {
        PersonaWorkshopMapper mapper = mock(PersonaWorkshopMapper.class);
        when(mapper.selectEntries(7L)).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<Map<String, Object>>getArgument(0).put("id", 33L);
            return 1;
        }).when(mapper).insertEntry(any());
        when(mapper.selectEntryOwned(33L, 7L)).thenReturn(Map.of("id", 33L));
        String content = "x".repeat(520);

        Map<String, Object> result = new PersonaEntryService(mapper)
                .create(7L, new PersonaEntryRequest("SKILL", content));

        assertEquals(33L, result.get("id"));
        verify(mapper).insertEntry(org.mockito.ArgumentMatchers.argThat(value ->
                String.valueOf(value.get("content")).length() == 500
                        && "MANUAL".equals(value.get("sourceType"))));
    }

    private PersonaSnapshotAssembler assembler(PersonaWorkshopMapper mapper) {
        WorkMapper works = mock(WorkMapper.class);
        AiResultRecordMapper records = mock(AiResultRecordMapper.class);
        when(works.selectByOwnerId(7L, 0, 5)).thenReturn(List.of());
        when(records.selectRecentForPersona(7L, 5)).thenReturn(List.of());
        PersonaSettingsService settings = new PersonaSettingsService(mapper);
        return new PersonaSnapshotAssembler(mapper, new PersonaMaterialCollector(mapper),
                mock(UserProfileMapper.class), works, records, settings,
                new PersonaEquipmentService(mapper, new PersonaBlueprintValidator()),
                new PersonaRecommendationPolicy());
    }
}
