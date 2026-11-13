package com.studio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyCipherTest {

    @Test
    void testEncryptAndDecryptRoundtrip() {
        String originalKey = "sk-proj-test1234567890abcdefghijklmnopqrstuvwxyz";
        String encrypted = ApiKeyCipher.encrypt(originalKey);

        assertNotNull(encrypted);
        assertTrue(ApiKeyCipher.isEncrypted(encrypted), "Encrypted string must start with enc:v1:");
        assertNotEquals(originalKey, encrypted, "Ciphertext must not be plaintext");

        String decrypted = ApiKeyCipher.decrypt(encrypted);
        assertEquals(originalKey, decrypted, "Decrypted text must match original API key");
    }

    @Test
    void testNullAndEmptyHandling() {
        assertEquals("", ApiKeyCipher.encrypt(null));
        assertEquals("", ApiKeyCipher.encrypt(""));
        assertEquals("", ApiKeyCipher.encrypt("   "));

        assertEquals("", ApiKeyCipher.decrypt(null));
        assertEquals("", ApiKeyCipher.decrypt(""));
        assertEquals("", ApiKeyCipher.decrypt("   "));
    }

    @Test
    void testLegacyPlaintextPassThrough() {
        String legacyKey = "sk-proj-legacy-unencrypted-key";
        assertFalse(ApiKeyCipher.isEncrypted(legacyKey));
        // Decrypting legacy plaintext should transparently return the same plaintext
        assertEquals(legacyKey, ApiKeyCipher.decrypt(legacyKey));
    }

    @Test
    void testRandomIvProducesDifferentCiphertexts() {
        String key = "sk-test-key-consistent";
        String enc1 = ApiKeyCipher.encrypt(key);
        String enc2 = ApiKeyCipher.encrypt(key);

        assertNotEquals(enc1, enc2, "Each encryption must use a unique random IV");
        assertEquals(key, ApiKeyCipher.decrypt(enc1));
        assertEquals(key, ApiKeyCipher.decrypt(enc2));
    }

    @Test
    void testTamperingRejection() {
        String key = "sk-test-tamper-key";
        String enc = ApiKeyCipher.encrypt(key);
        // Tamper with the ciphertext
        String tampered = enc.substring(0, enc.length() - 5) + "AAAAA";
        String decrypted = ApiKeyCipher.decrypt(tampered);
        // Tampered GCM ciphertext must fail authentication and safely return empty
        assertEquals("", decrypted);
    }

    @Test
    void testMaskKey() {
        assertEquals("", ApiKeyCipher.mask(null));
        assertEquals("", ApiKeyCipher.mask(""));
        assertEquals("••••••••", ApiKeyCipher.mask("short"));

        String openAiKey = "sk-proj-1234567890abcdefghijXYZW";
        String masked = ApiKeyCipher.mask(openAiKey);
        assertTrue(masked.startsWith("sk-"), "Masked key should retain sk- prefix");
        assertTrue(masked.endsWith("XYZW"), "Masked key should retain last 4 characters for identification");
        assertTrue(masked.contains("•"), "Masked key should contain bullets");
        assertFalse(masked.contains("1234567890"), "Masked key must not reveal secret content");
        assertTrue(ApiKeyCipher.isMasked(masked));
        assertFalse(ApiKeyCipher.isMasked(openAiKey));
        assertFalse(ApiKeyCipher.isMasked(null));
        assertFalse(ApiKeyCipher.isMasked(""));
    }
}
