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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private static volatile SecretKey cachedLegacyKey = null;

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
            // Fail closed: returning the plaintext key here would silently write a secret to
            // settings.json in the clear. The caller must abort the save instead.
            throw new IllegalStateException(
                    "Không thể mã hoá API key bằng AES-256-GCM: " + ex.getMessage(), ex);
        }
    }

    /**
     * Transparently decrypts an encrypted API key.
     * Supports automatic fallback to legacy entropy derivation for backwards compatibility.
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
            String res = decryptWithKey(cipherOrPlain, getOrDeriveKey());
            if (!res.isEmpty()) {
                return res;
            }
        } catch (Exception ignored) {}

        try {
            return decryptWithKey(cipherOrPlain, getOrDeriveLegacyKey());
        } catch (Exception ex) {
            // Authentication tag mismatch (tampering) or different machine: safely return empty
            return "";
        }
    }

    private static String decryptWithKey(String cipherText, SecretKey key) throws Exception {
        String base64Payload = cipherText.substring(ENVELOPE_PREFIX.length());
        byte[] combined = Base64.getDecoder().decode(base64Payload);
        if (combined.length <= GCM_IV_LENGTH) {
            return "";
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);

        byte[] cipherBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherBytes);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] decrypted = cipher.doFinal(cipherBytes);
        return new String(decrypted, StandardCharsets.UTF_8).trim();
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
                String machineEntropy = buildStableDeviceEntropy();
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(machineEntropy.toCharArray(), STATIC_SALT, ITERATIONS, KEY_LENGTH);
                SecretKey tmp = factory.generateSecret(spec);
                cachedKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            } catch (Exception e) {
                cachedKey = getOrGeneratePersistentFallbackKey();
            }
            return cachedKey;
        }
    }

    private static SecretKey getOrDeriveLegacyKey() {
        if (cachedLegacyKey != null) {
            return cachedLegacyKey;
        }
        synchronized (ApiKeyCipher.class) {
            if (cachedLegacyKey != null) {
                return cachedLegacyKey;
            }
            try {
                String machineEntropy = buildLegacyDeviceEntropy();
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec spec = new PBEKeySpec(machineEntropy.toCharArray(), STATIC_SALT, ITERATIONS, KEY_LENGTH);
                SecretKey tmp = factory.generateSecret(spec);
                cachedLegacyKey = new SecretKeySpec(tmp.getEncoded(), "AES");
            } catch (Exception e) {
                cachedLegacyKey = getOrGeneratePersistentFallbackKey();
            }
            return cachedLegacyKey;
        }
    }

    private static SecretKey getOrGeneratePersistentFallbackKey() {
        try {
            Path configDir = StudioPaths.getConfigDir();
            Files.createDirectories(configDir);
            Path secretFile = configDir.resolve(".device_entropy");
            byte[] keyBytes;
            if (Files.exists(secretFile)) {
                keyBytes = Files.readAllBytes(secretFile);
                if (keyBytes.length == 32) {
                    return new SecretKeySpec(keyBytes, "AES");
                }
            }
            keyBytes = new byte[32];
            SECURE_RANDOM.nextBytes(keyBytes);
            try {
                Files.write(secretFile, keyBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    java.nio.file.attribute.PosixFileAttributeView posix = Files.getFileAttributeView(
                            secretFile, java.nio.file.attribute.PosixFileAttributeView.class);
                    if (posix != null) {
                        posix.setPermissions(java.util.Set.of(
                                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                        ));
                    }
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            byte[] fallback = new byte[32];
            byte[] userBytes = (System.getProperty("user.name", "user") + "|" + System.getProperty("user.home", "/")).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(STATIC_SALT, 0, fallback, 0, Math.min(STATIC_SALT.length, 16));
            for (int i = 0; i < userBytes.length && i < 16; i++) {
                fallback[16 + i] = (byte) (userBytes[i] ^ (i * 31));
            }
            return new SecretKeySpec(fallback, "AES");
        }
    }

    private static String buildStableDeviceEntropy() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("user.name", "unknown_user")).append("|");
        sb.append(System.getProperty("os.name", "unknown_os")).append("|");
        sb.append(System.getProperty("os.arch", "unknown_arch")).append("|");
        sb.append(System.getProperty("user.home", "/")).append("|");

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                List<NetworkInterface> list = Collections.list(interfaces);
                List<NetworkInterface> physicalCandidates = new java.util.ArrayList<>();
                for (NetworkInterface nif : list) {
                    if (nif.isLoopback() || nif.isVirtual()) continue;
                    String name = nif.getName().toLowerCase();
                    if (name.startsWith("awdl") || name.startsWith("llw") || name.startsWith("p2p")
                            || name.startsWith("bridge") || name.startsWith("utun") || name.startsWith("tun")
                            || name.startsWith("tap") || name.startsWith("docker") || name.startsWith("veth")
                            || name.startsWith("tailscale") || name.startsWith("wg")) {
                        continue;
                    }
                    byte[] mac = nif.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        physicalCandidates.add(nif);
                    }
                }

                if (!physicalCandidates.isEmpty()) {
                    physicalCandidates.sort(Comparator.comparing(NetworkInterface::getName));
                    NetworkInterface chosen = physicalCandidates.get(0);
                    for (NetworkInterface cand : physicalCandidates) {
                        String name = cand.getName().toLowerCase();
                        if (name.equals("en0") || name.equals("eth0") || name.equals("wlan0")) {
                            chosen = cand;
                            break;
                        }
                    }
                    byte[] mac = chosen.getHardwareAddress();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                } else {
                    list.sort(Comparator.comparing(NetworkInterface::getName));
                    for (NetworkInterface nif : list) {
                        if (nif.isLoopback()) continue;
                        byte[] mac = nif.getHardwareAddress();
                        if (mac != null && mac.length > 0) {
                            for (byte b : mac) {
                                sb.append(String.format("%02X", b));
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return sb.toString();
    }

    private static String buildLegacyDeviceEntropy() {
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
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        return sb.toString();
    }
}
