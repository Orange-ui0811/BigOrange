package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PersonaMaterialService {
    private static final Set<String> STATUSES = Set.of("PENDING", "ACCEPTED", "REJECTED");
    private final PersonaWorkshopMapper mapper;

    public PersonaMaterialService(PersonaWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(Long accountId, String status) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !STATUSES.contains(normalized)) throw new BaseException("素材状态无效");
        return mapper.selectMaterials(accountId, normalized, 100);
    }

    public Map<String, Object> accept(Long accountId, Long id, String editedContent) {
        Map<String, Object> material = mapper.selectMaterialOwned(id, accountId);
        if (material == null) throw new BaseException("画像素材不存在或无权访问");
        Map<String, Object> existing = mapper.selectEntryByMaterial(accountId, id);
        if (existing != null) return existing;
        if (!"PENDING".equals(material.get("status"))) throw new BaseException("该画像素材已经处理");
        PersonaDimension dimension = PersonaDimension.parse(String.valueOf(material.get("dimensionCode")));
        String content = editedContent == null || editedContent.isBlank()
                ? String.valueOf(material.get("suggestedContent")) : editedContent;
        long position = PersonaEntryService.count(mapper.selectEntries(accountId), dimension);
        if (position >= PersonaEntryService.MAX_ENTRIES_PER_DIMENSION)
            throw new BaseException("该画像维度已经达到 8 条上限");
        Map<String, Object> entry = PersonaEntryService.entry(accountId, dimension,
                PersonaValueSupport.requiredText(content), "MATERIAL", id, (int) position);
        mapper.insertEntry(entry);
        if (mapper.acceptMaterial(id, accountId) == 0) throw new BaseException("该画像素材已经处理");
        return mapper.selectEntryOwned(((Number) entry.get("id")).longValue(), accountId);
    }

    public void reject(Long accountId, Long id) {
        Map<String, Object> material = mapper.selectMaterialOwned(id, accountId);
        if (material == null) throw new BaseException("画像素材不存在或无权访问");
        if (mapper.rejectMaterial(id, accountId) == 0) throw new BaseException("该画像素材已经处理");
    }
}
