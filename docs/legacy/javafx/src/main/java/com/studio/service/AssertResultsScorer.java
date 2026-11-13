package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AssertResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses NDJSON assert-results written by soft-assert harness. */
public final class AssertResultsScorer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssertResultsScorer() {
    }

    public static int countFailures(Path assertResultsFile) {
        try {
            if (assertResultsFile == null || !Files.exists(assertResultsFile)) {
                return 0;
            }
            return countFailuresFromContent(Files.readString(assertResultsFile));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int countFailuresFromContent(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : content.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(line);
                if (node.has("status") && "FAILED".equals(node.get("status").asText())) {
                    count++;
                }
            } catch (Exception ignored) {
            }
        }
        return count;
    }

    public static List<AssertResult> loadResults(Path assertResultsFile) {
        List<AssertResult> results = new ArrayList<>();
        try {
            if (assertResultsFile == null || !Files.exists(assertResultsFile)) {
                return results;
            }
            String content = Files.readString(assertResultsFile);
            for (String line : content.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                results.add(MAPPER.readValue(line, AssertResult.class));
            }
        } catch (Exception ignored) {
        }
        return results;
    }
}
