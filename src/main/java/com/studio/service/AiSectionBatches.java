package com.studio.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits one agent run's material into section-sized batches.
 *
 * <p>A single reply is bounded by the model's output ceiling (8192 tokens for the DeepSeek family,
 * roughly 17 000 characters), so a request whose material is larger than that can never be answered
 * in one turn. The old code truncated the document to a share of the context window — the log showed
 * 14 312 of 49 428 characters delivered, 29 % — and every section after the cut was analysed by
 * nobody. Batching keeps the whole document reachable: each batch is answered in its own bounded
 * reply, and the answers are merged under the section they belong to.</p>
 *
 * <p>Splitting is deterministic and line-safe: a chunk never ends mid-line, headings start a new
 * chunk when they fit, and a block with no headings at all is cut at line boundaries so the caller
 * cannot loop forever on one over-long paragraph.</p>
 */
public final class AiSectionBatches {

    /** Shortest batch worth a model call; below this the fixed prompt overhead dominates. */
    public static final int MIN_BATCH_CHARS = 1_000;

    /** Fallback label for material whose first line is not a readable heading. */
    private static final String FALLBACK_LABEL = "Phần";
    private static final int LABEL_MAX_CHARS = 80;
    private static final String LINE_BREAK = "\n";
    private static final String HEADING_PREFIX = "#";

    /**
     * One batch of material.
     *
     * @param index zero-based position in the whole material
     * @param total number of batches this material was split into
     * @param label human-readable section name, taken from the batch's first heading
     * @param body  the batch text itself, with all its original lines
     */
    public record Section(int index, int total, String label, String body) {
    }

    private AiSectionBatches() {
    }

    /**
     * Splits {@code material} into batches of at most {@code maxChars} characters.
     *
     * @return one section when the material already fits (so callers keep the single-call path),
     *         an empty list when there is nothing to send
     */
    public static List<Section> split(String material, int maxChars) {
        if (material == null || material.isBlank()) {
            return List.of();
        }
        int budget = Math.max(MIN_BATCH_CHARS, maxChars);
        List<String> chunks = chunk(material, budget);
        List<Section> sections = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String body = chunks.get(i);
            sections.add(new Section(i, chunks.size(), labelOf(body, i), body));
        }
        return sections;
    }

    /** Groups lines into chunks no longer than {@code maxChars}, preferring heading boundaries. */
    private static List<String> chunk(String material, int maxChars) {
        String[] lines = material.split("\\r\\n|\\r|\\n", -1);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            for (String piece : fitLine(line, maxChars)) {
                boolean overflows = current.length() > 0
                        && current.length() + piece.length() + LINE_BREAK.length() > maxChars;
                // Keep a heading at the start of its chunk when the current one is already half full:
                // a chunk that begins mid-section would be labelled by body text instead of the screen.
                boolean headingStartsChunk = piece.startsWith(HEADING_PREFIX)
                        && current.length() * 2 >= maxChars;
                if (overflows || headingStartsChunk) {
                    flush(chunks, current);
                }
                if (current.length() > 0) {
                    current.append(LINE_BREAK);
                }
                current.append(piece);
            }
        }
        flush(chunks, current);
        if (chunks.isEmpty()) {
            chunks.add(material);
        }
        return chunks;
    }

    /**
     * A single source line may itself exceed the batch budget (a minified JSON body, one long table
     * row). It is cut into budget-sized pieces here so the chunker always makes progress.
     */
    private static List<String> fitLine(String line, int maxChars) {
        if (line.length() <= maxChars) {
            return List.of(line);
        }
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(line.length(), start + maxChars);
            if (end < line.length()) {
                int space = line.lastIndexOf(' ', end);
                if (space > start) {
                    end = space;
                }
            }
            pieces.add(line.substring(start, end));
            start = end;
            while (start < line.length() && line.charAt(start) == ' ') {
                start++;
            }
        }
        return pieces;
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString());
        current.setLength(0);
    }

    /** The batch's first heading, or its first non-empty line, shortened for a merge header. */
    private static String labelOf(String body, int index) {
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.replaceFirst("^#{1,6}\\s*", "").replace("*", "").strip();
            if (line.isEmpty()) {
                continue;
            }
            return line.length() > LABEL_MAX_CHARS ? line.substring(0, LABEL_MAX_CHARS - 1) + "…" : line;
        }
        return FALLBACK_LABEL + " " + (index + 1);
    }
}
