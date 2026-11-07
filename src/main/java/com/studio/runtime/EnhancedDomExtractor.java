package com.studio.runtime;

import com.microsoft.playwright.Page;

/**
 * Extracts a compacted, token-optimized interactive DOM tree from a Playwright Page.
 * Traverses Shadow DOM, handles hidden/clipped/invisible elements, removes dynamic IDs,
 * and extracts bounding boxes for VLM reasoning.
 */
public class EnhancedDomExtractor {

    private static final String EXTRACT_SCRIPT = """
        () => {
            const results = [];
            const interactiveTags = new Set(['button', 'a', 'input', 'select', 'textarea']);
            const interactiveRoles = new Set(['button', 'link', 'checkbox', 'radio', 'tab', 'menuitem', 'combobox', 'textbox', 'searchbox', 'switch']);

            function isElementVisible(el, rect, style) {
                if (rect.width <= 0 || rect.height <= 0) return false;
                if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                if (style.pointerEvents === 'none') return false;
                if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) {
                    return false;
                }
                return true;
            }

            function inspectNode(node) {
                if (!node || node.nodeType !== Node.ELEMENT_NODE) return;
                const el = node;
                const tag = el.tagName.toLowerCase();
                const style = window.getComputedStyle(el);
                const rect = el.getBoundingClientRect();
                const role = el.getAttribute('role') || '';

                const isInteractive = interactiveTags.has(tag) || 
                                      interactiveRoles.has(role) || 
                                      el.hasAttribute('onclick') || 
                                      el.hasAttribute('data-testid') || 
                                      el.hasAttribute('data-qa') ||
                                      el.hasAttribute('data-cy') ||
                                      style.cursor === 'pointer';

                if (isInteractive && isElementVisible(el, rect, style)) {
                    const testId = el.getAttribute('data-testid') || el.getAttribute('data-qa') || el.getAttribute('data-cy');
                    const text = (el.innerText || el.value || el.getAttribute('title') || '').trim().replace(/\\s+/g, ' ').substring(0, 60);
                    
                    // Filter out unstable dynamic IDs like btn_182947192
                    let stableId = el.id || null;
                    if (stableId && /\\d{4,}/.test(stableId)) {
                        stableId = null;
                    }

                    results.push({
                        tag: tag,
                        id: stableId,
                        name: el.getAttribute('name'),
                        type: el.getAttribute('type'),
                        role: role || null,
                        testId: testId,
                        ariaLabel: el.getAttribute('aria-label'),
                        placeholder: el.getAttribute('placeholder'),
                        text: text,
                        bbox: [Math.round(rect.x), Math.round(rect.y), Math.round(rect.width), Math.round(rect.height)]
                    });
                }

                // Shadow DOM Traversal
                if (el.shadowRoot) {
                    for (const child of el.shadowRoot.children) {
                        inspectNode(child);
                    }
                }

                // Child Elements
                for (const child of el.children) {
                    inspectNode(child);
                }
            }

            if (document.body) {
                inspectNode(document.body);
            }
            return JSON.stringify(results.slice(0, 150));
        }
        """;

    public static String extractReducedDom(Page page) {
        if (page == null) return "[]";
        try {
            Object result = page.evaluate(EXTRACT_SCRIPT);
            return result != null ? result.toString() : "[]";
        } catch (Exception e) {
            return "[]";
        }
    }
}
