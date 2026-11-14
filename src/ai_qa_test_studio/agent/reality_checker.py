"""RealityChecker — APPLY gate + post-run SWARM_AUDIT analysis."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.validate_case_schema import validate_case_schema
from ai_qa_test_studio.constants import (
    ASSERT_STEP_ACTIONS,
    AUDIT_ASSERT_MISMATCH_RE,
    AUDIT_CODE_FALSE_POSITIVE_PASS,
    AUDIT_CODE_HEAL_REVIEW,
    AUDIT_CONSOLE_ERROR_RE,
    AUDIT_FAIL_APP_RUNTIME_ERROR,
    AUDIT_FAIL_ASSERTION_MISMATCH,
    AUDIT_FAIL_ENVIRONMENT_TIMEOUT,
    AUDIT_FAIL_SELECTOR_DRIFT,
    AUDIT_HTTP_ERROR_RE,
    AUDIT_SELECTOR_DRIFT_RE,
    AUDIT_TIMEOUT_RE,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import RunReport, RunStepResult, TestCase

_HTTP_RE = re.compile(AUDIT_HTTP_ERROR_RE)
_CONSOLE_RE = re.compile(AUDIT_CONSOLE_ERROR_RE)
_SELECTOR_RE = re.compile(AUDIT_SELECTOR_DRIFT_RE)
_ASSERT_RE = re.compile(AUDIT_ASSERT_MISMATCH_RE)
_TIMEOUT_RE = re.compile(AUDIT_TIMEOUT_RE)

_PASS_STATUSES = frozenset({"PASS", StepStatus.PASSED.value, "Passed"})
_HEALED_STATUSES = frozenset({"HEALED", StepStatus.HEALED.value, "Healed"})
_FAIL_STATUSES = frozenset(
    {"FAIL", "FAILED", "BLOCKED", StepStatus.FAILED.value, StepStatus.BLOCKED.value}
)


@dataclass(frozen=True)
class RealityFinding:
    code: str
    message: str
    severity: str = "error"
    case_code: str = ""
    step_no: int | None = None
    confidence: float | None = None
    remediation: str = ""
    previous_selector: str | None = None
    healed_selector: str | None = None
    failure_class: str = ""


@dataclass(frozen=True)
class RealityCheckResult:
    ok: bool
    findings: list[RealityFinding] = field(default_factory=list)
    case: TestCase | None = None

    @property
    def message(self) -> str:
        if self.ok:
            return "ok"
        return "; ".join(f"{f.code}: {f.message}" for f in self.findings) or "failed"


class RealityChecker:
    """Schema + lightweight semantic checks before project_tree_ops APPLY."""

    def check_case(self, payload: TestCase | dict[str, Any]) -> RealityCheckResult:
        ok, msg, case = validate_case_schema(payload)
        findings: list[RealityFinding] = []
        if not ok or case is None:
            findings.append(RealityFinding(code="schema", message=msg))
            return RealityCheckResult(ok=False, findings=findings, case=None)

        enabled = [s for s in case.steps if s.enabled]
        if not any(s.intent_hint.strip() for s in enabled):
            findings.append(
                RealityFinding(
                    code="intent_missing",
                    message="APPLY case has no intent_hint on enabled steps",
                    severity="warning",
                )
            )
        unbound_with_ref = [
            s
            for s in enabled
            if s.element_ref and str(s.binding_status) == "UNBOUND"
        ]
        if len(unbound_with_ref) == len(enabled) and enabled and all(
            s.element_ref for s in enabled
        ):
            findings.append(
                RealityFinding(
                    code="all_unbound",
                    message="All element steps still UNBOUND — prefer DRAFT or bind first",
                    severity="warning",
                )
            )
        # Warnings do not block; errors do
        errors = [f for f in findings if f.severity == "error"]
        return RealityCheckResult(ok=not errors, findings=findings, case=case)

    def audit_report(self, report: RunReport) -> RealityCheckResult:
        findings: list[RealityFinding] = []
        if not report.cases:
            findings.append(RealityFinding(code="empty_report", message="No cases in report"))

        for case in report.cases:
            has_assert = any(str(s.action) in ASSERT_STEP_ACTIONS for s in case.steps)
            case_passed = str(case.status) in _PASS_STATUSES
            if case_passed and not has_assert:
                findings.append(
                    RealityFinding(
                        code=AUDIT_CODE_FALSE_POSITIVE_PASS,
                        message=(
                            f"{case.case_code}: Passed without Assert verification step"
                        ),
                        severity="warning",
                        case_code=case.case_code,
                        remediation="Bổ sung bước Assert xác minh kết quả mong đợi",
                    )
                )

            for step in case.steps:
                findings.extend(self._audit_step(case.case_code, step))

        for audit in report.healing_audit:
            conf = audit.confidence_score
            if conf is None:
                continue
            if HEAL_RUN_THRESHOLD <= conf < HEAL_PERSIST_THRESHOLD:
                findings.append(
                    RealityFinding(
                        code=AUDIT_CODE_HEAL_REVIEW,
                        message=(
                            f"step {audit.step_no} healed at {conf:.2f} — "
                            "cần duyệt & Sync lại YAML"
                        ),
                        severity="warning",
                        step_no=audit.step_no,
                        confidence=conf,
                        previous_selector=audit.previous_selector or audit.old_locator,
                        healed_selector=audit.new_locator,
                        remediation="Duyệt locator yếu rồi Sync lại SharedElement YAML",
                    )
                )
            elif conf is not None and not audit.persisted:
                findings.append(
                    RealityFinding(
                        code="heal_not_persisted",
                        message=(
                            f"step {audit.step_no} healed at "
                            f"{conf:.2f} without YAML persist"
                        ),
                        severity="warning",
                        step_no=audit.step_no,
                        confidence=conf,
                    )
                )

        fail_count = sum(1 for c in report.cases if str(c.status) in {"FAIL", "BLOCKED"})
        if fail_count:
            findings.append(
                RealityFinding(
                    code="failed_cases",
                    message=f"{fail_count} case(s) FAIL/BLOCKED",
                    severity="warning",
                )
            )
        errors = [f for f in findings if f.severity == "error"]
        return RealityCheckResult(ok=not errors, findings=findings)

    def _audit_step(self, case_code: str, step: RunStepResult) -> list[RealityFinding]:
        out: list[RealityFinding] = []
        status = str(step.step_status or step.status or "")
        blob = self._step_signal_text(step)

        if status in _PASS_STATUSES and self._has_runtime_noise(step, blob):
            out.append(
                RealityFinding(
                    code=AUDIT_CODE_FALSE_POSITIVE_PASS,
                    message=(
                        f"{case_code} step {step.step_no}: Passed but HTTP/console errors "
                        "detected during run"
                    ),
                    severity="warning",
                    case_code=case_code,
                    step_no=step.step_no,
                    remediation="Thêm Assert và điều tra lỗi HTTP/console trước khi đóng case",
                )
            )

        if status in _HEALED_STATUSES:
            conf = step.confidence_score
            if conf is None and step.audit is not None:
                conf = step.audit.confidence_score
            if conf is not None and HEAL_RUN_THRESHOLD <= conf < HEAL_PERSIST_THRESHOLD:
                prev = step.previous_selector
                healed = None
                if step.audit is not None:
                    prev = prev or step.audit.previous_selector or step.audit.old_locator
                    healed = step.audit.new_locator
                out.append(
                    RealityFinding(
                        code=AUDIT_CODE_HEAL_REVIEW,
                        message=(
                            f"{case_code} step {step.step_no}: Healed @ {conf:.2f} — "
                            "Locator cần duyệt & Sync lại"
                        ),
                        severity="warning",
                        case_code=case_code,
                        step_no=step.step_no,
                        confidence=conf,
                        previous_selector=prev,
                        healed_selector=healed,
                        remediation="Duyệt previous_selector → healed_selector rồi Sync YAML",
                    )
                )

        if status in _FAIL_STATUSES:
            fail_class = self._classify_failure(blob, step)
            out.append(
                RealityFinding(
                    code=fail_class,
                    message=(
                        f"{case_code} step {step.step_no}: {status} → {fail_class}"
                    ),
                    severity="warning",
                    case_code=case_code,
                    step_no=step.step_no,
                    failure_class=fail_class,
                    remediation=self._remediation_for(fail_class),
                )
            )
        return out

    @staticmethod
    def _step_signal_text(step: RunStepResult) -> str:
        parts = [step.message or ""]
        parts.extend(step.console_logs or [])
        if step.audit is not None:
            parts.append(step.audit.reason or "")
        return "\n".join(parts)

    @staticmethod
    def _has_runtime_noise(step: RunStepResult, blob: str) -> bool:
        if any(code >= 400 for code in step.network_status_codes):
            return True
        if _HTTP_RE.search(blob) or _CONSOLE_RE.search(blob):
            return True
        return any(_CONSOLE_RE.search(line) for line in step.console_logs)

    @staticmethod
    def _classify_failure(blob: str, step: RunStepResult) -> str:
        if _TIMEOUT_RE.search(blob):
            return AUDIT_FAIL_ENVIRONMENT_TIMEOUT
        if str(step.action) in ASSERT_STEP_ACTIONS or _ASSERT_RE.search(blob):
            return AUDIT_FAIL_ASSERTION_MISMATCH
        if _SELECTOR_RE.search(blob):
            return AUDIT_FAIL_SELECTOR_DRIFT
        if _HTTP_RE.search(blob) or _CONSOLE_RE.search(blob):
            return AUDIT_FAIL_APP_RUNTIME_ERROR
        return AUDIT_FAIL_APP_RUNTIME_ERROR

    @staticmethod
    def _remediation_for(fail_class: str) -> str:
        mapping = {
            AUDIT_FAIL_SELECTOR_DRIFT: "Re-sync locator / chạy heal rồi duyệt YAML",
            AUDIT_FAIL_ASSERTION_MISMATCH: "Đối chiếu expected result với UI thực tế",
            AUDIT_FAIL_APP_RUNTIME_ERROR: "Điều tra lỗi ứng dụng (HTTP/console)",
            AUDIT_FAIL_ENVIRONMENT_TIMEOUT: "Kiểm tra môi trường / tăng timeout / mạng",
        }
        return mapping.get(fail_class, "Xem lại bằng chứng bước thất bại")
