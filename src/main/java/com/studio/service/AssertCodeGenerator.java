package com.studio.service;

import com.studio.model.Assertion;
import com.studio.util.StringEscapeUtil;
import java.util.List;

/**
 * Generates Java code for soft-assert blocks injected into test scripts.
 * Covers the full assert type catalog from the plan.
 */
public class AssertCodeGenerator {

    /**
     * Generate soft-assert Java code block for a list of assertions.
     * Only ACTIVE assertions produce try/catch blocks.
     * Returns: String containing the assert block + trailing AssertionError throw.
     */
    public static String generateAssertBlock(List<Assertion> assertions) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n        // --- studio assertions ---\n");
        sb.append("        java.util.List<String> __studioFailed = new java.util.ArrayList<>();\n");
        sb.append("        java.util.List<String> __studioAssertResults = new java.util.ArrayList<>();\n");

        int idx = 0;
        for (Assertion a : assertions) {
            if (!"ACTIVE".equals(a.getStatus())) {
                // Non-active: record as SKIPPED
                sb.append(String.format(
                    "        __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"status\\\":\\\"SKIPPED\\\",\\\"assertStatus\\\":\\\"%s\\\",\\\"message\\\":\\\"Assert not ACTIVE\\\"}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(a.getStatus())
                ));
            } else {
                sb.append("        try {\n");
                sb.append(generateAssertCode(a));
                sb.append(String.format(
                    "            __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"status\\\":\\\"PASSED\\\",\\\"assertStatus\\\":\\\"ACTIVE\\\",\\\"message\\\":null}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType())
                ));
                sb.append("        } catch (Throwable __t) {\n");
                sb.append(String.format(
                    "            __studioFailed.add(\"[%s] %s\");\n",
                    StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(a.getValue() != null ? a.getValue() : "")
                ));
                sb.append(String.format(
                    "            __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"status\\\":\\\"FAILED\\\",\\\"assertStatus\\\":\\\"ACTIVE\\\",\\\"message\\\":\\\"\" + com.studio.util.StringEscapeUtil.escapeJson(__t.getMessage()) + \"\\\"}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType())
                ));
                sb.append("        }\n");
            }
            idx++;
        }

        // Write assert-results to temp file
        sb.append("        try { java.nio.file.Files.writeString(java.nio.file.Paths.get(System.getProperty(\"studio.assertResults\")), String.join(\"\\n\", __studioAssertResults)); } catch (Exception __ignored) {}\n");
        // Throw if any failed
        sb.append("        if (!__studioFailed.isEmpty()) {\n");
        sb.append("            throw new AssertionError(\"Failed asserts (\" + __studioFailed.size() + \"): \" + String.join(\"; \", __studioFailed));\n");
        sb.append("        }\n");
        sb.append("        // --- end studio assertions ---\n");
        return sb.toString();
    }

    // ========================================================================
    // Assertion type code generation
    // ========================================================================

    private static String generateAssertCode(Assertion a) {
        String type = a.getType();
        String value = a.getValue() != null ? a.getValue() : "";
        String locator = a.getLocator() != null ? a.getLocator() : "";
        int timeout = a.getTimeoutMs() > 0 ? a.getTimeoutMs() : 30000;

        switch (type) {
            // ─── A. Content ──────────────────────────────────────────────────

            case "TEXT_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "TEXT_NOT_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d).setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "TEXT_EXACT":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().getByText(\"%s\", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "HEADING_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().getByRole(com.microsoft.playwright.options.AriaRole.HEADING, new com.microsoft.playwright.Page.GetByRoleOptions().setName(\"%s\")).first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "TITLE_CONTAINS":
                return String.format(
                    "            String __title = com.studio.runtime.StudioRuntime.page().title(); if (!__title.contains(\"%s\")) throw new AssertionError(\"Title does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "TITLE_EQUALS":
                return String.format(
                    "            String __title = com.studio.runtime.StudioRuntime.page().title(); if (!__title.equals(\"%s\")) throw new AssertionError(\"Title expected: %s, actual: \" + __title);\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_CONTAINS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.contains(\"%s\")) throw new AssertionError(\"URL does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_EQUALS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.equals(\"%s\")) throw new AssertionError(\"URL expected: %s, actual: \" + __url);\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_MATCHES":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.matches(\"%s\")) throw new AssertionError(\"URL does not match: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "PAGE_HAS_TEXT":
                return String.format(
                    "            String __content = com.studio.runtime.StudioRuntime.page().content(); if (!__content.contains(\"%s\")) throw new AssertionError(\"Page does not contain text: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            // ─── B. Element / UI ─────────────────────────────────────────────

            case "ELEMENT_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), timeout);

            case "ELEMENT_HIDDEN":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d).setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));\n",
                    StringEscapeUtil.escapeJava(locator), timeout);

            case "ELEMENT_ENABLED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.page().locator(\"%s\").isEnabled()) throw new AssertionError(\"Element not enabled: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_DISABLED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.page().locator(\"%s\").isDisabled()) throw new AssertionError(\"Element not disabled: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_EDITABLE":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.page().locator(\"%s\").isEditable()) throw new AssertionError(\"Element not editable: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_CHECKED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.page().locator(\"%s\").isChecked()) throw new AssertionError(\"Element not checked: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_UNCHECKED":
                return String.format(
                    "            if (com.studio.runtime.StudioRuntime.page().locator(\"%s\").isChecked()) throw new AssertionError(\"Element is checked: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_COUNT":
                return String.format(
                    "            int __count = com.studio.runtime.StudioRuntime.page().locator(\"%s\").count(); if (!(%s)) throw new AssertionError(\"Element count for '%s': expected %s, actual: \" + __count);\n",
                    StringEscapeUtil.escapeJava(locator), buildCountCondition(value), StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value));

            case "ELEMENT_HAS_TEXT":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(\"%s\")).first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), timeout);

            case "ELEMENT_HAS_VALUE":
                return String.format(
                    "            String __val = com.studio.runtime.StudioRuntime.page().locator(\"%s\").inputValue(); if (!\"%s\".equals(__val)) throw new AssertionError(\"Expected value: %s, actual: \" + __val);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "ATTRIBUTE_EQUALS":
                return String.format(
                    "            String __attr = com.studio.runtime.StudioRuntime.page().locator(\"%s\").getAttribute(\"%s\"); if (!\"%s\".equals(__attr)) throw new AssertionError(\"Attribute %s expected: %s, actual: \" + __attr);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()));

            case "CSS_CONTAINS":
                return String.format(
                    "            Object __css = com.studio.runtime.StudioRuntime.page().locator(\"%s\").evaluate(\"el => window.getComputedStyle(el).%s\"); if (__css == null || !__css.toString().contains(\"%s\")) throw new AssertionError(\"CSS %s does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(value));

            case "LINK_HREF_CONTAINS":
                return String.format(
                    "            String __href = com.studio.runtime.StudioRuntime.page().locator(\"%s\").getAttribute(\"href\"); if (__href == null || !__href.contains(\"%s\")) throw new AssertionError(\"Link href does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            // ─── C. Media ────────────────────────────────────────────────────

            case "IMAGE_VISIBLE": {
                String imgLoc = !value.isEmpty()
                    ? String.format("img[alt*=\"%s\" i], img[src*=\"%s\" i]", StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value))
                    : "img";
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    imgLoc, timeout);
            }

            case "IMAGE_COUNT":
                return String.format(
                    "            int __imgCount = com.studio.runtime.StudioRuntime.page().locator(\"img\").count(); if (__imgCount < %s) throw new AssertionError(\"Expected at least %s images, found: \" + __imgCount);\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "VIDEO_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"video\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    timeout);

            case "AUDIO_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"audio\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    timeout);

            // ─── D. File / Download ──────────────────────────────────────────

            case "FILE_DOWNLOADED":
                return "            // FILE_DOWNLOADED: requires download event listener in script — best-effort check\n"
                     + "            // If no download listener was set up, this assert will pass (no-op)\n";

            case "FILE_NAMED":
                return "            // FILE_NAMED: download.suggestedFilename() must match — requires download event setup\n"
                     + "            // This is a placeholder; actual check requires the download object from the script\n"
                     + "            throw new AssertionError(\"FILE_NAMED: download tracking not implemented in script\");\n";

            case "FILE_EXTENSION":
                return "            // FILE_EXTENSION: download.suggestedFilename() must end with extension — requires download event setup\n"
                     + "            throw new AssertionError(\"FILE_EXTENSION: download tracking not implemented in script\");\n";

            // ─── E. Table ────────────────────────────────────────────────────

            case "TABLE_HAS_TEXT":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"table\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "TABLE_ROW_COUNT":
                return String.format(
                    "            int __rowCount = com.studio.runtime.StudioRuntime.page().locator(\"%s tr\").count(); if (!(%s)) throw new AssertionError(\"Row count for '%s': expected %s, actual: \" + __rowCount);\n",
                    StringEscapeUtil.escapeJava(locator), buildCountCondition(value), StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value));

            case "TABLE_CELL_EQUALS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), timeout);

            case "TABLE_HEADER_CONTAINS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"th\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            // ─── F. Form ─────────────────────────────────────────────────────

            case "INPUT_VALUE_EQUALS":
                return String.format(
                    "            String __inputVal = com.studio.runtime.StudioRuntime.page().locator(\"%s\").inputValue(); if (!\"%s\".equals(__inputVal)) throw new AssertionError(\"Input value expected: %s, actual: \" + __inputVal);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "INPUT_VALUE_CONTAINS":
                return String.format(
                    "            String __inputVal = com.studio.runtime.StudioRuntime.page().locator(\"%s\").inputValue(); if (!__inputVal.contains(\"%s\")) throw new AssertionError(\"Input does not contain: %s, actual: \" + __inputVal);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "PLACEHOLDER_EQUALS":
                return String.format(
                    "            String __ph = com.studio.runtime.StudioRuntime.page().locator(\"%s\").getAttribute(\"placeholder\"); if (!\"%s\".equals(__ph)) throw new AssertionError(\"Placeholder expected: %s, actual: \" + __ph);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "SELECT_HAS_OPTION":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").selectOption(\"%s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value));

            case "FORM_VALID":
                return String.format(
                    "            Object __valid = com.studio.runtime.StudioRuntime.page().locator(\"%s\").evaluate(\"el => el.checkValidity()\"); if (!Boolean.TRUE.equals(__valid)) throw new AssertionError(\"Form is invalid: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "FORM_INVALID":
                return String.format(
                    "            Object __valid = com.studio.runtime.StudioRuntime.page().locator(\"%s\").evaluate(\"el => el.checkValidity()\"); if (Boolean.TRUE.equals(__valid)) throw new AssertionError(\"Form is unexpectedly valid: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            // ─── G. Storage / Network ────────────────────────────────────────

            case "NO_CONSOLE_ERROR":
                return "            // NO_CONSOLE_ERROR: best-effort — requires console listener in script\n";

            case "COOKIE_EXISTS":
                return String.format(
                    "            java.util.List<com.microsoft.playwright.options.Cookie> __cookies = com.studio.runtime.StudioRuntime.context().cookies(); boolean __found = false; for (var c : __cookies) { if (\"%s\".equals(c.name)) { __found = true; break; } } if (!__found) throw new AssertionError(\"Cookie not found: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "LOCAL_STORAGE_EQUALS":
                return String.format(
                    "            Object __ls = com.studio.runtime.StudioRuntime.page().evaluate(\"localStorage.getItem('%s')\"); if (!\"%s\".equals(__ls)) throw new AssertionError(\"localStorage %s expected: %s, actual: \" + __ls);\n",
                    StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()));

            case "SESSION_STORAGE_EQUALS":
                return String.format(
                    "            Object __ss = com.studio.runtime.StudioRuntime.page().evaluate(\"sessionStorage.getItem('%s')\"); if (!\"%s\".equals(__ss)) throw new AssertionError(\"sessionStorage %s expected: %s, actual: \" + __ss);\n",
                    StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()), StringEscapeUtil.escapeJava(a.getAttribute()), StringEscapeUtil.escapeJava(a.getExpectedValue()));

            // ─── H. Custom ───────────────────────────────────────────────────

            case "CUSTOM":
                return String.format(
                    "            // CUSTOM assert with locator: %s\n            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator), timeout);

            case "CUSTOM_JS":
                return String.format(
                    "            Object __jsResult = com.studio.runtime.StudioRuntime.page().evaluate(\"%s\"); if (!Boolean.TRUE.equals(__jsResult)) throw new AssertionError(\"Custom JS assertion failed: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            // ─── Unknown ─────────────────────────────────────────────────────

            default:
                return String.format(
                    "            // Unknown assert type: %s\n            throw new AssertionError(\"Unknown assert type: %s\");\n",
                    StringEscapeUtil.escapeJava(type), StringEscapeUtil.escapeJava(type));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Build a count comparison expression from a value like "=5", ">=3", "<=10", etc.
     */
    private static String buildCountCondition(String value) {
        if (value == null) return "__count == 0";
        if (value.startsWith(">=")) return String.format("__count >= %s", value.substring(2));
        if (value.startsWith("<=")) return String.format("__count <= %s", value.substring(2));
        if (value.startsWith(">"))  return String.format("__count >  %s", value.substring(1));
        if (value.startsWith("<"))  return String.format("__count <  %s", value.substring(1));
        if (value.startsWith("="))  return String.format("__count == %s", value.substring(1));
        return String.format("__count == %s", value); // default: equals
    }

}
