package com.orangejd.server.service;

import com.orangejd.server.dto.PersonaAccessoryBlueprintRequest;
import com.orangejd.server.dto.PersonaEntryRequest;
import com.orangejd.server.dto.PersonaEquipmentRequest;
import com.orangejd.server.dto.PersonaSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the existing persona workshop API.
 * Public methods intentionally retain their original names and response shapes.
 */
@Service
public class PersonaWorkshopService {
    private final PersonaSnapshotAssembler snapshotAssembler;
    private final PersonaEntryService entryService;
    private final PersonaMaterialService materialService;
    private final PersonaEquipmentService equipmentService;
    private final PersonaSettingsService settingsService;

    public PersonaWorkshopService(PersonaSnapshotAssembler snapshotAssembler,
                                  PersonaEntryService entryService,
                                  PersonaMaterialService materialService,
                                  PersonaEquipmentService equipmentService,
                                  PersonaSettingsService settingsService) {
        this.snapshotAssembler = snapshotAssembler;
        this.entryService = entryService;
        this.materialService = materialService;
        this.equipmentService = equipmentService;
        this.settingsService = settingsService;
    }

    public Map<String, Object> snapshot(Long accountId) {
        return snapshotAssembler.snapshot(accountId);
    }

    public Map<String, Object> publicSnapshot(Long accountId) {
        return snapshotAssembler.publicSnapshot(accountId);
    }

    public Map<String, Object> avatarSnapshot(Long accountId) {
        return snapshotAssembler.avatarSnapshot(accountId);
    }

    @Transactional
    public Map<String, Object> createEntry(Long accountId, PersonaEntryRequest request) {
        return entryService.create(accountId, request);
    }

    @Transactional
    public Map<String, Object> updateEntry(Long accountId, Long id, PersonaEntryRequest request) {
        return entryService.update(accountId, id, request);
    }

    @Transactional
    public void archiveEntry(Long accountId, Long id) {
        entryService.archive(accountId, id);
    }

    public List<Map<String, Object>> materials(Long accountId, String status) {
        return materialService.list(accountId, status);
    }

    @Transactional
    public Map<String, Object> acceptMaterial(Long accountId, Long id, String editedContent) {
        return materialService.accept(accountId, id, editedContent);
    }

    @Transactional
    public void rejectMaterial(Long accountId, Long id) {
        materialService.reject(accountId, id);
    }

    @Transactional
    public Map<String, Object> updateEquipment(Long accountId, String slotCode,
                                               PersonaEquipmentRequest request) {
        return equipmentService.update(accountId, slotCode, request);
    }

    @Transactional
    public Map<String, Object> updateAccessoryBlueprint(Long accountId, String slotCode,
                                                        PersonaAccessoryBlueprintRequest request) {
        return equipmentService.updateBlueprint(accountId, slotCode, request);
    }

    @Transactional
    public Map<String, Object> updateSettings(Long accountId, PersonaSettingsRequest request) {
        return settingsService.update(accountId, request);
    }
}
