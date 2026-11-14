"""Dual-Loop: Inner ReAct (one micro-tool) + Outer Ralph verification."""

from __future__ import annotations

import logging
import re
import time
import uuid
from collections.abc import Callable
from datetime import UTC, datetime
from typing import Any

from ai_qa_test_studio.agent.healer import Healer
from ai_qa_test_studio.browser.locator_resolver import LocatorResolver
from ai_qa_test_studio.constants import (
    CASE_STATUS_CANCELLED,
    CASE_STATUS_FAIL,
    CASE_STATUS_PASS,
    TOKEN_MAX_ATOMIC_OUTPUT,
)
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    RunReport,
    RunStepResult,
    SharedElement,
    TestCase,
)

logger = logging.getLogger(__name__)

CancelFn = Callable[[], bool]


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
    ) -> None:
        self.page = page
        self.element_repo = element_repo
        self.resolver = resolver or LocatorResolver()
        self.healer = healer or Healer(resolver=self.resolver)
        self.max_tokens = max_tokens

    async def run_case(
        self,
        test_case: TestCase,
        project_name: str = "",
        should_cancel: CancelFn | None = None,
    ) -> RunReport:
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
            try:
                await self.resolver.execute_step(self.page, step, element)
                status = CASE_STATUS_PASS
                message = "ok"
            except Exception as exc:  # noqa: BLE001
                message = str(exc)
                status = CASE_STATUS_FAIL
                if element is not None:
                    audit = await self.healer.heal_step(self.page, step, element)
                    if audit is not None:
                        audits.append(audit)
                        try:
                            await self.resolver.execute_step(self.page, step, element)
                            status = "HEALED"
                            message = f"healed:{audit.reason}"
                        except Exception as exc2:  # noqa: BLE001
                            message = f"{message}; retest_failed:{exc2}"
                            case_status = CASE_STATUS_FAIL
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
                    duration_ms=duration,
                    message=message,
                )
            )
            if case_status == CASE_STATUS_FAIL and status == CASE_STATUS_FAIL:
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
