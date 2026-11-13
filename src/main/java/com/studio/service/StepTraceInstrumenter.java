package com.studio.service;

import com.studio.util.JavaBodyScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stamps a recorded script with one marker per step so a run can report which step did what.
 *
 * <p>A step number means the same thing in two places: the number printed on a builder card, and the
 * number {@link com.studio.runtime.StepTracer} reports. Both are "the n-th non-blank line of the
 * script body", because {@link com.studio.ui.StepScriptBidirectionalSync} parses a script line by
 * line and both sides locate the body through {@link JavaBodyScanner}. Numbering here therefore uses
 * the same walk, and a marker is inserted before each step's first line:</p>
 *
 * <pre>
 * StudioRuntime.stepEnter(3);
 * page.locator("#login").click();
 * </pre>
 *
 * <p>A statement spread over several lines is not a new step: continuation lines are detected and
 * only the first line carries the marker, while the whole statement is what the report shows.</p>
 *
 * <p>Instrumentation runs before variable resolution and step-group expansion, so a
 * {@code @include} card stays ONE step even when it expands into many statements.</p>
 */
public final class StepTraceInstrumenter {

    /** Marker call prefix; the step number and closing paren complete it. */
    private static final String MARKER_PREFIX = "com.studio.runtime.StudioRuntime.stepEnter(";

    /** Body lines that continue a construct and must never be preceded by a statement of their own. */
    private static final Set<String> CONTINUATION_KEYWORDS = Set.of(
            "else", "catch", "finally", "case", "default");

    private StepTraceInstrumenter() {
    }

    /**
     * @param script     instrumented script, ready for the existing transform pipeline
     * @param statements one entry per step, index 0 unused, so a step number reads its own slot
     */
    public record Instrumented(String script, List<String> statements) {
    }

    /**
     * @return {@code script} with a tracer call before each step, plus the statement text of every
     *         step (used to describe it in a report after the code has already run)
     */
    public static Instrumented instrument(String script) {
        List<String> lines = splitLines(script);
        List<String> statements = new ArrayList<>();
        statements.add("");
        if (lines.isEmpty()) {
            return new Instrumented(script != null ? script : "", statements);
        }
        JavaBodyScanner.Body body = JavaBodyScanner.scan(lines);

        List<String> out = new ArrayList<>(lines.size() + 16);
        out.addAll(lines.subList(0, body.headerEnd()));

        String previousSignificant = null;
        for (int i = body.headerEnd(); i < body.footerStart(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                out.add(line != null ? line : "");
                continue;
            }
            String trimmed = line.strip();
            // One card per non-blank body line, exactly like the builder's parser. Anything else
            // makes the card number and the traced number disagree, and a verdict then lands on the
            // wrong step. A wrapped statement therefore keeps counting as several steps here.
            int step = statements.size();
            statements.add(trimmed);

            // Only a line that can legally host a statement may carry a marker: before a
            // continuation line (or between "}" and "else") Java would not compile.
            if (!startsContinuation(previousSignificant, trimmed) && !isQuietComment(trimmed)) {
                out.add(indentation(line) + MARKER_PREFIX + step + ");");
            }
            out.add(line);
            previousSignificant = trimmed;
        }
        out.addAll(lines.subList(body.footerStart(), lines.size()));
        return new Instrumented(String.join("\n", out) + (script != null && script.endsWith("\n") ? "\n" : ""),
                statements);
    }

    /**
     * @return true when {@code trimmed} continues the previous line rather than beginning a
     *         statement of its own, so no marker may be inserted before it
     */
    private static boolean startsContinuation(String previousSignificant, String trimmed) {
        if (previousSignificant == null) {
            return false;
        }
        return !endsStatement(previousSignificant) || startsContinuationKeyword(trimmed);
    }

    /** @return the leading whitespace of {@code line}, so an inserted marker keeps the indentation. */
    private static String indentation(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    /**
     * @return true when {@code line} closes a statement, meaning the next line starts a new one. A
     *         line that is only a brace opens a block, so what follows is still a new statement.
     *         A comment closes nothing syntactically, but the line after it is always a fresh
     *         statement — without this a note would swallow the step that follows it.
     */
    private static boolean endsStatement(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (isComment(trimmed)) {
            return true;
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        return last == ';' || last == '{' || last == '}';
    }

    private static boolean isComment(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    /** @return true for a comment line that produces no code, so it needs no start marker. */
    private static boolean isQuietComment(String trimmed) {
        return isComment(trimmed) && !trimmed.contains("@include(");
    }

    private static boolean startsContinuationKeyword(String trimmed) {
        String head = trimmed.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (CONTINUATION_KEYWORDS.contains(head)) {
            return true;
        }
        return trimmed.startsWith("}") && trimmed.length() > 1;
    }

    private static List<String> splitLines(String script) {
        List<String> lines = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return lines;
        }
        for (String line : script.split("\r\n|\r|\n", -1)) {
            lines.add(line);
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
