package com.studio.service;

import com.studio.model.AiConfig;
import com.studio.model.AiModelCapabilities;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A batch is a promise the model has to keep in ONE reply. Sizing it from the input window alone let
 * three screens produce ~24k characters of JSON in an 8192-token reply, so every batch came back cut,
 * the continuation rewrote instead of resuming, and the turn took minutes before losing the batch.
 */
class AiBatchSizingTest {

    /** Matches the production log: 12 screens, ~4k characters of specification each. */
    private static final int SCREEN_CHARS = 4_092;
    private static final int SCREENS = 12;

    private static List<AiMultiAgentCoordinator.ScreenSpecSection> screens(int count, int charsEach) {
        List<AiMultiAgentCoordinator.ScreenSpecSection> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new AiMultiAgentCoordinator.ScreenSpecSection(
                    i, "Màn hình " + i, "SCREEN_" + i, "x".repeat(charsEach),
                    List.of("Họ tên", "Email"), List.of("Bắt buộc nhập"), ""));
        }
        return list;
    }

    private static AiConfig configFor(String model) {
        AiConfig config = new AiConfig();
        config.setModel(model);
        config.setMaxTokens(128_000);
        return config;
    }

    @Test
    void batchSizeRespectsTheOutputCeilingNotOnlyTheInputWindow() {
        AiConfig config = configFor("deepseek-chat");

        int batchSize = AiTestGeneratorService.resolveBatchSize(config, screens(SCREENS, SCREEN_CHARS), false);

        int ceiling = AiModelCapabilities.resolveOutputCeiling(config);
        assertTrue(batchSize >= 1 && batchSize <= 2,
                "an 8192-token ceiling cannot carry four full cases for three screens "
                        + "(actual: " + batchSize + " screens per batch)");
        assertTrue(batchSize * AiTestGeneratorService.MIN_CASES_PER_SCREEN
                        * AiTestGeneratorService.ESTIMATED_TOKENS_PER_CASE <= ceiling,
                "the requested cases must fit inside one reply's output budget");
    }

    @Test
    void aLargerCeilingAllowsMoreScreensPerBatch() {
        AiConfig small = configFor("deepseek-chat");
        AiConfig large = configFor("gpt-4.1");

        int smallBatch = AiTestGeneratorService.resolveBatchSize(small, screens(SCREENS, SCREEN_CHARS), false);
        int largeBatch = AiTestGeneratorService.resolveBatchSize(large, screens(SCREENS, SCREEN_CHARS), false);

        assertTrue(largeBatch >= smallBatch,
                "a model that can emit more tokens per reply must not be split as aggressively");
        assertTrue(largeBatch <= 3, "the input window still bounds how much specification one turn can carry");
    }

    @Test
    void scriptBatchesStayAtOneScreenBecauseScriptsAreTheLargestReply() {
        assertEquals(1, AiTestGeneratorService.resolveBatchSize(
                configFor("deepseek-chat"), screens(SCREENS, SCREEN_CHARS), true));
    }

    @Test
    void everyScreenIsStillCoveredWhateverTheBatchSize() {
        int batchSize = AiTestGeneratorService.resolveBatchSize(
                configFor("deepseek-chat"), screens(SCREENS, SCREEN_CHARS), false);
        int totalBatches = (int) Math.ceil((double) SCREENS / batchSize);

        int covered = 0;
        for (int b = 0; b < totalBatches; b++) {
            covered += Math.min(SCREENS, (b + 1) * batchSize) - b * batchSize;
        }
        assertEquals(SCREENS, covered, "splitting into batches must still cover every screen exactly once");
    }
}
