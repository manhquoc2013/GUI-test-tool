"""RealityChecker — Sprint 4 APPLY gate (overrides bare validate_case_schema)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.validate_case_schema import validate_case_schema
from ai_qa_test_studio.core.models import RunReport, TestCase


@dataclass(frozen=True)
class RealityFinding:
    code: str
    message: str
    severity: str = "error"


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
        fail_count = sum(1 for c in report.cases if c.status == "FAIL")
        if fail_count:
            findings.append(
                RealityFinding(
                    code="failed_cases",
                    message=f"{fail_count} case(s) FAIL",
                    severity="warning",
                )
            )
        for audit in report.healing_audit:
            if audit.confidence_score is not None and not audit.persisted:
                findings.append(
                    RealityFinding(
                        code="heal_not_persisted",
                        message=(
                            f"step {audit.step_no} healed at "
                            f"{audit.confidence_score:.2f} without YAML persist"
                        ),
                        severity="warning",
                    )
                )
        errors = [f for f in findings if f.severity == "error"]
        return RealityCheckResult(ok=not errors, findings=findings)
