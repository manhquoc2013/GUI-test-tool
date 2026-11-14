"""Dual-Loop: Inner ReAct (one micro-tool) + Outer Ralph verification."""

from __future__ import annotations

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
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    RunReport,
    RunStepResult,
    SharedElement,
    TestCase,
)

logger = logging.getLogger(__name__)

CancelFn = Callable[[], bool]
VisionLocateFn = Callable[[str], Awaitable[dict[str, Any] | None]]


def _normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip())


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
    ) -> RunReport:
        locate = vision_locate if vision_locate is not None else self.vision_locate
        started = datetime.now(UTC).isoformat()
        step_results: list[RunStepResult] = []
        audits = []
        case_status: str = CASE_STATUS_PASS

        for step in test_case.steps:
            if should_cancel is not None and should_cancel():
                case_status = CASE_STATUS_CANCELLED
                step_results.append(
                    RunStepResult(
                        step_no=step.step_no,
                        action=step.action,
                        status=CASE_STATUS_CANCELLED,  # type: ignore[arg-type]
                        message="cancelled",
                    )
                )
                break
            if not step.enabled:
                step_results.append(
                    RunStepResult(
                        step_no=step.step_no,
                        action=step.action,
                        status="SKIPPED",
                        message="disabled",
                    )
                )
                continue
            t0 = time.perf_counter()
            element = self.element_repo.get(step.element_ref or "") if step.element_ref else None
            step_status: StepStatus | None = None
            confidence: float | None = None
            audit_entry = None
            try:
                await self._execute_step_mcp_first(step, element)
                status = CASE_STATUS_PASS
                message = "ok"
                step_status = StepStatus.PASSED
            except Exception as exc:  # noqa: BLE001
                message = str(exc)
                status = CASE_STATUS_FAIL
                step_status = StepStatus.FAILED
                if should_cancel is not None and should_cancel():
                    case_status = CASE_STATUS_CANCELLED
                    status = CASE_STATUS_CANCELLED
                    message = "cancelled"
                elif element is not None:
                    audit = await self.healer.heal_step(
                        self.page, step, element, vision_locate=locate
                    )
                    if should_cancel is not None and should_cancel():
                        case_status = CASE_STATUS_CANCELLED
                        status = CASE_STATUS_CANCELLED
                        message = "cancelled"
                    elif audit is not None:
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
                        except Exception as exc2:  # noqa: BLE001
                            message = f"{message}; retest_failed:{exc2}"
                            case_status = CASE_STATUS_FAIL
                            step_status = StepStatus.FAILED
                    else:
                        case_status = CASE_STATUS_FAIL
                else:
                    case_status = CASE_STATUS_FAIL
            duration = int((time.perf_counter() - t0) * 1000)
            step_results.append(
                RunStepResult(
                    step_no=step.step_no,
                    action=step.action,
                    status=status,  # type: ignore[arg-type]
                    step_status=step_status,
                    duration_ms=duration,
                    message=message,
                    confidence_score=confidence,
                    previous_selector=audit_entry.previous_selector if audit_entry else None,
                    audit=audit_entry,
                )
            )
            if case_status == CASE_STATUS_FAIL and status == CASE_STATUS_FAIL:
                break
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
                        message=f"verification missing: {expected!r}",
                    )
                )

        finished = datetime.now(UTC).isoformat()
        return RunReport(
            run_id=str(uuid.uuid4()),
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
                args["selector"] = element.primary_playwright or element.css or ""
            if step.input_data:
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
