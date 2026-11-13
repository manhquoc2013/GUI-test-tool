package com.studio.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The empty-buffer hint is the only text a case with no script shows, and the editor paints it against
 * its own background instead of the theme's control background. A stock prompt colour is therefore a
 * guess: on the dark editor it can land too close to the background to read. The fill is pinned to a
 * themed token whose contrast is measured here, in both themes.
 */
class CodeEditorPromptStyleTest {

    private static final Pattern EDITOR_RULE = Pattern.compile(
            "(\\.playwright-code-area\\s+\\.code-editor-inner\\s*\\{[^}]*})");
    private static final Pattern PROMPT_FILL = Pattern.compile("-fx-prompt-text-fill:\\s*([^;]+);");
    private static final String MUTED_TOKEN = "-color-fg-muted";

    @Test
    void noPromptFillIsAPinnedColour() throws IOException {
        // A literal prompt colour cannot follow the theme: the same #565f89 reads at 3:1 on the dark field
        // and 6:1 on the light one. Prompts belong to the token, so they answer to the theme.
        for (String resource : new String[]{"/css/studio.css", "/css/studio-light.css"}) {
            Matcher literal = Pattern.compile("-fx-prompt-text-fill:\\s*#[0-9a-fA-F]{3,8}").matcher(read(resource));
            assertFalse(literal.find(), "Pinned prompt colour in " + resource + ": "
                    + (literal.reset().find() ? literal.group() : ""));
        }
    }

    @Test
    void theEditorPromptFillIsAThemedTokenThatReadsInBothThemes() throws IOException {
        String css = read("/css/studio.css");
        Matcher rule = EDITOR_RULE.matcher(css);
        assertTrue(rule.find(), "studio.css must style .playwright-code-area .code-editor-inner");

        Matcher fill = PROMPT_FILL.matcher(rule.group(1));
        assertTrue(fill.find(), "The editor rule must set -fx-prompt-text-fill: " + rule.group(1));
        assertTrue(MUTED_TOKEN.equals(fill.group(1).trim()),
                "The hint uses the themed muted token, not a literal colour: " + fill.group(1));

        double darkContrast = contrast(token(css, MUTED_TOKEN), token(css, "-color-bg-default"));
        double lightContrast = contrast(token(read("/css/studio-light.css"), MUTED_TOKEN),
                token(read("/css/studio-light.css"), "-color-bg-default"));

        assertTrue(darkContrast >= 4.5, "Prompt contrast in the dark theme: " + darkContrast);
        assertTrue(lightContrast >= 4.5, "Prompt contrast in the light theme: " + lightContrast);
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = CodeEditorPromptStyleTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing stylesheet " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The first {@code #rrggbb} a token is declared with; the theme files override their tokens at the top. */
    private static int[] token(String css, String name) {
        Matcher m = Pattern.compile(Pattern.quote(name) + ":\\s*#([0-9a-fA-F]{6})").matcher(css);
        assertTrue(m.find(), "Token " + name + " must be declared as #rrggbb");
        String hex = m.group(1);
        return new int[]{Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)};
    }

    private static double contrast(int[] a, int[] b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG 2.x relative luminance. */
    private static double relativeLuminance(int[] rgb) {
        double[] channel = new double[3];
        double[] weight = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double v = rgb[i] / 255.0;
            channel[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return weight[0] * channel[0] + weight[1] * channel[1] + weight[2] * channel[2];
    }
}
