"""Dual-Loop: Inner ReAct (one micro-tool) + Outer Ralph verification."""

from __future__ import annotations

import asyncio
import logging
import re
import time
import uuid
from collections.abc import Awaitable, Callable
from datetime import UTC, datetime
from typing import Any

from ai_qa_test_studio.agent.healer import Healer, heal_step_status
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import (
    CASE_STATUS_CANCELLED,
    CASE_STATUS_FAIL,
    CASE_STATUS_PASS,
    MCP_ACTIONS_ENABLED,
    MICRO_TOOL_LOAD_SKILL,
    TOKEN_MAX_ATOMIC_OUTPUT,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.fingerprint import should_run_heal
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    CaseStatus,
    RunReport,
    RunStepResult,
    SharedElement,
    TestCase,
)
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.reporting.evidence import EvidenceCollector

logger = logging.getLogger(__name__)

CancelFn = Callable[[], bool]
VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]
StepProgressFn = Callable[[int, str, str, float, str], None]


def _normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip())


def _snapshot_shared_element(element: SharedElement) -> dict[str, Any]:
    """Capture SharedElement fields so mid-band heal can restore before YAML save."""
    fp = element.fingerprint
    return {
        "css": element.css,
        "primary_playwright": element.primary_playwright,
        "test_id": element.test_id,
        "role": element.role,
        "name": element.name,
        "xpath": element.xpath,
        "vision_prompt": element.vision_prompt,
        "fingerprint": fp.model_copy(deep=True) if fp is not None else None,
    }


def _restore_shared_element(element: SharedElement, snap: dict[str, Any]) -> None:
    """Revert runtime-only heal mutations so mid-band scores never overwrite YAML."""
    element.css = snap["css"]
    element.primary_playwright = snap["primary_playwright"]
    element.test_id = snap["test_id"]
    element.role = snap["role"]
    element.name = snap["name"]
    element.xpath = snap["xpath"]
    element.vision_prompt = snap["vision_prompt"]
    element.fingerprint = snap["fingerprint"]


class DualLoopAgent:
    def __init__(
        self,
        page: Any,
        element_repo: dict[str, SharedElement],
        resolver: LocatorResolver | None = None,
        healer: Healer | None = None,
        max_tokens: int = TOKEN_MAX_ATOMIC_OUTPUT,
        vision_locate: VisionLocateFn | None = None,
        skill_context: str = "",
        skill_loader: ProgressiveSkillLoader | None = None,
        evidence: EvidenceCollector | None = None,
    ) -> None:
        self.page = page
        self.element_repo = element_repo
        self.resolver = resolver or LocatorResolver()
        self.healer = healer or Healer(resolver=self.resolver)
        self.max_tokens = max_tokens
        self.vision_locate = vision_locate
        self.skill_context = skill_context
        self.skill_loader = skill_loader
        self.mcp_actions_enabled = MCP_ACTIONS_ENABLED
        self.mcp_executor: Any | None = None
        self.evidence = evidence

    def invoke_micro_tool(self, tool_name: str, args: dict[str, Any] | None = None) -> str:
        if tool_name == MICRO_TOOL_LOAD_SKILL:
            if self.skill_loader is None:
                return "Skill loader unavailable."
            body = self.skill_loader.invoke_tool(tool_name, args)
            self.skill_context = body
            return body
        return f"Unsupported micro-tool: {tool_name}"

    async def run_case(
        self,
        test_case: TestCase,
        project_name: str = "",
        should_cancel: CancelFn | None = None,
        vision_locate: VisionLocateFn | None = None,
        on_step: StepProgressFn | None = None,
        run_id: str | None = None,
    ) -> RunReport:
        locate = vision_locate if vision_locate is not None else self.vision_locate
        started = datetime.now(UTC).isoformat()
        step_results: list[RunStepResult] = []
        audits = []
        case_status: str = CASE_STATUS_PASS
        masker = get_secret_masker()
        active_run_id = run_id or str(uuid.uuid4())
        blocked = False

        for step in test_case.steps:
            if should_cancel is not None and should_cancel():
                case_status = CASE_STATUS_CANCELLED
                result = RunStepResult(
                    step_no=step.step_no,
                    action=step.action,
                    status=CASE_STATUS_CANCELLED,  # type: ignore[arg-type]
                    step_status=StepStatus.SKIPPED,
                    message="cancelled",
                )
                step_results.append(result)
                self._emit_progress(on_step, result)
                break

            if not step.enabled:
                result = RunStepResult(
                    step_no=step.step_no,
                    action=step.action,
                    status="SKIPPED",
                    step_status=StepStatus.SKIPPED,
                    message="disabled",
                )
                step_results.append(result)
                self._emit_progress(on_step, result)
                continue

            if blocked:
                result = RunStepResult(
                    step_no=step.step_no,
                    action=step.action,
                    status="BLOCKED",
                    step_status=StepStatus.BLOCKED,
                    message="blocked_by_prior_failure",
                )
                step_results.append(result)
                self._emit_progress(on_step, result)
                continue

            self._emit_progress(
                on_step,
                RunStepResult(
                    step_no=step.step_no,
                    action=step.action,
                    status="NOT_RUN",
                    step_status=StepStatus.RUNNING,
                    message="",
                ),
            )

            t0 = time.perf_counter()
            element = self.element_repo.get(step.element_ref or "") if step.element_ref else None
            step_status: StepStatus = StepStatus.RUNNING
            confidence: float | None = None
            audit_entry = None
            status: CaseStatus = "PASS"
            message = "ok"
            try:
                await self._execute_step_mcp_first(step, element)
                status = "PASS"
                message = "ok"
                step_status = StepStatus.PASSED
            except Exception as exc:  # noqa: BLE001
                message = masker.mask_text(str(exc))
                status = "FAIL"
                step_status = StepStatus.FAILED
                if should_cancel is not None and should_cancel():
                    case_status = CASE_STATUS_CANCELLED
                    status = CASE_STATUS_CANCELLED  # type: ignore[assignment]
                    message = "cancelled"
                    step_status = StepStatus.SKIPPED
                elif element is not None:
                    pre_heal = _snapshot_shared_element(element)
                    audit = await self.healer.heal_step(
                        self.page, step, element, vision_locate=locate
                    )
                    if should_cancel is not None and should_cancel():
                        case_status = CASE_STATUS_CANCELLED
                        status = CASE_STATUS_CANCELLED  # type: ignore[assignment]
                        message = "cancelled"
                        step_status = StepStatus.SKIPPED
                        if audit is not None and not audit.persisted:
                            _restore_shared_element(element, pre_heal)
                    elif audit is not None and should_run_heal(audit.confidence_score or 0.0):
                        audits.append(audit)
                        audit_entry = audit
                        confidence = audit.confidence_score
                        step_status = heal_step_status(confidence) or StepStatus.HEALED
                        try:
                            await self._execute_step_mcp_first(step, element)
                            status = "HEALED"
                            message = f"healed:{audit.reason}"
                            if not audit.persisted:
                                message = f"{message};persist_skipped"
                                # Runtime-only: restore SharedElement so Run save ≠ YAML overwrite
                                _restore_shared_element(element, pre_heal)
                        except Exception as exc2:  # noqa: BLE001
                            if not audit.persisted:
                                _restore_shared_element(element, pre_heal)
                            message = masker.mask_text(
                                f"{message}; retest_failed:{exc2}"
                            )
                            case_status = CASE_STATUS_FAIL
                            status = "FAIL"
                            step_status = StepStatus.FAILED
                            blocked = True
                    else:
                        if audit is not None:
                            audits.append(audit)
                            audit_entry = audit
                            confidence = audit.confidence_score
                            if not audit.persisted:
                                _restore_shared_element(element, pre_heal)
                        case_status = CASE_STATUS_FAIL
                        status = "FAIL"
                        step_status = StepStatus.FAILED
                        blocked = True
                else:
                    case_status = CASE_STATUS_FAIL
                    blocked = True

            duration = int((time.perf_counter() - t0) * 1000)
            result = RunStepResult(
                step_no=step.step_no,
                action=step.action,
                status=status,
                step_status=step_status,
                duration_ms=duration,
                message=masker.mask_text(message),
                confidence_score=confidence,
                previous_selector=audit_entry.previous_selector if audit_entry else None,
                audit=audit_entry,
            )
            if step_status in {StepStatus.HEALED, StepStatus.FAILED}:
                await self._capture_evidence(active_run_id, test_case.id, result)
            step_results.append(result)
            self._emit_progress(on_step, result)

            if case_status == CASE_STATUS_CANCELLED:
                break

        if case_status == CASE_STATUS_PASS and test_case.expected_final_result:
            body_text = ""
            try:
                body_text = await self.page.inner_text("body")
            except Exception as exc:  # noqa: BLE001
                logger.warning("verify_read_failed err=%s", exc)
            expected = _normalize_text(test_case.expected_final_result)
            haystack = _normalize_text(body_text)
            if expected and expected not in haystack:
                case_status = CASE_STATUS_FAIL
                step_results.append(
                    RunStepResult(
                        step_no=len(step_results) + 1,
                        action="assert_text",
                        status=CASE_STATUS_FAIL,  # type: ignore[arg-type]
                        step_status=StepStatus.FAILED,
                        message=f"verification missing: {expected!r}",
                    )
                )

        finished = datetime.now(UTC).isoformat()
        report = RunReport(
            run_id=active_run_id,
            project_name=project_name,
            started_at=started,
            finished_at=finished,
            cases=[
                CaseRunResult(
                    case_id=test_case.id,
                    case_code=test_case.code,
                    title=test_case.title,
                    status=case_status,  # type: ignore[arg-type]
                    steps=step_results,
                )
            ],
            healing_audit=audits,
        )
        if self.evidence is not None:
            await self._finalize_evidence(report)
        return report

    def _emit_progress(
        self,
        on_step: StepProgressFn | None,
        result: RunStepResult,
    ) -> None:
        if on_step is None:
            return
        live = (
            result.step_status.value
            if result.step_status is not None
            else str(result.status)
        )
        on_step(
            result.step_no,
            result.action,
            live,
            float(result.duration_ms),
            result.message,
        )

    async def _capture_evidence(
        self,
        run_id: str,
        case_id: str,
        result: RunStepResult,
    ) -> None:
        if self.evidence is None:
            return
        try:
            png = await self.page.screenshot(type="png")
        except Exception as exc:  # noqa: BLE001
            logger.warning("evidence_screenshot_failed step=%s err=%s", result.step_no, exc)
            return
        if not isinstance(png, (bytes, bytearray)):
            return

        def _write() -> None:
            assert self.evidence is not None
            self.evidence.persist_step_png(
                run_id,
                case_id,
                result,
                bytes(png),
            )

        await asyncio.to_thread(_write)

    async def _finalize_evidence(self, report: RunReport) -> None:
        if self.evidence is None:
            return
        await asyncio.to_thread(self.evidence.collect, report)

    async def _execute_step_mcp_first(
        self,
        step: Any,
        element: SharedElement | None,
    ) -> None:
        """Prefer SmartMCPToolExecutor when MCP_ACTIONS_ENABLED; else in-process page."""
        if self.mcp_actions_enabled and self.mcp_executor is not None:
            tool = f"browser_{step.action}"
            args: dict[str, Any] = {}
            if element is not None:
                # BOUND fast-path: fingerprint-derived selector, never LLM locate
                args["selector"] = self.resolver.bound_selector(element)
            if step.input_data:
                # Raw secrets go to Playwright; masking is outbound-only in executor/logs
                args["text"] = step.input_data
            result = await self.mcp_executor.execute("playwright", tool, args)
            if result.get("ok"):
                return
            logger.info(
                "mcp_first_fallback step=%s class=%s",
                step.step_no,
                result.get("error_class"),
            )
        await self.resolver.execute_step(self.page, step, element)
