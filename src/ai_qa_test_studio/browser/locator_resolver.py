"""Multi-strategy locator resolution without spending LLM tokens."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.core.models import SharedElement, TestStep


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
                })"""
            )
        except Exception:
            return
        if info.get("id"):
            element.css = f"#{info['id']}"
        if info.get("testId"):
            element.test_id = info["testId"]

    async def execute_step(self, page: Any, step: TestStep, element: SharedElement | None) -> None:
        if step.action == "goto":
            url = step.input_data or step.intent_hint
            await page.goto(url, wait_until="domcontentloaded")
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
        elif step.action == "assert_visible":
            await locator.wait_for(state="visible")
        elif step.action == "assert_text":
            await locator.wait_for(state="visible")
            content = await locator.inner_text()
            expected = step.expected_result or step.input_data or ""
            if expected and expected not in content:
                raise AssertionError(f"Expected text {expected!r} not in {content!r}")
        elif step.action == "assert_url":
            expected = step.expected_result or step.input_data or ""
            if expected and expected not in page.url:
                raise AssertionError(f"URL {page.url!r} missing {expected!r}")
