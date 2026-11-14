"""Self-healing micro-patcher: DOM first, vision/midscene hook second."""

from __future__ import annotations

import logging
from typing import Any

from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import BINDING_HEALED
from ai_qa_test_studio.core.models import SharedElement, StepAuditEntry, TestStep

logger = logging.getLogger(__name__)


class Healer:
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
        compact_dom, element_map = await self.distiller.extract(page)
        for eid, meta in element_map.items():
            name = str(meta.get("name") or "").lower()
            if step.intent_hint.lower()[:20] in name or name in step.intent_hint.lower():
                new_css = str(meta.get("css") or "")
                element.css = new_css
                element.primary_playwright = new_css
                step.binding_status = BINDING_HEALED  # type: ignore[assignment]
                logger.info("healed_dom step=%s element=%s", step.step_no, eid)
                return StepAuditEntry(
                    step_no=step.step_no,
                    old_locator=old,
                    new_locator=new_css,
                    reason="dom_heuristic",
                    binding_status="HEALED",
                )

        if vision_locate is not None:
            try:
                vision = await vision_locate(step.intent_hint)
            except Exception as exc:  # noqa: BLE001
                logger.warning("vision_heal_failed err=%s", exc)
                vision = None
            if isinstance(vision, dict) and vision.get("css"):
                new_css = str(vision["css"])
                element.css = new_css
                element.primary_playwright = new_css
                element.vision_prompt = step.intent_hint
                step.binding_status = BINDING_HEALED  # type: ignore[assignment]
                return StepAuditEntry(
                    step_no=step.step_no,
                    old_locator=old,
                    new_locator=new_css,
                    reason="midscene_vision",
                    binding_status="HEALED",
                )
        return None
