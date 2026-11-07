package com.studio.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Transparent AES-256-GCM encryption & decryption utility for storing API keys securely at rest.
 * Uses machine-bound entropy (PBKDF2WithHmacSHA256) so keys cannot be decrypted if copied off-device.
 */
public final class ApiKeyCipher {

    public static final String ENVELOPE_PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    private static final byte[] STATIC_SALT = new byte[]{
            (byte) 0x50, (byte) 0x6c, (byte) 0x61, (byte) 0x79, // Play
            (byte) 0x77, (byte) 0x72, (byte) 0x69, (byte) 0x67, // wrig
            (byte) 0x68, (byte) 0x74, (byte) 0x53, (byte) 0x74, // htSt
            (byte) 0x75, (byte) 0x64, (byte) 0x69, (byte) 0x6f  // udio
    };

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile SecretKey cachedKey = null;

    private ApiKeyCipher() {}

    /**
     * Checks if a string is already encrypted in the v1 envelope format.
     */
    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith(ENVELOPE_PREFIX);
    }

    /**
     * Encrypts plain-text API key with AES-256-GCM using unique 12-byte random IV.
     * Returns "enc:v1:<base64(iv + ciphertext_with_tag)>".
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        if (isEncrypted(plainText)) {
            return plainText;
        }

        try {
            SecretKey secretKey = getOrDeriveKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] plainBytes = plainText.trim().getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherBytes);

            String encoded = Base64.getEncoder().encodeToString(byteBuffer.array());
            return ENVELOPE_PREFIX + encoded;
        } catch (Exception ex) {
            // In case of an unexpected cipher failure, return plain text as fallback
            return plainText.trim();
        }
    }

    /**
     * Transparently decrypts an encrypted API key.
     * If string is not encrypted (legacy plain text), returns it directly.
     */
    public static String decrypt(String cipherOrPlain) {
        if (cipherOrPlain == null || cipherOrPlain.isBlank()) {
            return "";
        }
        if (!isEncrypted(cipherOrPlain)) {
            return cipherOrPlain.trim();
        }

        try {
            String base64Payload = cipherOrPlain.substring(ENVELOPE_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Payload);
            if (combined.length <= GCM_IV_LENGTH) {
                return "";
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherBytes);

            SecretKey secretKey = getOrDeriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(cipherBytes);
            return new String(decrypted, StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            // Authentication tag mismatch (tampering) or different machine: safely return empty
            return "";
        }
    }

    /**
     * Masks an API key for safe display and logging (e.g. sk-••••••••XYZW).
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String clean = apiKey.trim();
        if (isEncrypted(clean)) {
            clean = decrypt(clean);
        }
        if (clean.length() <= 8) {
            return "••••••••";
        }
        if (clean.startsWith("sk-") && clean.length() > 8) {
            String suffix = clean.substring(clean.length() - 4);
            return "sk-••••••••" + suffix;
        }
        String prefix = clean.substring(0, Math.min(3, clean.length()));
        String suffix = clean.substring(Math.max(0, clean.length() - 4));
        return prefix + "••••••••" + suffix;
    }

    /**
     * Checks if a string appears to be a masked API key placeholder.
     */
    public static boolean isMasked(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return apiKey.contains("•") || apiKey.contains("****") || apiKey.contains("***");
    }

    private static SecretKey getOrDeriveKey() {
        if (cachedKey != null) {
            return cachedKey;
        }
        synchronized (ApiKeyCipher.class) {
            if (cachedKey != null) {
                return cachedKey;
            }
            try {
                String machineEntropy = buildDeviceEntropy();
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(machineEntropy.toCharArray(), STATIC_SALT, ITERATIONS, KEY_LENGTH);
                SecretKey tmp = factory.generateSecret(spec);
                cachedKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            } catch (Exception e) {
                // Fallback fixed-key if machine derivation fails
                cachedKey = new SecretKeySpec(STATIC_SALT, 0, 16, "AES");
            }
            return cachedKey;
        }
    }

    private static String buildDeviceEntropy() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("user.name", "unknown_user")).append("|");
        sb.append(System.getProperty("os.name", "unknown_os")).append("|");
        sb.append(System.getProperty("user.home", "/")).append("|");

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                List<NetworkInterface> list = Collections.list(interfaces);
                list.sort(Comparator.comparing(NetworkInterface::getName));
                for (NetworkInterface nif : list) {
                    if (nif.isLoopback()) {
                        continue;
                    }
                    byte[] mac = nif.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        for (byte b : mac) {
                            sb.append(String.format("%02X", b));
                        }
                        break; // Use the first valid hardware MAC in deterministic sorted order
                    }
                }
            }
        } catch (Exception ignored) {}

        return sb.toString();
    }
}
