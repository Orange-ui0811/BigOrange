package com.orangejd.server.foundation.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiConfigCryptoTest {
    @Test
    void encryptsAndDecryptsWithoutPersistingPlaintext() {
        AiConfigCrypto crypto = new AiConfigCrypto("test-only-encryption-key-with-enough-entropy");
        String encrypted = crypto.encrypt("sk-sensitive-value");

        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("sk-sensitive-value"));
        assertEquals("sk-sensitive-value", crypto.decrypt(encrypted));
    }

    @Test
    void detectsTamperedCiphertext() {
        AiConfigCrypto crypto = new AiConfigCrypto("test-only-encryption-key-with-enough-entropy");
        String encrypted = crypto.encrypt("sk-sensitive-value");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void refusesDynamicSecretsWithoutMasterKey() {
        AiConfigCrypto crypto = new AiConfigCrypto("");

        assertFalse(crypto.isConfigured());
        assertThrows(IllegalStateException.class, () -> crypto.encrypt("secret"));
    }
}
