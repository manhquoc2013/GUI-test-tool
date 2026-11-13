package com.studio.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Locates the editable statement body of a recorded Java/Playwright script.
 *
 * <p>A codegen file is a class with a {@code main} method; the statements a builder may touch are
 * the non-blank lines between the {@code main} signature (plus the {@code Page} binding Studio
 * injects) and the trailing braces that close the envelope. Two callers need exactly that region
 * and must agree on it byte for byte: the visual builder, which numbers the statement cards, and the
 * run-time tracer, which stamps those same numbers into the executing code. Keeping the rule in one
 * place is what guarantees the number on a card is the number the tracer reports.</p>
 */
public final class JavaBodyScanner {

    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile(".*\\bstatic\\s+void\\s+main\\s*\\(.*");
    private static final Pattern PAGE_DECLARATION_PATTERN =
            Pattern.compile(".*\\bPage\\s+\\w+\\s*=.*;.*");

    /** @param headerEnd index of the first body line; @param footerStart index of the first index after the body */
    public record Body(int headerEnd, int footerStart) {

        public boolean isEmpty() {
            return footerStart <= headerEnd;
        }
    }

    private JavaBodyScanner() {
    }

    /**
     * @return the body region of {@code lines}, or an empty region spanning nothing when the script
     *         has no {@code main} method (a bare statement list, where the whole file is the body)
     */
    public static Body scan(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new Body(0, 0);
        }
        int headerEnd = findHeaderEnd(lines);
        return new Body(headerEnd, findFooterStart(lines, headerEnd));
    }

    private static int findHeaderEnd(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).strip();
            if (!MAIN_METHOD_PATTERN.matcher(trimmed).matches()) {
                continue;
            }
            int afterSignature = i + 1;
            if (!trimmed.endsWith("{")) {
                // Signature and opening brace may sit on separate lines.
                int probe = afterSignature;
                while (probe < lines.size() && lines.get(probe).isBlank()) {
                    probe++;
                }
                if (probe < lines.size() && "{".equals(lines.get(probe).strip())) {
                    afterSignature = probe + 1;
                }
            }
            // Keep the StudioRuntime page binding with the envelope: it is runtime plumbing,
            // not an authored step, and must survive every regeneration.
            int bodyStart = afterSignature;
            while (bodyStart < lines.size() && lines.get(bodyStart).isBlank()) {
                bodyStart++;
            }
            if (bodyStart < lines.size() && PAGE_DECLARATION_PATTERN.matcher(lines.get(bodyStart)).matches()) {
                return bodyStart + 1;
            }
            return afterSignature;
        }
        return 0;
    }

    private static int findFooterStart(List<String> lines, int headerEnd) {
        int start = lines.size();
        while (start > headerEnd) {
            String trimmed = lines.get(start - 1).strip();
            if (trimmed.isEmpty() || "}".equals(trimmed)) {
                start--;
            } else {
                break;
            }
        }
        return start;
    }
}
