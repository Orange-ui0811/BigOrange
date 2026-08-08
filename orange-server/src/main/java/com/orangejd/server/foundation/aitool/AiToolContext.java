package com.orangejd.server.foundation.aitool;

public record AiToolContext(
        String purpose,
        String requestId,
        Long accountId,
        String businessType,
        String businessId,
        String legacyConfigType,
        String requestHash
) {
    public AiToolContext(String purpose, String requestId, Long accountId,
                         String businessType, String businessId, String legacyConfigType) {
        this(purpose, requestId, accountId, businessType, businessId, legacyConfigType, "");
    }

    public AiToolContext {
        purpose = safe(purpose, "unknown", 64);
        requestId = safe(requestId, java.util.UUID.randomUUID().toString(), 128);
        businessType = safe(businessType, purpose, 64);
        businessId = safe(businessId, "", 128);
        legacyConfigType = safe(legacyConfigType, "trend", 32);
        requestHash = safe(requestHash, "", 64);
    }

    private static String safe(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
