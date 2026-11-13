package com.studio.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How much of a transcript message the chat bubble shows by default.
 *
 * <p>A sub-agent answer is long prose (often several thousand characters with markdown headings and
 * tables). Rendering it in full drowns the conversation, so the bubble shows an excerpt and keeps the
 * whole message behind a "Xem thêm" toggle. Nothing is discarded: the stored message content is never
 * rewritten, only the label is clipped.</p>
 */
public enum ChatDisplayMode {

    /** One status line per message. */
    COMPACT("Gọn", 1, 180),
    /** A short executive excerpt (headings stripped, blank/table lines dropped). */
    SUMMARY("Tổng hợp", 8, 900),
    /** The whole message, without clipping. */
    DETAIL("Chi tiết", Integer.MAX_VALUE, Integer.MAX_VALUE);

    /** Marker appended when an excerpt was clipped, so the reader knows text is hidden. */
    public static final String ELLIPSIS = "…";

    private static final Pattern FENCED_CODE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern HEADING_MARKS = Pattern.compile("^#{1,6}\\s*");
    private static final Pattern EMPHASIS_MARKS = Pattern.compile("\\*\\*|__|`");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|.*\\|$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{2,}.*$");
    private static final Pattern RULE_LINE = Pattern.compile("^[-=*_]{3,}$");

    private final String label;
    private final int maxLines;
    private final int maxChars;

    ChatDisplayMode(String label, int maxLines, int maxChars) {
        this.label = label;
        this.maxLines = maxLines;
        this.maxChars = maxChars;
    }

    /** Vietnamese label shown in the selector. */
    public String getLabel() {
        return label;
    }

    /** Stable key persisted in settings. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a persisted key, falling back to {@link #SUMMARY} for unknown or missing values. */
    public static ChatDisplayMode fromKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUMMARY;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ChatDisplayMode mode : values()) {
            if (mode.key().equals(normalized)) {
                return mode;
            }
        }
        return SUMMARY;
    }

    /**
     * An excerpt of {@code content} for this mode.
     *
     * @param content the stored message content (never mutated)
     * @return the text to render plus whether more text exists behind the toggle
     */
    public Excerpt excerpt(String content) {
        if (content == null || content.isBlank()) {
            return new Excerpt("", false);
        }
        if (this == DETAIL) {
            return new Excerpt(content, false);
        }

        String sanitized = FENCED_CODE.matcher(content).replaceAll("");
        StringBuilder out = new StringBuilder();
        int keptLines = 0;
        boolean clipped = false;

        for (String rawLine : sanitized.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || RULE_LINE.matcher(line).matches()
                    || TABLE_SEPARATOR.matcher(line).matches()
                    || TABLE_ROW.matcher(line).matches()) {
                continue;
            }
            line = HEADING_MARKS.matcher(line).replaceFirst("");
            line = EMPHASIS_MARKS.matcher(line).replaceAll("").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (keptLines >= maxLines) {
                clipped = true;
                break;
            }
            int separator = out.length() == 0 ? 0 : 1;
            if (out.length() + separator + line.length() > maxChars) {
                int room = maxChars - out.length() - separator;
                if (room > 0) {
                    if (separator > 0) {
                        out.append('\n');
                    }
                    out.append(line, 0, room);
                }
                clipped = true;
                break;
            }
            if (separator > 0) {
                out.append('\n');
            }
            out.append(line);
            keptLines++;
        }

        if (!clipped) {
            // Something was cleaned away (code fence, table, markdown marks) but nothing was dropped
            // by the line/char budget: this is still not the stored text, so the toggle stays useful.
            clipped = !out.toString().equals(content.trim());
        }
        String text = out.toString().trim();
        if (clipped && !text.isEmpty() && !text.endsWith(ELLIPSIS)) {
            text = text + ELLIPSIS;
        }
        return new Excerpt(text, clipped);
    }

    /** Rendered text plus whether the full message is hidden behind the toggle. */
    public record Excerpt(String text, boolean truncated) {
    }
}
