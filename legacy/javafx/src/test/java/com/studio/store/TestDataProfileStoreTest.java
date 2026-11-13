package com.studio.store;

import com.studio.model.TestDataProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestDataProfileStoreTest {

    private Path tempDir;
    private TestDataProfileStore store;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-data-store-test-");
        store = new TestDataProfileStore(tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            }
        }
    }

    @Test
    void testSaveFindAllAndDelete() throws IOException {
        TestDataProfile profile = new TestDataProfile("tdp_auth", "Auth Credentials");
        profile.setDescription("Staging authentication accounts");
        profile.addVariable("BASE_URL", "https://staging.test.com");
        profile.addVariable("API_KEY", "test-secret-key");
        profile.addDataRow(Map.of("user", "admin", "role", "SUPERUSER"));
        profile.addDataRow(Map.of("user", "qa_user", "role", "TESTER"));

        TestDataProfile saved = store.save(profile);
        assertNotNull(saved.getId());
        assertEquals("tdp_auth", saved.getId());

        Optional<TestDataProfile> loaded = store.findById("tdp_auth");
        assertTrue(loaded.isPresent());
        assertEquals("Auth Credentials", loaded.get().getName());
        assertEquals("https://staging.test.com", loaded.get().getVariable("BASE_URL"));
        assertEquals(2, loaded.get().getDataRows().size());

        List<TestDataProfile> all = store.findAll();
        assertEquals(1, all.size());

        boolean deleted = store.delete("tdp_auth");
        assertTrue(deleted);
        assertTrue(store.findById("tdp_auth").isEmpty());
    }

    @Test
    void testAutoGeneratesIdWhenMissing() throws IOException {
        TestDataProfile profile = new TestDataProfile(null, "Generated ID Profile");
        TestDataProfile saved = store.save(profile);
        assertNotNull(saved.getId());
        assertTrue(saved.getId().startsWith("tdp-"));
    }
}

