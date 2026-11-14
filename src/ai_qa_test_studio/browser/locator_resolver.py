"""Multi-strategy locator resolution without spending LLM tokens."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.constants import STEP_SCROLL_DEFAULT_DELTA_Y, STEP_WAIT_DEFAULT_MS
from ai_qa_test_studio.core.models import ElementFingerprint, SharedElement, TestStep


class LocatorResolver:
    async def try_semantic_match(self, page: Any, element: SharedElement) -> Any | None:
        candidates: list[str] = []
        if element.primary_playwright:
            candidates.append(element.primary_playwright)
        if element.css:
            candidates.append(element.css)
        if element.test_id:
            candidates.append(f"[data-testid=\"{element.test_id}\"]")
        if element.xpath:
            candidates.append(f"xpath={element.xpath}")
        if element.fingerprint is not None:
            fp = element.fingerprint
            if fp.css and fp.css not in candidates:
                candidates.append(fp.css)
            if fp.test_id:
                tid = f"[data-testid=\"{fp.test_id}\"]"
                if tid not in candidates:
                    candidates.append(tid)
            if fp.xpath:
                xp = fp.xpath if fp.xpath.startswith("xpath=") else f"xpath={fp.xpath}"
                if xp not in candidates:
                    candidates.append(xp)

        for candidate in candidates:
            locator = self._to_locator(page, candidate)
            try:
                count = await locator.count()
            except Exception:
                continue
            if count == 1:
                return locator.first
        return None

    def _to_locator(self, page: Any, candidate: str) -> Any:
        text = candidate.strip()
        if text.startswith("get_by_role"):
            # Limited eval of common patterns — prefer CSS/xpath stored fields.
            return page.locator("body")  # pragma: no cover - rare path
        if text.startswith("xpath="):
            return page.locator(text)
        return page.locator(text)

    async def enrich_element_bundle(self, handle: Any, element: SharedElement) -> None:
        try:
            info = await handle.evaluate(
                """el => ({
                  id: el.id || '',
                  testId: el.getAttribute('data-testid') || '',
                  tag: el.tagName.toLowerCase(),
                  role: el.getAttribute('role') || '',
                  name: (el.innerText || el.getAttribute('aria-label')
                    || el.getAttribute('name') || '').trim().slice(0, 80),
                })"""
            )
        except Exception:
            return
        if info.get("id"):
            element.css = f"#{info['id']}"
        if info.get("testId"):
            element.test_id = info["testId"]
        if info.get("role"):
            element.role = str(info["role"])
        if info.get("name"):
            element.name = str(info["name"])
        element.fingerprint = ElementFingerprint(
            tag=str(info.get("tag") or ""),
            role=element.role,
            accessible_name=element.name,
            text_sample=element.name,
            test_id=element.test_id,
            css=element.css,
            xpath=element.xpath,
        )

    async def execute_step(self, page: Any, step: TestStep, element: SharedElement | None) -> None:
        if step.action in {"goto", "navigate"}:
            url = step.input_data or step.intent_hint
            await page.goto(url, wait_until="domcontentloaded")
            return
        if step.action == "wait":
            raw = step.input_data or str(STEP_WAIT_DEFAULT_MS)
            await page.wait_for_timeout(int(float(raw)))
            return
        if step.action == "scroll":
            raw = step.input_data or str(STEP_SCROLL_DEFAULT_DELTA_Y)
            await page.mouse.wheel(0, int(float(raw)))
            return
        if step.action == "api_request":
            return
        if step.action == "assert_url":
            expected = step.expected_result or step.input_data or ""
            if expected and expected not in page.url:
                raise AssertionError(f"URL {page.url!r} missing {expected!r}")
            return
        if element is None:
            raise RuntimeError(f"Missing element for step {step.step_no}")
        locator = await self.try_semantic_match(page, element)
        if locator is None:
            raise RuntimeError(f"Cannot resolve element {element.element_id}")
        if step.action == "click":
            await locator.click()
        elif step.action == "fill":
            await locator.fill(step.input_data or "")
        elif step.action == "select":
            await locator.select_option(step.input_data or "")
        elif step.action == "hover":
            await locator.hover()
        elif step.action == "press_enter":
            await locator.press("Enter")
        elif step.action in {"assert", "assert_visible"}:
            await locator.wait_for(state="visible")
        elif step.action == "assert_text":
            await locator.wait_for(state="visible")
            content = await locator.inner_text()
            expected = step.expected_result or step.input_data or ""
            if expected and expected not in content:
                raise AssertionError(f"Expected text {expected!r} not in {content!r}")
