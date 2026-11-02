package com.orangejd.server.service;

import com.orangejd.server.mapper.PersonaWorkshopMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PersonaMaterialCollector {
    private final PersonaWorkshopMapper mapper;

    public PersonaMaterialCollector(PersonaWorkshopMapper mapper) {
        this.mapper = mapper;
    }

    public void capture(Long accountId, PersonaDimension dimension, String eventType,
                        String evidenceType, String evidenceRef,
                        String evidenceSummary, String suggestedContent) {
        if (accountId == null || accountId <= 0) return;
        String safeRef = clean(evidenceRef, 128);
        String safeEvidence = required(evidenceSummary, 500);
        String safeSuggestion = required(suggestedContent, 500);
        String identity = String.join("|", String.valueOf(accountId), eventType,
                evidenceType, safeRef == null ? safeEvidence : safeRef);
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("accountId", accountId);
        material.put("dimensionCode", dimension.name());
        material.put("eventType", required(eventType, 48));
        material.put("evidenceType", required(evidenceType, 32));
        material.put("evidenceRef", safeRef);
        material.put("evidenceSummary", safeEvidence);
        material.put("suggestedContent", safeSuggestion);
        material.put("dedupeKey", sha256(identity));
        mapper.insertMaterial(material);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String required(String value, int max) {
        String cleaned = clean(value, max);
        if (cleaned == null) throw new IllegalArgumentException("material text is required");
        return cleaned;
    }

    private String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
