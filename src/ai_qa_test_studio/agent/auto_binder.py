"""Shift-Left Auto-Binder: Tier1 semantic → Tier2 DistilledNode match (dual 0.65/0.85)."""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.case_binding import is_bindable_step
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import (
    BINDING_BOUND,
    BINDING_UNBOUND,
    HEAL_NAME_HEURISTIC_CONFIDENCE,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
    NON_BINDABLE_STEP_ACTIONS,
)
from ai_qa_test_studio.core.fingerprint import (
    apply_locator_to_element,
    fingerprint_from_shared,
    score_match,
    should_persist_heal,
)
from ai_qa_test_studio.core.models import (
    BindingStatus,
    DistilledNode,
    ElementFingerprint,
    Project,
    SharedElement,
    StepAuditEntry,
    TestCase,
    TestStep,
)

logger = logging.getLogger(__name__)

ProgressCb = Callable[[str], None]
LlmBindFn = Callable[[str, list[DistilledNode]], Awaitable[str | None]]


@dataclass
class SyncBindResult:
    case: TestCase
    audits: list[StepAuditEntry] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    yaml_persisted: bool = False


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
        self.last_audits: list[StepAuditEntry] = []

    async def sync_test_case(
        self,
        test_case: TestCase,
        base_url: str,
        on_progress: ProgressCb | None = None,
    ) -> TestCase:
        """Bind UNBOUND (and stale BOUND) UI steps. Returns updated case; see ``last_audits``."""
        result = await self.sync_with_audit(test_case, base_url=base_url, on_progress=on_progress)
        return result.case

    async def sync_with_audit(
        self,
        test_case: TestCase,
        base_url: str,
        on_progress: ProgressCb | None = None,
    ) -> SyncBindResult:
        progress = on_progress or (lambda _msg: None)
        self.last_bind_warnings = []
        self.last_audits = []
        yaml_persisted = False

        if base_url:
            await self.page.goto(base_url, wait_until="domcontentloaded")
            try:
                await self.page.wait_for_load_state("networkidle", timeout=5_000)
            except Exception:  # noqa: BLE001
                logger.debug("sync_networkidle_skipped url=%s", base_url)

        bindable_steps = [s for s in test_case.steps if s.enabled and is_bindable_step(s)]
        total_bindable = len(bindable_steps)
        bindable_index = 0

        for step in test_case.steps:
            if not step.enabled:
                continue
            action = str(step.action or "")
            if action in NON_BINDABLE_STEP_ACTIONS:
                # Navigate / Wait / AssertUrl — execute for page flow, never bind selectors.
                if action in {"goto", "navigate", "scroll", "wait"}:
                    try:
                        await self.resolver.execute_step(self.page, step, None)
                    except Exception as exc:  # noqa: BLE001
                        logger.warning("non_bindable_exec_failed step=%s err=%s", step.step_no, exc)
                progress(f"Bước {step.step_no}: {action} — bỏ qua ánh xạ selector")
                continue

            if not is_bindable_step(step):
                continue

            bindable_index += 1
            progress(f"Đang dò bước {bindable_index}/{total_bindable}…")

            shared = self.element_repo.get(step.element_ref or "")
            if shared is None:
                shared = _ensure_shared_for_step(self.element_repo, step, test_case)
                if shared is None:
                    msg = f"Bước {step.step_no}: thiếu SharedElement / element_ref"
                    progress(msg)
                    self.last_bind_warnings.append(msg)
                    self.last_audits.append(
                        StepAuditEntry(
                            step_no=step.step_no,
                            reason="missing_shared_element",
                            binding_status="UNBOUND",
                            persisted=False,
                        )
                    )
                    continue

            # Tier 1 — semantic match (0 token)
            matched = await self.resolver.try_semantic_match(self.page, shared)
            if matched is not None:
                await self.resolver.enrich_element_bundle(matched, shared)
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
                yaml_persisted = True
                progress(f"Bước {step.step_no}: Khớp tự động (0 token) -> BOUND")
                try:
                    await self.resolver.execute_step(self.page, step, shared)
                except Exception as exc:  # noqa: BLE001
                    logger.warning("tier1_execute_failed step=%s err=%s", step.step_no, exc)
                continue

            # Tier 2 — DistilledNode semantic grounding
            nodes = await self.distiller.distill(self.page)
            best = await self._best_node_match(step, shared, nodes)
            if best is None:
                reason = (
                    f"Bước {step.step_no}: không tìm thấy phần tử phù hợp "
                    f"(sai URL, iframe/shadow DOM, hoặc chưa hiển thị)"
                )
                step.binding_status = BINDING_UNBOUND  # type: ignore[assignment]
                progress(reason)
                self.last_bind_warnings.append(reason)
                self.last_audits.append(
                    StepAuditEntry(
                        step_no=step.step_no,
                        reason="no_match",
                        binding_status="UNBOUND",
                        confidence_score=0.0,
                        previous_selector=shared.css or shared.primary_playwright,
                        persisted=False,
                    )
                )
                continue

            node, confidence = best
            cand_fp = node.fingerprint
            css = cand_fp.css or shared.css or ""
            old_locator = shared.css or shared.primary_playwright

            if confidence < HEAL_RUN_THRESHOLD:
                step.binding_status = BINDING_UNBOUND  # type: ignore[assignment]
                warn = (
                    f"Bước {step.step_no}: confidence={confidence:.2f} < {HEAL_RUN_THRESHOLD} "
                    f"— giữ UNBOUND"
                )
                progress(warn)
                self.last_bind_warnings.append(warn)
                self.last_audits.append(
                    StepAuditEntry(
                        step_no=step.step_no,
                        old_locator=old_locator,
                        new_locator=css or None,
                        reason="below_run_threshold",
                        binding_status="UNBOUND",
                        confidence_score=confidence,
                        previous_selector=old_locator,
                        persisted=False,
                    )
                )
                continue

            if not should_persist_heal(confidence):
                # Mid band: candidate logged only — do not mutate SharedElement / YAML.
                warn = (
                    f"Bước {step.step_no}: độ tin cậy thấp "
                    f"({confidence:.2f}; cần ≥ {HEAL_PERSIST_THRESHOLD}) — "
                    f"không ghi YAML, giữ UNBOUND để kiểm tra"
                )
                progress(warn)
                self.last_bind_warnings.append(warn)
                self.last_audits.append(
                    StepAuditEntry(
                        step_no=step.step_no,
                        old_locator=old_locator,
                        new_locator=css or None,
                        reason="below_persist_threshold",
                        binding_status="UNBOUND",
                        confidence_score=confidence,
                        previous_selector=old_locator,
                        persisted=False,
                    )
                )
                continue

            # Persist band ≥ 0.85 — only after frame/shadow-scoped verify when needed
            bound_fp = ElementFingerprint(
                tag=cand_fp.tag,
                role=cand_fp.role,
                accessible_name=cand_fp.accessible_name,
                text_sample=cand_fp.text_sample,
                test_id=cand_fp.test_id,
                css=cand_fp.css,
                xpath=cand_fp.xpath,
                frame_path=list(cand_fp.frame_path),
                attrs_subset=dict(cand_fp.attrs_subset),
            )
            if not await self._verify_candidate(bound_fp, css):
                warn = (
                    f"Bước {step.step_no}: candidate score={confidence:.2f} "
                    f"nhưng không verify được trong frame_path={bound_fp.frame_path!r}"
                )
                progress(warn)
                self.last_bind_warnings.append(warn)
                step.binding_status = BINDING_UNBOUND  # type: ignore[assignment]
                self.last_audits.append(
                    StepAuditEntry(
                        step_no=step.step_no,
                        old_locator=old_locator,
                        new_locator=css or None,
                        reason="frame_verify_failed",
                        binding_status="UNBOUND",
                        confidence_score=confidence,
                        previous_selector=old_locator,
                        persisted=False,
                    )
                )
                continue

            persisted = apply_locator_to_element(
                shared,
                css=css,
                confidence=confidence,
                test_id=cand_fp.test_id,
                name=cand_fp.accessible_name,
                role=cand_fp.role,
                fingerprint=bound_fp,
            )
            step.binding_status = BINDING_BOUND  # type: ignore[assignment]
            yaml_persisted = yaml_persisted or persisted
            progress(
                f"Bước {step.step_no}: đồng bộ -> BOUND "
                f"(score={confidence:.2f}, persist={persisted}"
                f"{', frames=' + str(bound_fp.frame_path) if bound_fp.frame_path else ''})"
            )
            bound_status: BindingStatus = "BOUND"
            self.last_audits.append(
                StepAuditEntry(
                    step_no=step.step_no,
                    old_locator=old_locator,
                    new_locator=css or None,
                    reason="distilled_node_match",
                    binding_status=bound_status,
                    confidence_score=confidence,
                    previous_selector=old_locator,
                    persisted=persisted,
                )
            )
            try:
                await self.resolver.execute_step(self.page, step, shared)
            except Exception as exc:  # noqa: BLE001
                logger.warning("tier2_execute_failed step=%s err=%s", step.step_no, exc)

        return SyncBindResult(
            case=test_case,
            audits=list(self.last_audits),
            warnings=list(self.last_bind_warnings),
            yaml_persisted=yaml_persisted,
        )

    async def _verify_candidate(self, fingerprint: ElementFingerprint, css: str) -> bool:
        """Confirm locator resolves inside chained ``frame_locator`` when ``frame_path`` is set."""
        if not fingerprint.frame_path:
            return True
        if not css and not fingerprint.test_id:
            return True
        root = self.resolver.chain_frame_locators(self.page, fingerprint.frame_path)
        selectors: list[str] = []
        if fingerprint.test_id:
            selectors.append(f'[data-testid="{fingerprint.test_id}"]')
        if css:
            selectors.append(css)
        for sel in selectors:
            try:
                locator = self.resolver._to_locator(root, sel)
                count = await locator.count()
                if int(count) >= 1:
                    return True
            except Exception:  # noqa: BLE001
                continue
        return False

    async def _best_node_match(
        self,
        step: TestStep,
        shared: SharedElement,
        nodes: list[DistilledNode],
    ) -> tuple[DistilledNode, float] | None:
        if not nodes:
            return None
        expected_fp = fingerprint_from_shared(shared)
        intent = (step.intent_hint or shared.friendly_name or shared.name or "").strip()

        llm_id: str | None = None
        if self.llm_bind is not None:
            llm_id = await self.llm_bind(intent, nodes)

        best_node: DistilledNode | None = None
        best_score = 0.0
        for node in nodes:
            if llm_id and node.short_id == llm_id:
                scored = max(score_match(expected_fp, node.fingerprint), HEAL_PERSIST_THRESHOLD)
            else:
                scored = score_match(expected_fp, node.fingerprint)
                scored = max(scored, _intent_heuristic_score(intent, node))
            if scored > best_score:
                best_score = scored
                best_node = node

        if best_node is None:
            return None
        return best_node, round(best_score, 4)


def _intent_heuristic_score(intent: str, node: DistilledNode) -> float:
    needle = intent.lower().strip()
    if not needle:
        return 0.0
    fp = node.fingerprint
    hay = " ".join(
        part
        for part in (
            fp.accessible_name or "",
            fp.text_sample or "",
            fp.css or "",
            fp.test_id or "",
            fp.attrs_subset.get("placeholder", ""),
        )
        if part
    ).lower()
    if not hay:
        return 0.0
    if needle in hay or any(tok in hay for tok in needle.split() if len(tok) > 3):
        return HEAL_NAME_HEURISTIC_CONFIDENCE
    return 0.0


def _heuristic_bind(intent: str, element_map: dict[str, dict[str, Any]]) -> str | None:
    """Legacy map-based heuristic (unit tests + extract_legacy callers)."""
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


def _ensure_shared_for_step(
    repo: dict[str, SharedElement],
    step: TestStep,
    case: TestCase,
) -> SharedElement | None:
    """Create a lightweight SharedElement from intent when element_ref is missing."""
    hint = (step.intent_hint or "").strip()
    if not hint and not step.element_ref:
        return None
    element_id = step.element_ref or f"EL_{case.code}_S{step.step_no}"
    if element_id in repo:
        return repo[element_id]
    shared = SharedElement(
        element_id=element_id,
        page_name=case.code or "Page",
        friendly_name=hint or element_id,
        primary_playwright=None,
        name=hint or None,
    )
    repo[element_id] = shared
    step.element_ref = element_id
    return shared


def repo_from_project(project: Project) -> dict[str, SharedElement]:
    return {el.element_id: el for el in project.shared_elements}


__all__ = [
    "ShiftLeftAutoBinder",
    "SyncBindResult",
    "repo_from_project",
    "_heuristic_bind",
]
