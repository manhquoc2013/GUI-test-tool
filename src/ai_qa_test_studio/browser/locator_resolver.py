"""Multi-strategy locator resolution without spending LLM tokens."""

from __future__ import annotations

import re
from typing import Any

from ai_qa_test_studio.constants import (
    FRAME_PATH_NTH_SEP,
    STEP_SCROLL_DEFAULT_DELTA_Y,
    STEP_WAIT_DEFAULT_MS,
)
from ai_qa_test_studio.core.fingerprint import normalize_frame_path
from ai_qa_test_studio.core.models import ElementFingerprint, SharedElement, TestStep

_ROLE_NAME_RE = re.compile(
    r"""role\s*=\s*(?P<role>[\w-]+)\s*(?:\[\s*name\s*=\s*["'](?P<name>[^"']+)["']\s*\])?""",
    re.IGNORECASE,
)


class LocatorResolver:
    def bound_selector(self, element: SharedElement) -> str:
        """Deterministic BOUND selector: test_id → role+name → css → xpath (no LLM)."""
        for candidate in self._fingerprint_candidates(element):
            if candidate:
                return candidate
        return element.primary_playwright or element.css or ""

    def _fingerprint_candidates(self, element: SharedElement) -> list[str]:
        fp = element.fingerprint
        candidates: list[str] = []

        def _add(value: str) -> None:
            text = value.strip()
            if text and text not in candidates:
                candidates.append(text)

        test_id = (element.test_id or (fp.test_id if fp else None) or "").strip()
        if test_id:
            _add(f'[data-testid="{test_id}"]')

        role = (element.role or (fp.role if fp else None) or "").strip()
        name = (element.name or (fp.accessible_name if fp else None) or "").strip()
        if role and name:
            _add(f'role={role}[name="{name}"]')
        elif role:
            _add(f"role={role}")

        css = (element.css or (fp.css if fp else None) or "").strip()
        if css:
            _add(css)

        xpath = (element.xpath or (fp.xpath if fp else None) or "").strip()
        if xpath:
            _add(xpath if xpath.startswith("xpath=") else f"xpath={xpath}")

        if element.primary_playwright:
            _add(element.primary_playwright)
        return candidates

    async def try_semantic_match(self, page: Any, element: SharedElement) -> Any | None:
        root = await self._frame_root(page, element.fingerprint)
        for candidate in self._fingerprint_candidates(element):
            locator = self._to_locator(root, candidate)
            try:
                count = await locator.count()
            except Exception:
                continue
            if count >= 1:
                return locator.first
        return None

    async def _frame_root(self, page: Any, fingerprint: ElementFingerprint | None) -> Any:
        if fingerprint is None:
            return page
        return self.chain_frame_locators(page, fingerprint.frame_path)

    @staticmethod
    def chain_frame_locators(page: Any, frame_path: list[str] | str | None) -> Any:
        """Walk ``page.frame_locator(...)`` for each frame_path segment (incl. ``>> nth=N``)."""
        parts = normalize_frame_path(frame_path)
        if not parts:
            return page
        current: Any = page
        for frame_sel in parts:
            try:
                if FRAME_PATH_NTH_SEP in frame_sel:
                    base, _, nth_raw = frame_sel.partition(FRAME_PATH_NTH_SEP)
                    base_sel = base.strip() or "iframe"
                    current = current.frame_locator(base_sel).nth(int(nth_raw.strip()))
                else:
                    current = current.frame_locator(frame_sel)
            except Exception:
                return page
        return current

    def _to_locator(self, page: Any, candidate: str) -> Any:
        text = candidate.strip()
        role_match = _ROLE_NAME_RE.fullmatch(text) or (
            _ROLE_NAME_RE.search(text) if text.startswith("role=") else None
        )
        if role_match is not None:
            role = role_match.group("role")
            name = role_match.group("name")
            if hasattr(page, "get_by_role"):
                if name:
                    return page.get_by_role(role, name=name)
                return page.get_by_role(role)
        if text.startswith("get_by_role") and hasattr(page, "get_by_role"):
            # Best-effort: prefer stored role=/css fields; avoid eval.
            return page.locator("body")  # pragma: no cover - legacy string form
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
        prior = element.fingerprint
        element.fingerprint = ElementFingerprint(
            tag=str(info.get("tag") or ""),
            role=element.role,
            accessible_name=element.name,
            text_sample=element.name,
            test_id=element.test_id,
            css=element.css,
            xpath=element.xpath,
            frame_path=list(prior.frame_path) if prior is not None else [],
            attrs_subset=dict(prior.attrs_subset) if prior is not None else {},
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
