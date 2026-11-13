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
    /** A markdown table row is data, never a section name. */
    private static final String TABLE_ROW_PREFIX = "|";
    /** A horizontal rule carries no name either. */
    private static final String SEPARATOR_PREFIX = "---";
    /** Plain-text documents number their sections ("4.2 Luồng xử lý") instead of using '#' headings. */
    private static final java.util.regex.Pattern SECTION_TITLE_PATTERN =
            java.util.regex.Pattern.compile("^\\d+(?:\\.\\d+)*[.)]?\\s+\\S.*");

    /**
     * One batch of material.
     *
     * @param index zero-based position in the whole material
     * @param total number of batches this material was split into
     * @param label human-readable section name: its first heading, the heading it continues,
     *              or a numbered section title for plain-text documents
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
        List<Batch> chunks = chunk(material, budget);
        List<Section> sections = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Batch batch = chunks.get(i);
            sections.add(new Section(i, chunks.size(),
                    labelOf(batch.body(), batch.headingBefore(), i), batch.body()));
        }
        return sections;
    }

    /** One chunk plus the heading that was in force when the chunk began. */
    private record Batch(String body, String headingBefore) {
    }

    /**
     * Groups lines into chunks no longer than {@code maxChars}, preferring heading boundaries, and
     * remembers the heading each chunk continues so a chunk that starts mid-table still has a name.
     */
    private static List<Batch> chunk(String material, int maxChars) {
        String[] lines = material.split("\\r\\n|\\r|\\n", -1);
        List<Batch> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String lastHeading = null;
        String headingAtStart = null;
        for (String line : lines) {
            for (String piece : fitLine(line, maxChars)) {
                boolean overflows = current.length() > 0
                        && current.length() + piece.length() + LINE_BREAK.length() > maxChars;
                // Keep a heading at the start of its chunk when the current one is already half full:
                // a chunk that begins mid-section would lose the screen it belongs to.
                boolean headingStartsChunk = piece.startsWith(HEADING_PREFIX)
                        && current.length() * 2 >= maxChars;
                if (overflows || headingStartsChunk) {
                    flush(chunks, current, headingAtStart);
                    headingAtStart = lastHeading;
                }
                if (current.length() == 0) {
                    headingAtStart = lastHeading;
                } else {
                    current.append(LINE_BREAK);
                }
                current.append(piece);
                if (piece.startsWith(HEADING_PREFIX)) {
                    lastHeading = stripHeading(piece);
                }
            }
        }
        flush(chunks, current, headingAtStart);
        if (chunks.isEmpty()) {
            chunks.add(new Batch(material, null));
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

    private static void flush(List<Batch> chunks, StringBuilder current, String headingBefore) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(new Batch(current.toString(), headingBefore));
        current.setLength(0);
    }

    /**
     * The batch's first heading, else the heading it continues, else its first non-empty line.
     *
     * <p>A batch that continues a table starts with a data row, and the old first-line rule labelled
     * it "| 8 | Quản trị viên | …" — unreadable in the merged report. A heading inside the batch wins;
     * otherwise the heading in force when the batch began names the section it belongs to.</p>
     */
    private static String labelOf(String body, String headingBefore, int index) {
        String firstProseLine = null;
        String firstSectionTitle = null;
        for (String raw : body.split("\\r?\\n")) {
            String stripped = raw.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.startsWith(HEADING_PREFIX)) {
                return shorten(stripHeading(stripped));
            }
            boolean tableRow = stripped.startsWith(TABLE_ROW_PREFIX);
            boolean separator = stripped.startsWith(SEPARATOR_PREFIX);
            if (!tableRow && !separator) {
                if (firstProseLine == null) {
                    firstProseLine = stripped.replace("*", "").strip();
                }
                if (firstSectionTitle == null
                        && stripped.length() <= LABEL_MAX_CHARS * 2
                        && SECTION_TITLE_PATTERN.matcher(stripped).matches()) {
                    firstSectionTitle = stripped.replace("*", "").strip();
                }
            }
        }
        if (headingBefore != null) {
            return shorten(headingBefore);
        }
        if (firstSectionTitle != null) {
            return shorten(firstSectionTitle);
        }
        return firstProseLine != null ? shorten(firstProseLine) : FALLBACK_LABEL + " " + (index + 1);
    }

    /** A heading line reduced to its readable text. */
    private static String stripHeading(String line) {
        return line.replaceFirst("^#{1,6}\\s*", "").replace("*", "").strip();
    }

    /** Keeps a label short enough for a merge header. */
    private static String shorten(String label) {
        return label.length() > LABEL_MAX_CHARS ? label.substring(0, LABEL_MAX_CHARS - 1) + "…" : label;
    }
}
