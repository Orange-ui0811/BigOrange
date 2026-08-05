package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;
import com.orangejd.server.dto.PersonaEntryRequest;
import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonaEntryService {
    static final int MAX_ENTRIES_PER_DIMENSION = 8;
    private final PersonaWorkshopMapper mapper;

    public PersonaEntryService(PersonaWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> create(Long accountId, PersonaEntryRequest request) {
        PersonaDimension dimension = PersonaDimension.parse(request.dimension());
        String content = PersonaValueSupport.requiredText(request.content());
        List<Map<String, Object>> entries = mapper.selectEntries(accountId);
        long count = count(entries, dimension);
        if (count >= MAX_ENTRIES_PER_DIMENSION) throw new BaseException("每个画像维度最多保留 8 条内容");
        Map<String, Object> entry = entry(accountId, dimension, content, "MANUAL", null, (int) count);
        mapper.insertEntry(entry);
        return mapper.selectEntryOwned(((Number) entry.get("id")).longValue(), accountId);
    }

    public Map<String, Object> update(Long accountId, Long id, PersonaEntryRequest request) {
        PersonaDimension dimension = PersonaDimension.parse(request.dimension());
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", id);
        update.put("accountId", accountId);
        update.put("dimensionCode", dimension.name());
        update.put("content", PersonaValueSupport.requiredText(request.content()));
        if (mapper.updateEntry(update) == 0) throw new BaseException("画像内容不存在或无权修改");
        return mapper.selectEntryOwned(id, accountId);
    }

    public void archive(Long accountId, Long id) {
        if (mapper.archiveEntry(id, accountId) == 0) throw new BaseException("画像内容不存在或无权删除");
    }

    static long count(List<Map<String, Object>> entries, PersonaDimension dimension) {
        return entries.stream().filter(item -> dimension.name().equals(item.get("dimensionCode"))).count();
    }

    static Map<String, Object> entry(Long accountId, PersonaDimension dimension, String content,
                                     String sourceType, Long sourceMaterialId, int positionNo) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("accountId", accountId);
        entry.put("dimensionCode", dimension.name());
        entry.put("content", content);
        entry.put("sourceType", sourceType);
        entry.put("sourceMaterialId", sourceMaterialId);
        entry.put("positionNo", positionNo);
        return entry;
    }
}
