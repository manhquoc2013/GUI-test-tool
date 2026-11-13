package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.studio.model.TestDataProfile;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persistence service for Test Data Profiles (Automa & Testsigma inspired).
 * Stores data-driven test datasets and variable profiles under data/test-data/.
 */
public class TestDataProfileStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storageDir;

    public TestDataProfileStore() {
        this(StudioPaths.getDataDir().resolve("test-data"));
    }

    public TestDataProfileStore(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException ignored) {}
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public TestDataProfile save(TestDataProfile profile) throws IOException {
        if (profile == null) {
            throw new IllegalArgumentException("TestDataProfile cannot be null");
        }
        if (profile.getId() == null || profile.getId().isBlank()) {
            profile.setId("tdp-" + UUID.randomUUID().toString().substring(0, 8));
        }

        Files.createDirectories(storageDir);
        Path targetFile = storageDir.resolve(profile.getId() + ".json");
        ProjectStore.writeJsonAtomic(targetFile, profile);
        return profile;
    }

    public Optional<TestDataProfile> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Path targetFile = storageDir.resolve(id + ".json");
        if (!Files.exists(targetFile)) {
            return Optional.empty();
        }
        try {
            TestDataProfile profile = mapper.readValue(targetFile.toFile(), TestDataProfile.class);
            return Optional.ofNullable(profile);
        } catch (IOException e) {
            System.err.printf("[TestDataProfileStore] Failed to read %s: %s%n", targetFile, e.getMessage());
            return Optional.empty();
        }
    }

    public List<TestDataProfile> findAll() {
        if (!Files.exists(storageDir)) {
            return Collections.emptyList();
        }
        List<TestDataProfile> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    TestDataProfile pObj = mapper.readValue(p.toFile(), TestDataProfile.class);
                    if (pObj != null) list.add(pObj);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            System.err.println("[TestDataProfileStore] Failed to list test data profiles: " + e.getMessage());
        }
        return list;
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        Path targetFile = storageDir.resolve(id + ".json");
        try {
            return Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            System.err.printf("[TestDataProfileStore] Failed to delete %s: %s%n", targetFile, e.getMessage());
            return false;
        }
    }
}

