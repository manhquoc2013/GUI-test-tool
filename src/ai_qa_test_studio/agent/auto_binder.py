"""Shift-Left Auto-Binder: Tier1 semantic (0 token) → Tier2 AI DOM → Tier3 verify."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Any

from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import (
    BINDING_BOUND,
    HEAL_NAME_HEURISTIC_CONFIDENCE,
)
from ai_qa_test_studio.core.fingerprint import (
    apply_locator_to_element,
    fingerprint_from_meta,
    fingerprint_from_shared,
    score_match,
    should_persist_heal,
)
from ai_qa_test_studio.core.models import Project, SharedElement, TestCase

logger = logging.getLogger(__name__)

ProgressCb = Callable[[str], None]
LlmBindFn = Callable[[str, str], Awaitable[str | None]]


class ShiftLeftAutoBinder:
    def __init__(
        self,
        page: Any,
        element_repo: dict[str, SharedElement],
        distiller: DomDistiller | None = None,
        resolver: LocatorResolver | None = None,
        llm_bind: LlmBindFn | None = None,
    ) -> None:
        self.page = page
        self.element_repo = element_repo
        self.distiller = distiller or DomDistiller()
        self.resolver = resolver or LocatorResolver()
        self.llm_bind = llm_bind
        self.last_bind_warnings: list[str] = []

    async def sync_test_case(
        self,
        test_case: TestCase,
        base_url: str,
        on_progress: ProgressCb | None = None,
    ) -> TestCase:
        progress = on_progress or (lambda _msg: None)
        self.last_bind_warnings = []
        if base_url:
            await self.page.goto(base_url, wait_until="domcontentloaded")

        for step in test_case.steps:
            if not step.enabled:
                continue
            if step.action in {"goto", "navigate", "scroll", "wait", "api_request"}:
                await self.resolver.execute_step(self.page, step, None)
                step.binding_status = BINDING_BOUND  # type: ignore[assignment]
                progress(f"Bước {step.step_no}: {step.action} -> BOUND")
                continue
            if step.action == "verify_db":
                step.binding_status = BINDING_BOUND  # type: ignore[assignment]
                continue
            shared = self.element_repo.get(step.element_ref or "")
            if shared is None:
                progress(f"Bước {step.step_no}: thiếu SharedElement")
                continue

            matched = await self.resolver.try_semantic_match(self.page, shared)
            if matched is not None:
                await self.resolver.enrich_element_bundle(matched, shared)
                # Exact semantic hit — always YAML-safe
                apply_locator_to_element(
                    shared,
                    css=shared.css or shared.primary_playwright or "",
                    confidence=1.0,
                    test_id=shared.test_id,
                    name=shared.name,
                    role=shared.role,
                    fingerprint=fingerprint_from_shared(shared),
                )
                step.binding_status = BINDING_BOUND  # type: ignore[assignment]
                progress(f"Bước {step.step_no}: Khớp tự động (0 token) -> BOUND")
                try:
                    await self.resolver.execute_step(self.page, step, shared)
                except Exception as exc:  # noqa: BLE001
                    logger.warning("tier1_execute_failed step=%s err=%s", step.step_no, exc)
                continue

            # Tier 2 — AI DOM grounding
            compact_dom, element_map = await self.distiller.extract(self.page)
            element_id: str | None = None
            if self.llm_bind is not None:
                element_id = await self.llm_bind(step.intent_hint, compact_dom)
            else:
                element_id = _heuristic_bind(step.intent_hint, element_map)

            if element_id and element_id in element_map:
                meta = element_map[element_id]
                cand_fp = fingerprint_from_meta(meta)
                expected_fp = fingerprint_from_shared(shared)
                confidence = score_match(expected_fp, cand_fp)
                if confidence < HEAL_NAME_HEURISTIC_CONFIDENCE:
                    confidence = HEAL_NAME_HEURISTIC_CONFIDENCE
                css = str(meta.get("css") or shared.css or "")
                persisted = apply_locator_to_element(
                    shared,
                    css=css,
                    confidence=confidence,
                    test_id=str(meta.get("testId") or "") or None,
                    name=str(meta.get("name") or "") or None,
                    role=str(meta.get("role") or "") or None,
                    fingerprint=cand_fp,
                )
                step.binding_status = BINDING_BOUND  # type: ignore[assignment]
                if not persisted:
                    warn = (
                        f"Bước {step.step_no}: BOUND runtime score={confidence:.2f} "
                        f"< persist — YAML không auto-patch"
                    )
                    self.last_bind_warnings.append(warn)
                    logger.warning("bind_below_persist step=%s score=%.3f", step.step_no, confidence)
                    progress(warn)
                else:
                    progress(
                        f"Bước {step.step_no}: AI/heuristic đồng bộ [{element_id}] "
                        f"-> BOUND (persist={should_persist_heal(confidence)})"
                    )
                try:
                    await self.resolver.execute_step(self.page, step, shared)
                except Exception as exc:  # noqa: BLE001
                    logger.warning("tier2_execute_failed step=%s err=%s", step.step_no, exc)
            else:
                progress(f"Bước {step.step_no}: vẫn UNBOUND")
        return test_case


def _heuristic_bind(intent: str, element_map: dict[str, dict[str, Any]]) -> str | None:
    needle = (intent or "").lower()
    for eid, meta in element_map.items():
        name = str(meta.get("name") or "").lower()
        css = str(meta.get("css") or "").lower()
        if needle and (
            needle in name
            or any(tok in name or tok in css for tok in needle.split() if len(tok) > 3)
        ):
            return eid
    return None


def repo_from_project(project: Project) -> dict[str, SharedElement]:
    return {el.element_id: el for el in project.shared_elements}
