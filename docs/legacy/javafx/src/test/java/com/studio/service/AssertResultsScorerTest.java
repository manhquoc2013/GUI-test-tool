package com.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssertResultsScorerTest {

    @TempDir
    Path tempDir;

    @Test
    void countsFailedEvenWhenProcessWouldExitZero() throws Exception {
        Path file = tempDir.resolve("asserts.json");
        Files.writeString(file, """
                {"index":0,"type":"TITLE_CONTAINS","status":"PASSED","message":"ok"}
                {"index":1,"type":"URL_CONTAINS","status":"FAILED","message":"missing"}
                {"index":2,"type":"TEXT_VISIBLE","status":"PASSED","message":"ok"}
                """);

        assertEquals(1, AssertResultsScorer.countFailures(file));
        assertEquals(1, AssertResultsScorer.countFailuresFromContent(Files.readString(file)));
        assertEquals(3, AssertResultsScorer.loadResults(file).size());
    }

    @Test
    void zeroWhenFileMissingOrEmpty() {
        assertEquals(0, AssertResultsScorer.countFailures(tempDir.resolve("missing.json")));
        assertEquals(0, AssertResultsScorer.countFailuresFromContent(""));
        assertEquals(0, AssertResultsScorer.countFailuresFromContent(null));
    }
}
