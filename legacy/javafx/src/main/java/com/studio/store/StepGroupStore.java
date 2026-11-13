package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.studio.model.StepGroup;
import com.studio.util.EntityTime;
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
 * Persistence service for Reusable Step Groups (Testsigma-inspired).
 * Stores modular step collections under data/step-groups/.
 */
public class StepGroupStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storageDir;

    public StepGroupStore() {
        this(StudioPaths.getDataDir().resolve("step-groups"));
    }

    public StepGroupStore(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException ignored) {}
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public StepGroup save(StepGroup group) throws IOException {
        if (group == null) {
            throw new IllegalArgumentException("StepGroup cannot be null");
        }
        if (group.getId() == null || group.getId().isBlank()) {
            group.setId("sg-" + UUID.randomUUID().toString().substring(0, 8));
        }

        String now = EntityTime.nowIso();
        if (group.getCreatedAt() == null || group.getCreatedAt().isBlank()) {
            group.setCreatedAt(now);
        }
        group.setUpdatedAt(now);

        Files.createDirectories(storageDir);
        Path targetFile = storageDir.resolve(group.getId() + ".json");
        ProjectStore.writeJsonAtomic(targetFile, group);
        return group;
    }

    public Optional<StepGroup> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        Path targetFile = storageDir.resolve(id + ".json");
        if (!Files.exists(targetFile)) {
            return Optional.empty();
        }
        try {
            StepGroup group = mapper.readValue(targetFile.toFile(), StepGroup.class);
            return Optional.ofNullable(group);
        } catch (IOException e) {
            System.err.printf("[StepGroupStore] Failed to read %s: %s%n", targetFile, e.getMessage());
            return Optional.empty();
        }
    }

    public List<StepGroup> findAll() {
        if (!Files.exists(storageDir)) {
            return Collections.emptyList();
        }
        List<StepGroup> list = new ArrayList<>();
        try (Stream<Path> stream = Files.list(storageDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    StepGroup g = mapper.readValue(p.toFile(), StepGroup.class);
                    if (g != null) list.add(g);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            System.err.println("[StepGroupStore] Failed to list groups: " + e.getMessage());
        }
        return list;
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        Path targetFile = storageDir.resolve(id + ".json");
        try {
            return Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            System.err.printf("[StepGroupStore] Failed to delete %s: %s%n", targetFile, e.getMessage());
            return false;
        }
    }
}
