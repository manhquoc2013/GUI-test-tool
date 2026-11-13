package com.studio.service;

import java.util.Locale;
import java.util.Set;

/**
 * Decides when the multi-agent path may take over a turn.
 *
 * <p>Delegation used to run for every prompt and handed the sub-agents no documents, no DOM and no
 * retrieved context, so a grounded request could lose all of its evidence. A turn is now delegated
 * only when the intent needs artefacts and at least one grounding source exists — or when the user
 * explicitly asks for the multi-agent pipeline.</p>
 */
public final class AiDelegationPolicy {

    private static final Set<AiAmbiguityAnalyzer.Intent> DELEGABLE_INTENTS = Set.of(
            AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
            AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
            AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
            AiAmbiguityAnalyzer.Intent.GAP_ANALYSIS
    );

    private static final String[] EXPLICIT_MULTI_AGENT_MARKERS = {
            "đa tác nhân", "da tac nhan", "multi-agent", "multi agent",
            "/agent", "/loop", "coordinator", "loop engineer", "reality checker",
            "spec miner", "test architect", "điều phối", "dieu phoi"
    };

    private AiDelegationPolicy() {
    }

    public static boolean shouldDelegate(AiAmbiguityAnalyzer.Intent intent,
                                         boolean hasDocuments,
                                         boolean hasDom,
                                         String prompt) {
        if (intent == null || !DELEGABLE_INTENTS.contains(intent)) {
            return false;
        }
        if (hasDocuments || hasDom) {
            return true;
        }
        return hasExplicitMultiAgentRequest(prompt);
    }

    static boolean hasExplicitMultiAgentRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String marker : EXPLICIT_MULTI_AGENT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
