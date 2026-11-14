"""Self-healing interceptor — dual thresholds 0.65 run / 0.85 persist."""

from __future__ import annotations

import logging
from typing import Any

from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import (
    BINDING_HEALED,
    HEAL_NAME_HEURISTIC_CONFIDENCE,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
    HEAL_VISION_DEFAULT_CONFIDENCE,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.fingerprint import (
    apply_locator_to_element,
    fingerprint_from_meta,
    fingerprint_from_shared,
    score_match,
    should_persist_heal,
    should_run_heal,
)
from ai_qa_test_studio.core.models import DistilledNode, SharedElement, StepAuditEntry, TestStep

logger = logging.getLogger(__name__)


class Healer:
    """SelfHealingInterceptor — ≥ run threshold continues; ≥ persist patches YAML-safe."""

    def __init__(
        self,
        distiller: DomDistiller | None = None,
        resolver: LocatorResolver | None = None,
    ) -> None:
        self.distiller = distiller or DomDistiller()
        self.resolver = resolver or LocatorResolver()

    async def heal_step(
        self,
        page: Any,
        step: TestStep,
        element: SharedElement,
        vision_locate: Any | None = None,
    ) -> StepAuditEntry | None:
        old = element.primary_playwright or element.css
        expected_fp = fingerprint_from_shared(element)

        best_id: str | None = None
        best_score = 0.0
        best_meta: dict[str, Any] | None = None

        nodes = await self._distill_nodes(page)
        for node in nodes:
            cand = node.fingerprint
            scored = score_match(expected_fp, cand)
            name = (cand.accessible_name or "").lower()
            intent = (step.intent_hint or "").lower()
            if intent and name and (intent[:20] in name or name in intent):
                scored = max(scored, HEAL_NAME_HEURISTIC_CONFIDENCE)
            if scored > best_score:
                best_score = scored
                best_id = node.short_id
                best_meta = {
                    "tag": cand.tag,
                    "role": cand.role or "",
                    "name": cand.accessible_name or "",
                    "testId": cand.test_id or "",
                    "css": cand.css or "",
                    "xpath": cand.xpath or "",
                }

        if best_meta is not None and should_run_heal(best_score):
            new_css = str(best_meta.get("css") or "")
            cand_fp = fingerprint_from_meta(best_meta)
            # Never persist DistilledNode short_id into YAML fingerprint
            persisted = apply_locator_to_element(
                element,
                css=new_css,
                confidence=best_score,
                test_id=str(best_meta.get("testId") or "") or None,
                name=str(best_meta.get("name") or "") or None,
                role=str(best_meta.get("role") or "") or None,
                fingerprint=cand_fp,
            )
            step.binding_status = BINDING_HEALED  # type: ignore[assignment]
            reason = self._reason_for_score(best_score, heuristic=best_score <= HEAL_NAME_HEURISTIC_CONFIDENCE)
            if not persisted:
                logger.warning(
                    "heal_below_persist step=%s score=%.3f — runtime only, no YAML",
                    step.step_no,
                    best_score,
                )
            else:
                logger.info(
                    "healed_dom step=%s element=%s score=%.3f persisted=1",
                    step.step_no,
                    best_id,
                    best_score,
                )
            return StepAuditEntry(
                step_no=step.step_no,
                old_locator=old,
                new_locator=new_css,
                reason=reason,
                binding_status="HEALED",
                confidence_score=best_score,
                previous_selector=old,
                healed_selector=new_css,
                persisted=persisted,
            )

        if vision_locate is not None:
            try:
                vision = await vision_locate(step.intent_hint)
            except Exception as exc:  # noqa: BLE001
                logger.warning("vision_heal_failed err=%s", exc)
                vision = None
            if isinstance(vision, dict) and vision.get("css"):
                new_css = str(vision["css"])
                confidence = float(
                    vision.get("confidence") or HEAL_VISION_DEFAULT_CONFIDENCE
                )
                if not should_run_heal(confidence):
                    return StepAuditEntry(
                        step_no=step.step_no,
                        old_locator=old,
                        new_locator=new_css,
                        reason="midscene_vision_low_confidence",
                        binding_status=step.binding_status,
                        confidence_score=confidence,
                        previous_selector=old,
                        healed_selector=None,
                        persisted=False,
                    )
                element.vision_prompt = step.intent_hint
                persisted = apply_locator_to_element(
                    element, css=new_css, confidence=confidence
                )
                step.binding_status = BINDING_HEALED  # type: ignore[assignment]
                reason = self._reason_for_score(confidence, base="midscene_vision")
                if not persisted:
                    logger.warning(
                        "vision_heal_below_persist step=%s score=%.3f",
                        step.step_no,
                        confidence,
                    )
                return StepAuditEntry(
                    step_no=step.step_no,
                    old_locator=old,
                    new_locator=new_css,
                    reason=reason,
                    binding_status="HEALED",
                    confidence_score=confidence,
                    previous_selector=old,
                    healed_selector=new_css,
                    persisted=persisted,
                )
        return None

    async def _distill_nodes(self, page: Any) -> list[DistilledNode]:
        distill = getattr(self.distiller, "distill", None)
        raw_nodes: Any = None
        if callable(distill):
            maybe = distill(page)
            if hasattr(maybe, "__await__"):
                raw_nodes = await maybe
            elif isinstance(maybe, list):
                raw_nodes = maybe
        if isinstance(raw_nodes, list) and raw_nodes:
            distilled = [n for n in raw_nodes if isinstance(n, DistilledNode)]
            if distilled:
                return distilled
        # Legacy mock / DomDistiller.extract path
        extract = getattr(self.distiller, "extract", None)
        if not callable(extract):
            return []
        extracted = extract(page)
        if hasattr(extracted, "__await__"):
            _compact, element_map = await extracted
        else:
            return []
        legacy: list[DistilledNode] = []
        if isinstance(element_map, dict):
            for eid, meta in element_map.items():
                if not isinstance(meta, dict):
                    continue
                fp = fingerprint_from_meta(meta)
                legacy.append(
                    DistilledNode(
                        short_id=str(eid),
                        fingerprint=fp,
                    )
                )
        return legacy

    @staticmethod
    def _reason_for_score(
        score: float,
        *,
        heuristic: bool = False,
        base: str = "",
    ) -> str:
        if base:
            reason = base
        elif heuristic:
            reason = "dom_heuristic"
        else:
            reason = "fingerprint"
        if HEAL_RUN_THRESHOLD <= score < HEAL_PERSIST_THRESHOLD:
            return f"{reason};medium_confidence"
        return reason


# Preferred name in roadmap / contracts language
SelfHealingInterceptor = Healer


def heal_step_status(confidence: float | None) -> StepStatus | None:
    if confidence is None:
        return None
    if should_run_heal(confidence):
        return StepStatus.HEALED
    return StepStatus.FAILED


def may_persist_bound(confidence: float) -> bool:
    return should_persist_heal(confidence)
