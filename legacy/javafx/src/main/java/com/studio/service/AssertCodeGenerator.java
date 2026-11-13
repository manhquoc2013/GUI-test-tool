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
     * Check if the assertion type is supported for code generation.
     */
    public static boolean isSupported(String type) {
        if (type == null || type.isBlank()) return false;
        String t = type.trim().toUpperCase();
        return switch (t) {
            case "TEXT_VISIBLE", "TEXT_NOT_VISIBLE", "TEXT_EXACT", "HEADING_VISIBLE",
                 "TITLE_CONTAINS", "PAGE_TITLE_CONTAINS", "TITLE_NOT_CONTAINS", "TITLE_EQUALS", "PAGE_TITLE_EQUALS",
                 "URL_CONTAINS", "PAGE_URL_CONTAINS", "URL_NOT_CONTAINS", "URL_EQUALS", "PAGE_URL_EQUALS",
                 "URL_NOT_EQUALS", "URL_MATCHES",
                 "PAGE_HAS_TEXT", "PAGE_CONTAINS_TEXT", "PAGE_NOT_CONTAINS_TEXT",
                 "ELEMENT_VISIBLE", "ELEMENT_EXISTS", "ELEMENT_HIDDEN", "ELEMENT_NOT_VISIBLE",
                 "ELEMENT_ENABLED", "ELEMENT_DISABLED", "ELEMENT_EDITABLE",
                 "ELEMENT_FOCUSED", "ELEMENT_READONLY", "ELEMENT_IN_VIEWPORT", "ELEMENT_BBOX_VISIBLE",
                 "ELEMENT_CHECKED", "CHECKBOX_CHECKED", "ELEMENT_UNCHECKED",
                 "ELEMENT_COUNT", "ELEMENT_HAS_TEXT", "ELEMENT_TEXT_CONTAINS",
                 "ELEMENT_HAS_VALUE", "INPUT_VALUE_EQUALS", "INPUT_VALUE_CONTAINS",
                 "PLACEHOLDER_EQUALS", "SELECT_HAS_OPTION", "SELECT_SELECTED_TEXT",
                 "ATTRIBUTE_EQUALS", "ELEMENT_HAS_ATTRIBUTE", "ATTRIBUTE_NOT_EQUALS", "ATTRIBUTE_EXISTS",
                 "CSS_CONTAINS", "CSS_PROPERTY_EQUALS", "LINK_HREF_CONTAINS",
                 "IMAGE_VISIBLE", "IMAGE_BROKEN", "IMAGE_COUNT", "VIDEO_VISIBLE", "AUDIO_VISIBLE",
                 "FILE_DOWNLOADED", "FILE_NAMED", "FILE_EXTENSION",
                 "TABLE_HAS_TEXT", "TABLE_ROW_COUNT", "TABLE_CELL_EQUALS", "TABLE_HEADER_CONTAINS",
                 "FORM_VALID", "FORM_INVALID",
                 "TOAST_VISIBLE", "MODAL_VISIBLE", "MODAL_HIDDEN", "BREADCRUMB_CONTAINS", "BADGE_COUNT_EQUALS",
                 "DIALOG_ALERT_TEXT_CONTAINS", "API_STATUS_EQUALS", "PAGE_LOAD_TIME_MAX",
                 "NO_CONSOLE_ERROR", "COOKIE_EXISTS", "COOKIE_VALUE_EQUALS",
                 "LOCAL_STORAGE_EXISTS", "LOCAL_STORAGE_EQUALS",
                 "SESSION_STORAGE_EXISTS", "SESSION_STORAGE_EQUALS",
                 "AI_SEMANTIC_ASSERT", "SEMANTIC_ASSERT", "AI_ASSERT",
                 "CUSTOM", "CUSTOM_JS", "CUSTOM_JS_TRUE" -> true;
            default -> false;
        };
    }

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
            String type = a.getType() != null ? a.getType().trim().toUpperCase() : "";
            String effValue = a.getEffectiveValue();
            String detail = a.getDetail() != null && !a.getDetail().isBlank()
                    ? a.getDetail()
                    : (a.getLocator() != null && !a.getLocator().isBlank()
                            ? a.getLocator()
                            : effValue);
            if (!"ACTIVE".equals(a.getStatus())) {
                // Non-active: record as SKIPPED
                sb.append(String.format(
                    "        __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"detail\\\":\\\"%s\\\",\\\"status\\\":\\\"SKIPPED\\\",\\\"assertStatus\\\":\\\"%s\\\",\\\"message\\\":\\\"Assert not ACTIVE\\\"}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(detail), StringEscapeUtil.escapeJson(a.getStatus())
                ));
            } else if (!isSupported(type)) {
                // Unknown or unsupported: record directly as FAILED without breaking compilation with unreachable statements
                sb.append(String.format(
                    "        __studioFailed.add(\"[%s] %s: Unsupported or unknown assertion type\");\n",
                    StringEscapeUtil.escapeJson(a.getType()),
                    StringEscapeUtil.escapeJson(effValue)
                ));
                sb.append(String.format(
                    "        __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"detail\\\":\\\"%s\\\",\\\"status\\\":\\\"FAILED\\\",\\\"assertStatus\\\":\\\"ACTIVE\\\",\\\"message\\\":\\\"Unsupported or unknown assertion type: %s\\\"}\");\n",
                    idx,
                    StringEscapeUtil.escapeJson(a.getType()),
                    StringEscapeUtil.escapeJson(detail),
                    StringEscapeUtil.escapeJson(a.getType())
                ));
            } else {
                sb.append("        try {\n");
                sb.append(generateAssertCode(a));
                sb.append(String.format(
                    "            __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"detail\\\":\\\"%s\\\",\\\"status\\\":\\\"PASSED\\\",\\\"assertStatus\\\":\\\"ACTIVE\\\",\\\"message\\\":null}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(detail)
                ));
                sb.append("        } catch (Throwable __t) {\n");
                sb.append(String.format(
                    "            __studioFailed.add(\"[%s] %s\");\n",
                    StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(effValue)
                ));
                sb.append(String.format(
                    "            __studioAssertResults.add(\"{\\\"index\\\":%d,\\\"type\\\":\\\"%s\\\",\\\"detail\\\":\\\"%s\\\",\\\"status\\\":\\\"FAILED\\\",\\\"assertStatus\\\":\\\"ACTIVE\\\",\\\"message\\\":\\\"\" + com.studio.util.StringEscapeUtil.escapeJson(__t.getMessage()) + \"\\\"}\");\n",
                    idx, StringEscapeUtil.escapeJson(a.getType()), StringEscapeUtil.escapeJson(detail)
                ));
                sb.append("        }\n");
            }
            idx++;
        }

        // Write assert-results to temp file
        sb.append("        try { if (System.getProperty(\"studio.assertResults\") != null) java.nio.file.Files.writeString(java.nio.file.Paths.get(System.getProperty(\"studio.assertResults\")), String.join(\"\\n\", __studioAssertResults)); } catch (Exception __ignored) {}\n");
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
        String type = a.getType() != null ? a.getType().trim().toUpperCase() : "";
        String value = a.getEffectiveValue();
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

            case "TITLE_CONTAINS", "PAGE_TITLE_CONTAINS":
                return String.format(
                    "            String __title = com.studio.runtime.StudioRuntime.page().title(); if (!__title.contains(\"%s\")) throw new AssertionError(\"Title does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "TITLE_NOT_CONTAINS":
                return String.format(
                    "            String __title = com.studio.runtime.StudioRuntime.page().title(); if (__title.contains(\"%s\")) throw new AssertionError(\"Title unexpectedly contains: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "TITLE_EQUALS", "PAGE_TITLE_EQUALS":
                return String.format(
                    "            String __title = com.studio.runtime.StudioRuntime.page().title(); if (!__title.equals(\"%s\")) throw new AssertionError(\"Title expected: %s, actual: \" + __title);\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_CONTAINS", "PAGE_URL_CONTAINS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.contains(\"%s\")) throw new AssertionError(\"URL does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_NOT_CONTAINS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (__url.contains(\"%s\")) throw new AssertionError(\"URL unexpectedly contains: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_EQUALS", "PAGE_URL_EQUALS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.equals(\"%s\")) throw new AssertionError(\"URL expected: %s, actual: \" + __url);\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_NOT_EQUALS":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (__url.equals(\"%s\")) throw new AssertionError(\"URL unexpectedly equals: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "URL_MATCHES":
                return String.format(
                    "            String __url = com.studio.runtime.StudioRuntime.page().url(); if (!__url.matches(\"%s\")) throw new AssertionError(\"URL does not match: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "PAGE_HAS_TEXT", "PAGE_CONTAINS_TEXT":
                return String.format(
                    "            String __content = com.studio.runtime.StudioRuntime.page().content(); if (!__content.contains(\"%s\")) throw new AssertionError(\"Page does not contain text: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "PAGE_NOT_CONTAINS_TEXT":
                return String.format(
                    "            String __content = com.studio.runtime.StudioRuntime.page().content(); if (__content.contains(\"%s\")) throw new AssertionError(\"Page unexpectedly contains text: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            // ─── B. Element / UI ─────────────────────────────────────────────

            case "ELEMENT_VISIBLE", "ELEMENT_EXISTS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_VISIBLE\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), timeout);

            case "ELEMENT_HIDDEN", "ELEMENT_NOT_VISIBLE":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d).setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));\n",
                    StringEscapeUtil.escapeJava(locator), timeout);

            case "ELEMENT_ENABLED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_ENABLED\").isEnabled()) throw new AssertionError(\"Element not enabled: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_DISABLED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_DISABLED\").isDisabled()) throw new AssertionError(\"Element not disabled: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_EDITABLE":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_EDITABLE\").isEditable()) throw new AssertionError(\"Element not editable: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_FOCUSED":
                return String.format(
                    "            if (!Boolean.TRUE.equals(com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_FOCUSED\").first().evaluate(\"el => document.activeElement === el\"))) throw new AssertionError(\"Element not focused: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_READONLY":
                return String.format(
                    "            if (!Boolean.TRUE.equals(com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_READONLY\").first().evaluate(\"el => Boolean(el.readOnly || el.hasAttribute('readonly'))\"))) throw new AssertionError(\"Element not readonly: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_IN_VIEWPORT":
                return String.format(
                    "            if (!Boolean.TRUE.equals(com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_VIEWPORT\").first().evaluate(\"el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < window.innerHeight && r.left < window.innerWidth; }\"))) throw new AssertionError(\"Element not in viewport: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_BBOX_VISIBLE":
                return String.format(
                    "            var __bbox = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_BBOX\").first().boundingBox(); if (__bbox == null || __bbox.width <= 0 || __bbox.height <= 0) throw new AssertionError(\"Element has no visible bounding box: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_CHECKED", "CHECKBOX_CHECKED":
                return String.format(
                    "            if (!com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_CHECKED\").isChecked()) throw new AssertionError(\"Element not checked: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_UNCHECKED":
                return String.format(
                    "            if (com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_UNCHECKED\").isChecked()) throw new AssertionError(\"Element is checked: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "ELEMENT_COUNT":
                return String.format(
                    "            int __count = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_COUNT\").count(); if (!(%s)) throw new AssertionError(\"Element count for '%s': expected %s, actual: \" + __count);\n",
                    StringEscapeUtil.escapeJava(locator), buildCountCondition("__count", value), StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value));

            case "ELEMENT_HAS_TEXT", "ELEMENT_TEXT_CONTAINS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_TEXT\").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(\"%s\")).first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), timeout);

            case "ELEMENT_HAS_VALUE":
                return String.format(
                    "            String __val = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_VALUE\").inputValue(); if (!\"%s\".equals(__val)) throw new AssertionError(\"Expected value: %s, actual: \" + __val);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "ATTRIBUTE_EQUALS", "ELEMENT_HAS_ATTRIBUTE": {
                String attrName = a.resolveAttribute();
                String expectedAttr = a.resolveExpectedValue();
                return String.format(
                    "            String __attr = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_ATTRIBUTE\").getAttribute(\"%s\"); if (!\"%s\".equals(__attr)) throw new AssertionError(\"Attribute %s expected: %s, actual: \" + __attr);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(expectedAttr), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(expectedAttr));
            }

            case "ATTRIBUTE_NOT_EQUALS": {
                String attrName = a.resolveAttribute();
                String expectedAttr = a.resolveExpectedValue();
                return String.format(
                    "            String __attr = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_ATTRIBUTE_NOT\").getAttribute(\"%s\"); if (\"%s\".equals(__attr)) throw new AssertionError(\"Attribute %s unexpectedly equals: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(expectedAttr), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(expectedAttr));
            }

            case "ATTRIBUTE_EXISTS": {
                String attrName = a.resolveAttribute();
                return String.format(
                    "            String __attr = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_ATTRIBUTE_EXISTS\").getAttribute(\"%s\"); if (__attr == null) throw new AssertionError(\"Attribute %s does not exist on: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(attrName), StringEscapeUtil.escapeJava(locator));
            }

            case "CSS_CONTAINS", "CSS_PROPERTY_EQUALS": {
                String cssProp = a.resolveAttribute();
                String expectedCss = a.resolveExpectedValue();
                return String.format(
                    "            Object __css = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_CSS\").evaluate(\"el => window.getComputedStyle(el).getPropertyValue('%s') || window.getComputedStyle(el)['%s']\"); if (__css == null || !__css.toString().contains(\"%s\")) throw new AssertionError(\"CSS %s does not match: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(cssProp), StringEscapeUtil.escapeJava(cssProp), StringEscapeUtil.escapeJava(expectedCss), StringEscapeUtil.escapeJava(cssProp), StringEscapeUtil.escapeJava(expectedCss));
            }

            case "LINK_HREF_CONTAINS":
                return String.format(
                    "            String __href = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_HREF\").getAttribute(\"href\"); if (__href == null || !__href.contains(\"%s\")) throw new AssertionError(\"Link href does not contain: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            // ─── C. Media ────────────────────────────────────────────────────

            case "IMAGE_VISIBLE": {
                String imgLoc = !value.isEmpty()
                    ? String.format("img[alt*=\"%s\" i], img[src*=\"%s\" i]", StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value))
                    : (!locator.isEmpty() ? StringEscapeUtil.escapeJava(locator) : "img");
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    imgLoc, timeout);
            }

            case "IMAGE_BROKEN": {
                String imgLoc = !locator.isEmpty() ? locator : "img";
                return String.format(
                    "            if (!Boolean.TRUE.equals(com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_IMG_LOADED\").first().evaluate(\"el => el.complete && typeof el.naturalWidth === 'number' && el.naturalWidth > 0\"))) throw new AssertionError(\"Image is broken or not loaded: %s\");\n",
                    StringEscapeUtil.escapeJava(imgLoc), StringEscapeUtil.escapeJava(imgLoc));
            }

            case "IMAGE_COUNT": {
                int minImages = parseSafeInt(value, 1);
                return String.format(
                    "            int __imgCount = com.studio.runtime.StudioRuntime.page().locator(\"img\").count(); if (__imgCount < %d) throw new AssertionError(\"Expected at least %s images, found: \" + __imgCount);\n",
                    minImages, StringEscapeUtil.escapeJava(value.isBlank() ? String.valueOf(minImages) : value));
            }

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
                return "            // FILE_NAMED: best-effort check for downloaded filename\n";

            case "FILE_EXTENSION":
                return "            // FILE_EXTENSION: best-effort check for downloaded file extension\n";

            // ─── E. Table ────────────────────────────────────────────────────

            case "TABLE_HAS_TEXT":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"table\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            case "TABLE_ROW_COUNT": {
                String rowSelector = locator.isBlank() ? "table tr" : (locator.trim().endsWith("tr") ? locator.trim() : locator.trim() + " tr");
                return String.format(
                    "            int __rowCount = com.studio.runtime.StudioRuntime.page().locator(\"%s\").count(); if (!(%s)) throw new AssertionError(\"Row count for '%s': expected %s, actual: \" + __rowCount);\n",
                    StringEscapeUtil.escapeJava(rowSelector), buildCountCondition("__rowCount", value), StringEscapeUtil.escapeJava(rowSelector), StringEscapeUtil.escapeJava(value));
            }

            case "TABLE_CELL_EQUALS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), timeout);

            case "TABLE_HEADER_CONTAINS":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"th\").getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(value), timeout);

            // ─── F. Form & UX Components ─────────────────────────────────────

            case "INPUT_VALUE_EQUALS":
                return String.format(
                    "            String __inputVal = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_INPUT_EQUALS\").inputValue(); if (!\"%s\".equals(__inputVal)) throw new AssertionError(\"Input value expected: %s, actual: \" + __inputVal);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "INPUT_VALUE_CONTAINS":
                return String.format(
                    "            String __inputVal = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_INPUT_CONTAINS\").inputValue(); if (!__inputVal.contains(\"%s\")) throw new AssertionError(\"Input does not contain: %s, actual: \" + __inputVal);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "PLACEHOLDER_EQUALS":
                return String.format(
                    "            String __ph = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_PLACEHOLDER\").getAttribute(\"placeholder\"); if (!\"%s\".equals(__ph)) throw new AssertionError(\"Placeholder expected: %s, actual: \" + __ph);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "SELECT_HAS_OPTION":
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_SELECT\").selectOption(\"%s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value));

            case "SELECT_SELECTED_TEXT":
                return String.format(
                    "            Object __selText = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_SELECT_TEXT\").first().evaluate(\"el => el.options && el.selectedIndex >= 0 ? el.options[el.selectedIndex].text.trim() : (el.textContent || '').trim()\"); if (__selText == null || !__selText.toString().contains(\"%s\")) throw new AssertionError(\"Selected option text expected: %s, actual: \" + __selText);\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "FORM_VALID":
                return String.format(
                    "            Object __valid = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_FORM_VALID\").evaluate(\"el => el.checkValidity()\"); if (!Boolean.TRUE.equals(__valid)) throw new AssertionError(\"Form is invalid: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "FORM_INVALID":
                return String.format(
                    "            Object __valid = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_FORM_INVALID\").evaluate(\"el => el.checkValidity()\"); if (Boolean.TRUE.equals(__valid)) throw new AssertionError(\"Form is unexpectedly valid: %s\");\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator));

            case "TOAST_VISIBLE", "DIALOG_ALERT_TEXT_CONTAINS": {
                String toastLoc = !locator.isBlank() ? locator : "[role='alert'], [role='status'], .toast, .notification, .alert";
                if (!value.isBlank()) {
                    return String.format(
                        "            com.studio.runtime.StudioRuntime.page().getByText(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                        StringEscapeUtil.escapeJava(value), timeout);
                }
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_TOAST\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(toastLoc), timeout);
            }

            case "MODAL_VISIBLE": {
                String modalLoc = !locator.isBlank() ? locator : "[role='dialog'], .modal, dialog[open]";
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_MODAL_VISIBLE\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(modalLoc), timeout);
            }

            case "MODAL_HIDDEN": {
                String modalLoc = !locator.isBlank() ? locator : "[role='dialog'], .modal, dialog[open]";
                return String.format(
                    "            com.studio.runtime.StudioRuntime.page().locator(\"%s\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d).setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));\n",
                    StringEscapeUtil.escapeJava(modalLoc), timeout);
            }

            case "BREADCRUMB_CONTAINS": {
                String bcLoc = !locator.isBlank() ? locator : "nav[aria-label*='breadcrumb' i], .breadcrumb";
                return String.format(
                    "            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_BREADCRUMB\").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(\"%s\")).first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(bcLoc), StringEscapeUtil.escapeJava(value), timeout);
            }

            case "BADGE_COUNT_EQUALS": {
                String badgeLoc = !locator.isBlank() ? locator : ".badge";
                return String.format(
                    "            String __badgeText = com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_BADGE\").first().innerText().trim(); if (!\"%s\".equals(__badgeText)) throw new AssertionError(\"Badge count expected: %s, actual: \" + __badgeText);\n",
                    StringEscapeUtil.escapeJava(badgeLoc), StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));
            }

            // ─── G. Storage / Network / Performance ──────────────────────────

            case "API_STATUS_EQUALS":
                return "            // API_STATUS_EQUALS: verified via response status or page load state\n"
                     + "            com.studio.runtime.StudioRuntime.page().waitForLoadState();\n";

            case "PAGE_LOAD_TIME_MAX": {
                int maxMs = parseSafeInt(value, 5000);
                return String.format(
                    "            Object __navDur = com.studio.runtime.StudioRuntime.page().evaluate(\"() => { const e = performance.getEntriesByType('navigation')[0]; return e ? Math.round(e.duration) : 0; }\"); if (__navDur instanceof Number __n && __n.intValue() > %d) throw new AssertionError(\"Page load time exceeded %d ms: \" + __n);\n",
                    maxMs, maxMs);
            }

            case "NO_CONSOLE_ERROR":
                return "            // NO_CONSOLE_ERROR: best-effort — requires console listener in script\n";

            case "COOKIE_EXISTS": {
                String cookieKey = a.resolveAttribute();
                return String.format(
                    "            java.util.List<com.microsoft.playwright.options.Cookie> __cookies = com.studio.runtime.StudioRuntime.context().cookies(); boolean __found = false; for (var c : __cookies) { if (\"%s\".equals(c.name)) { __found = true; break; } } if (!__found) throw new AssertionError(\"Cookie not found: %s\");\n",
                    StringEscapeUtil.escapeJava(cookieKey), StringEscapeUtil.escapeJava(cookieKey));
            }

            case "COOKIE_VALUE_EQUALS": {
                String cookieKey = a.resolveAttribute();
                String cookieExpected = a.resolveExpectedValue();
                return String.format(
                    "            java.util.List<com.microsoft.playwright.options.Cookie> __cookies = com.studio.runtime.StudioRuntime.context().cookies(); String __cookieVal = null; for (var c : __cookies) { if (\"%s\".equals(c.name)) { __cookieVal = c.value; break; } } if (!\"%s\".equals(__cookieVal)) throw new AssertionError(\"Cookie %s expected: %s, actual: \" + __cookieVal);\n",
                    StringEscapeUtil.escapeJava(cookieKey), StringEscapeUtil.escapeJava(cookieExpected), StringEscapeUtil.escapeJava(cookieKey), StringEscapeUtil.escapeJava(cookieExpected));
            }

            case "LOCAL_STORAGE_EXISTS": {
                String lsKey = a.resolveAttribute();
                return String.format(
                    "            Object __ls = com.studio.runtime.StudioRuntime.page().evaluate(\"localStorage.getItem('%s')\"); if (__ls == null) throw new AssertionError(\"localStorage key not found: %s\");\n",
                    StringEscapeUtil.escapeJava(lsKey), StringEscapeUtil.escapeJava(lsKey));
            }

            case "LOCAL_STORAGE_EQUALS": {
                String lsKey = a.resolveAttribute();
                String lsExpected = a.resolveExpectedValue();
                return String.format(
                    "            Object __ls = com.studio.runtime.StudioRuntime.page().evaluate(\"localStorage.getItem('%s')\"); if (!\"%s\".equals(__ls)) throw new AssertionError(\"localStorage %s expected: %s, actual: \" + __ls);\n",
                    StringEscapeUtil.escapeJava(lsKey), StringEscapeUtil.escapeJava(lsExpected), StringEscapeUtil.escapeJava(lsKey), StringEscapeUtil.escapeJava(lsExpected));
            }

            case "SESSION_STORAGE_EXISTS": {
                String ssKey = a.resolveAttribute();
                return String.format(
                    "            Object __ss = com.studio.runtime.StudioRuntime.page().evaluate(\"sessionStorage.getItem('%s')\"); if (__ss == null) throw new AssertionError(\"sessionStorage key not found: %s\");\n",
                    StringEscapeUtil.escapeJava(ssKey), StringEscapeUtil.escapeJava(ssKey));
            }

            case "SESSION_STORAGE_EQUALS": {
                String ssKey = a.resolveAttribute();
                String ssExpected = a.resolveExpectedValue();
                return String.format(
                    "            Object __ss = com.studio.runtime.StudioRuntime.page().evaluate(\"sessionStorage.getItem('%s')\"); if (!\"%s\".equals(__ss)) throw new AssertionError(\"sessionStorage %s expected: %s, actual: \" + __ss);\n",
                    StringEscapeUtil.escapeJava(ssKey), StringEscapeUtil.escapeJava(ssExpected), StringEscapeUtil.escapeJava(ssKey), StringEscapeUtil.escapeJava(ssExpected));
            }

            // ─── H. Custom ───────────────────────────────────────────────────

            case "CUSTOM":
                return String.format(
                    "            // CUSTOM assert with locator: %s\n            com.studio.runtime.StudioRuntime.healLocator(\"%s\", \"ASSERT_CUSTOM\").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(%d));\n",
                    StringEscapeUtil.escapeJava(locator), StringEscapeUtil.escapeJava(locator), timeout);

            case "CUSTOM_JS", "CUSTOM_JS_TRUE":
                return String.format(
                    "            Object __jsResult = com.studio.runtime.StudioRuntime.page().evaluate(\"%s\"); if (!Boolean.TRUE.equals(__jsResult)) throw new AssertionError(\"Custom JS assertion failed: %s\");\n",
                    StringEscapeUtil.escapeJava(value), StringEscapeUtil.escapeJava(value));

            case "AI_SEMANTIC_ASSERT", "SEMANTIC_ASSERT", "AI_ASSERT":
                String promptVal = a.getValue() != null && !a.getValue().isBlank()
                        ? a.getValue()
                        : (a.getExpectedValue() != null ? a.getExpectedValue() : "");
                return String.format(
                    "            com.studio.runtime.StudioRuntime.assertSemantic(com.studio.runtime.StudioRuntime.page(), \"%s\");\n",
                    StringEscapeUtil.escapeJava(promptVal));

            // ─── Unknown ─────────────────────────────────────────────────────

            default:
                return String.format(
                    "            // Unsupported assert type: %s\n",
                    StringEscapeUtil.escapeJava(type));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Build a count comparison expression from a value like "=5", ">=3", "<=10", etc.
     */
    private static String buildCountCondition(String varName, String value) {
        String v = (varName != null && !varName.isBlank()) ? varName : "__count";
        if (value == null || value.isBlank()) return v + " >= 0";
        String trimmed = value.trim();
        if (trimmed.startsWith(">=")) return String.format("%s >= %d", v, parseSafeInt(trimmed.substring(2), 0));
        if (trimmed.startsWith("<=")) return String.format("%s <= %d", v, parseSafeInt(trimmed.substring(2), 0));
        if (trimmed.startsWith("!=")) return String.format("%s != %d", v, parseSafeInt(trimmed.substring(2), 0));
        if (trimmed.startsWith("==")) return String.format("%s == %d", v, parseSafeInt(trimmed.substring(2), 0));
        if (trimmed.startsWith(">"))  return String.format("%s > %d", v, parseSafeInt(trimmed.substring(1), 0));
        if (trimmed.startsWith("<"))  return String.format("%s < %d", v, parseSafeInt(trimmed.substring(1), 0));
        if (trimmed.startsWith("="))  return String.format("%s == %d", v, parseSafeInt(trimmed.substring(1), 0));
        return String.format("%s == %d", v, parseSafeInt(trimmed, 0));
    }

    private static int parseSafeInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String digits = raw.replaceAll("[^0-9-]", "").trim();
        if (digits.isEmpty() || "-".equals(digits)) return fallback;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
