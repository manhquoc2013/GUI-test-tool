package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.Assertion;
import com.studio.runtime.StudioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssertReconciliationTest {

    @BeforeEach
    void setUp() {
        StudioRuntime.clearRecordedAsserts();
    }

    @AfterEach
    void tearDown() {
        StudioRuntime.clearRecordedAsserts();
    }

    @Test
    void testReconcileAssertsOnExecutionFailure() throws Exception {
        Assertion a1 = new Assertion();
        a1.setType("ELEMENT_VISIBLE");
        a1.setLocator(".error-message, [role='alert']");
        a1.setStatus("ACTIVE");

        Assertion a2 = new Assertion();
        a2.setType("ELEMENT_HAS_TEXT");
        a2.setLocator(".error-message, [role='alert']");
        a2.setValue("Invalid");
        a2.setStatus("ACTIVE");

        // Simulate StudioAssertions recording a1 as PASSED before a2 fails
        StudioRuntime.recordAssert(new AssertResult(0, "ELEMENT_VISIBLE", a1.getLocator(), "ACTIVE", "PASSED", null));

        TestRunnerService service = new TestRunnerService(null, null, null, null, null);
        Method reconcileMethod = TestRunnerService.class.getDeclaredMethod(
                "reconcileAssertResults",
                Path.class, List.class, List.class, Throwable.class, String.class);
        reconcileMethod.setAccessible(true);

        AssertionError failure = new AssertionError("Locator expected to contain text: Invalid");
        @SuppressWarnings("unchecked")
        List<AssertResult> results = (List<AssertResult>) reconcileMethod.invoke(
                service,
                null, // no file written
                List.of(a1, a2),
                List.of(a1, a2),
                failure,
                "Locator expected to contain text: Invalid"
        );

        assertNotNull(results);
        assertEquals(2, results.size());

        // First assert should be PASSED
        assertEquals("PASSED", results.get(0).getStatus());
        assertEquals("ELEMENT_VISIBLE", results.get(0).getType());

        // Second assert should be FAILED with error message, not missing or empty!
        assertEquals("FAILED", results.get(1).getStatus());
        assertEquals("ELEMENT_HAS_TEXT", results.get(1).getType());
        assertTrue(results.get(1).getMessage().contains("Invalid"));
    }

    @Test
    void testReconcileAssertsWhenScriptCrashesBeforeAssertions() throws Exception {
        Assertion a1 = new Assertion();
        a1.setType("ELEMENT_VISIBLE");
        a1.setLocator("button#submit");
        a1.setStatus("ACTIVE");

        TestRunnerService service = new TestRunnerService(null, null, null, null, null);
        Method reconcileMethod = TestRunnerService.class.getDeclaredMethod(
                "reconcileAssertResults",
                Path.class, List.class, List.class, Throwable.class, String.class);
        reconcileMethod.setAccessible(true);

        RuntimeException crash = new RuntimeException("Timeout 30000ms waiting for page navigation");
        @SuppressWarnings("unchecked")
        List<AssertResult> results = (List<AssertResult>) reconcileMethod.invoke(
                service,
                null,
                List.of(a1),
                List.of(a1),
                crash,
                "Timeout 30000ms waiting for page navigation"
        );

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("SKIPPED", results.get(0).getStatus());
        assertTrue(results.get(0).getMessage().contains("Timeout 30000ms"));
    }
}

