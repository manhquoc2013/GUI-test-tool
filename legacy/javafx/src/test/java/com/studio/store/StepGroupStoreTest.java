package com.studio.store;

import com.studio.model.StepGroup;
import com.studio.model.TestStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StepGroupStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void testSaveFindAllAndDelete() throws Exception {
        StepGroupStore store = new StepGroupStore(tempDir);

        StepGroup group = new StepGroup("login-flow", "Standard Login", "Logs into dashboard");
        group.setSteps(List.of(
                new TestStep(1, "NAVIGATE", "https://example.com/login", "", "Go to login", "Login page loads"),
                new TestStep(2, "FILL", "#username", "admin", "Enter username", "Username typed")
        ));

        store.save(group);

        Optional<StepGroup> loaded = store.findById("login-flow");
        assertTrue(loaded.isPresent());
        assertEquals("Standard Login", loaded.get().getName());
        assertEquals(2, loaded.get().getSteps().size());

        List<StepGroup> all = store.findAll();
        assertEquals(1, all.size());

        boolean deleted = store.delete("login-flow");
        assertTrue(deleted);
        assertTrue(store.findById("login-flow").isEmpty());
    }
}

