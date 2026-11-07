package com.studio.service;

/**
 * Transforms recorded Playwright codegen scripts for in-process execution with StudioRuntime.
 * Strips browser launch/close, rewrites Page to StudioRuntime.page(), renames class,
 * and wraps the main body in try/finally for screenshots.
 */
public class ScriptHarnessTransformer {

    /**
     * @param originalCode     recorded codegen Java
     * @param sharedContext    unused (reserved for future shared-session variants)
     * @param screenshotPath   absolute path base for screenshot (without .png)
     * @param harnessClassName valid Java class name for the harness (e.g. tc_123_Run)
     */
    public static String transform(String originalCode, boolean sharedContext,
                                   String screenshotPath, String harnessClassName) {
        String code = originalCode != null ? originalCode : "";

        String className = sanitizeClassName(harnessClassName);
        code = code.replaceAll("public\\s+class\\s+\\w+", "public class " + className);

        // try (Playwright p = Playwright.create()) {  →  {
        code = code.replaceAll(
                "(?s)try\\s*\\(\\s*Playwright\\s+\\w+\\s*=\\s*Playwright\\.create\\(\\)\\s*\\)\\s*\\{",
                "{");

        // Playwright p = Playwright.create();
        code = code.replaceAll(
                "(?m)^\\s*Playwright\\s+\\w+\\s*=\\s*Playwright\\.create\\(\\)\\s*;\\s*$",
                "");

        // Browser browser = playwright.chromium().launch(...);
        code = code.replaceAll(
                "(?s)Browser\\s+\\w+\\s*=\\s*[^;]*\\.launch\\s*\\(.*?\\)\\s*;",
                "");

        // BrowserContext context = browser.newContext(...);
        code = code.replaceAll(
                "(?s)BrowserContext\\s+\\w+\\s*=\\s*[^;]+;",
                "");

        // Page page = context.newPage() / browser.newPage();
        code = code.replaceAll(
                "(?s)Page\\s+(\\w+)\\s*=\\s*\\w+\\.newPage\\s*\\(\\s*\\)\\s*;",
                "Page $1 = com.studio.runtime.StudioRuntime.page();");

        // Drop close calls — Studio owns the browser/page lifecycle
        code = code.replaceAll(
                "(?m)^\\s*(?:\\w+\\.)?(?:browser|playwright|context|page)\\.close\\(\\)\\s*;\\s*$",
                "// close handled by StudioRuntime");

        // Self-Healing: wrap page.locator("...") with StudioRuntime.healLocator("...")
        code = code.replaceAll(
                "(?m)(\\b(?:page|p)\\b)\\.locator\\s*\\(\\s*(\"(?:\\\\\"|[^\"])*\")\\s*\\)",
                "com.studio.runtime.StudioRuntime.healLocator($2)");

        return wrapMainBodyWithScreenshot(code, screenshotPath);
    }

    /** Backward-compatible overload (class name left as in source). */
    public static String transform(String originalCode, boolean sharedContext, String screenshotPath) {
        return transform(originalCode, sharedContext, screenshotPath, null);
    }

    static String sanitizeClassName(String name) {
        if (name == null || name.isBlank()) {
            return "HarnessRun";
        }
        String safe = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.isEmpty()) {
            return "HarnessRun";
        }
        if (!Character.isJavaIdentifierStart(safe.charAt(0))) {
            safe = "C_" + safe;
        }
        return safe;
    }

    private static String wrapMainBodyWithScreenshot(String code, String screenshotPath) {
        int mainStart = code.indexOf("public static void main");
        if (mainStart < 0) {
            return code;
        }
        int bodyStart = code.indexOf('{', mainStart);
        if (bodyStart < 0) {
            return code;
        }

        int depth = 1;
        int pos = bodyStart + 1;
        while (pos < code.length() && depth > 0) {
            char ch = code.charAt(pos);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
            pos++;
        }
        int closingBrace = pos - 1;
        if (closingBrace <= bodyStart) {
            return code;
        }

        String header = code.substring(0, bodyStart + 1);
        String innerBody = code.substring(bodyStart + 1, closingBrace).trim();
        // Drop a single outermost { } pair left from try-with-resources stripping
        if (innerBody.startsWith("{") && innerBody.endsWith("}")) {
            innerBody = innerBody.substring(1, innerBody.length() - 1).trim();
        }
        String footer = code.substring(closingBrace);

        String ss = escapeForJava(screenshotPath);
        return header
                + "\n        try {\n"
                + indentBody(innerBody)
                + "\n        } finally {\n"
                + "            try { com.studio.runtime.StudioRuntime.page().screenshot("
                + "new com.microsoft.playwright.Page.ScreenshotOptions().setPath("
                + "java.nio.file.Paths.get(\"" + ss + ".png\"))); } catch (Exception __ss) {}\n"
                + "        }\n"
                + footer;
    }

    private static String indentBody(String body) {
        if (body.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            sb.append("            ").append(line.stripLeading()).append('\n');
        }
        // remove trailing newline duplication handled by caller
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String escapeForJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
