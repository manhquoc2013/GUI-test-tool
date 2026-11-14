"""SWARM_AUDIT — Evidence + TraceabilityAuditor + RealityChecker over a RunReport."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.reality_checker import RealityChecker, RealityFinding
from ai_qa_test_studio.constants import (
    APPLY_KIND_SWARM_AUDIT,
    AUDIT_CODE_FALSE_POSITIVE_PASS,
    AUDIT_CODE_HEAL_REVIEW,
    AUDIT_CODE_MISSING_EVIDENCE,
    BINDING_BOUND,
    BINDING_HEALED,
    BINDING_UNBOUND,
)
from ai_qa_test_studio.core.enums import IntentType, ThinkingLevel
from ai_qa_test_studio.core.models import Project, RunReport, TestCase
from ai_qa_test_studio.reporting.evidence import EvidenceCollector, EvidenceFlag


@dataclass(frozen=True)
class TraceabilityMetrics:
    requirement_total: int = 0
    requirement_covered: int = 0
    requirement_coverage_pct: float = 0.0
    case_total: int = 0
    pure_pass: int = 0
    healed: int = 0
    failed: int = 0
    blocked: int = 0
    pure_pass_pct: float = 0.0
    healed_pct: float = 0.0
    bound_ready: int = 0
    bound_ready_pct: float = 0.0
    gaps: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class SwarmAuditReport:
    intent: IntentType = IntentType.SWARM_AUDIT
    thinking: ThinkingLevel = ThinkingLevel.HIGH
    ok: bool = True
    findings: list[RealityFinding] = field(default_factory=list)
    evidence_dir: Path | None = None
    coverage_gaps: list[str] = field(default_factory=list)
    metrics: TraceabilityMetrics = field(default_factory=TraceabilityMetrics)
    evidence_flags: list[EvidenceFlag] = field(default_factory=list)


class TraceabilityAuditor:
    """Requirement / Scenario → TestCase → StepResult coverage matrix."""

    def audit(self, project: Project, report: RunReport) -> TraceabilityMetrics:
        reported = {c.case_id: c for c in report.cases}
        gaps: list[str] = []
        req_ids: set[str] = set()
        covered_reqs: set[str] = set()
        all_cases: list[TestCase] = []
        pure_pass = healed = failed = blocked = 0
        bound_ready = 0
        step_total = 0

        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                req = (scenario.requirement_ref or "").strip()
                if req:
                    req_ids.add(req)
                if not scenario.cases:
                    gaps.append(
                        f"Scenario {scenario.code} chưa có TestCase"
                    )
                    continue
                for case in scenario.cases:
                    all_cases.append(case)
                    case_req = (case.requirement_ref or req or "").strip()
                    if case_req:
                        req_ids.add(case_req)
                    run = reported.get(case.id)
                    if run is None:
                        if case.last_status not in {"SKIPPED"}:
                            gaps.append(
                                f"Scenario {scenario.code} / {case.code} chưa chạy"
                            )
                        continue
                    if case_req:
                        covered_reqs.add(case_req)
                    status = str(run.status)
                    if status == "PASS":
                        pure_pass += 1
                    elif status == "HEALED":
                        healed += 1
                    elif status == "FAIL":
                        failed += 1
                    elif status == "BLOCKED":
                        blocked += 1
                        gaps.append(
                            f"{case.code} đang Blocked"
                        )
                    for step in case.steps:
                        step_total += 1
                        binding = str(step.binding_status)
                        if binding in {BINDING_BOUND, BINDING_HEALED}:
                            bound_ready += 1
                        elif binding == BINDING_UNBOUND:
                            gaps.append(
                                f"{case.code} step {step.step_no} UNBOUND"
                            )

        # Requirements present on project but never referenced by a run
        for req in sorted(req_ids):
            if req not in covered_reqs:
                gaps.append(f"Requirement {req} chưa có TestCase chạy")

        case_total = len(all_cases) if all_cases else len(report.cases)
        if case_total == 0 and report.cases:
            case_total = len(report.cases)
            for cr in report.cases:
                if cr.status == "PASS":
                    pure_pass += 1
                elif cr.status == "HEALED":
                    healed += 1
                elif cr.status == "FAIL":
                    failed += 1
                elif cr.status == "BLOCKED":
                    blocked += 1

        req_total = len(req_ids) or (1 if covered_reqs else 0)
        req_covered = len(covered_reqs)
        # If project has no requirement refs, coverage = cases present in report / project cases
        if not req_ids and all_cases:
            req_total = len(all_cases)
            req_covered = sum(1 for c in all_cases if c.id in reported)
        coverage_pct = (100.0 * req_covered / req_total) if req_total else 0.0
        run_total = max(1, pure_pass + healed + failed + blocked)
        # Prefer report case count for pass rates when project tree empty
        if not all_cases and report.cases:
            run_total = len(report.cases)
        pure_pct = 100.0 * pure_pass / run_total if run_total else 0.0
        healed_pct = 100.0 * healed / run_total if run_total else 0.0
        if step_total == 0:
            for cr in report.cases:
                for run_step in cr.steps:
                    step_total += 1
                    # Run results do not carry binding; treat PASS/HEALED as ready signal
                    if str(run_step.status) in {"PASS", "HEALED"}:
                        bound_ready += 1
        bound_pct = 100.0 * bound_ready / step_total if step_total else 0.0

        return TraceabilityMetrics(
            requirement_total=req_total,
            requirement_covered=req_covered,
            requirement_coverage_pct=round(coverage_pct, 1),
            case_total=case_total or len(report.cases),
            pure_pass=pure_pass,
            healed=healed,
            failed=failed,
            blocked=blocked,
            pure_pass_pct=round(pure_pct, 1),
            healed_pct=round(healed_pct, 1),
            bound_ready=bound_ready,
            bound_ready_pct=round(bound_pct, 1),
            gaps=gaps[:50],
        )


class SwarmAuditor:
    def __init__(
        self,
        *,
        reality: RealityChecker | None = None,
        evidence: EvidenceCollector | None = None,
        traceability: TraceabilityAuditor | None = None,
    ) -> None:
        self.reality = reality or RealityChecker()
        self.evidence = evidence
        self.traceability = traceability or TraceabilityAuditor()

    def run(
        self,
        report: RunReport,
        project: Project | None = None,
        *,
        project_root: Path | None = None,
    ) -> SwarmAuditReport:
        reality = self.reality.audit_report(report)
        metrics = (
            self.traceability.audit(project, report)
            if project is not None
            else TraceabilityMetrics(
                case_total=len(report.cases),
                pure_pass=sum(1 for c in report.cases if c.status == "PASS"),
                healed=sum(1 for c in report.cases if c.status == "HEALED"),
                failed=sum(1 for c in report.cases if c.status == "FAIL"),
                blocked=sum(1 for c in report.cases if c.status == "BLOCKED"),
            )
        )
        # Fill percentages when project was None
        if project is None and metrics.case_total:
            total = metrics.case_total
            metrics = TraceabilityMetrics(
                case_total=total,
                pure_pass=metrics.pure_pass,
                healed=metrics.healed,
                failed=metrics.failed,
                blocked=metrics.blocked,
                pure_pass_pct=round(100.0 * metrics.pure_pass / total, 1),
                healed_pct=round(100.0 * metrics.healed / total, 1),
                requirement_coverage_pct=100.0 if report.cases else 0.0,
                requirement_total=1 if report.cases else 0,
                requirement_covered=1 if report.cases else 0,
            )

        evidence_dir: Path | None = None
        evidence_flags: list[EvidenceFlag] = []
        root = project_root
        if root is None and self.evidence is not None:
            root = self.evidence.project_root
        if root is not None:
            collector = self.evidence or EvidenceCollector(root)
            evidence_dir = collector.collect(report)
            evidence_flags = list(collector.verify_integrity(report).flags)

        findings = list(reality.findings)
        for gap in metrics.gaps:
            findings.append(
                RealityFinding(code="traceability_gap", message=gap, severity="warning")
            )
        for flag in evidence_flags:
            findings.append(
                RealityFinding(
                    code=flag.code or AUDIT_CODE_MISSING_EVIDENCE,
                    message=flag.message,
                    severity=flag.severity,
                    case_code=flag.case_code,
                    step_no=flag.step_no,
                )
            )
        ok = reality.ok and not any(f.severity == "error" for f in findings)
        return SwarmAuditReport(
            ok=ok,
            findings=findings,
            evidence_dir=evidence_dir,
            coverage_gaps=list(metrics.gaps),
            metrics=metrics,
            evidence_flags=evidence_flags,
        )


def swarm_audit_context(audit: SwarmAuditReport) -> dict[str, Any]:
    return {
        "intent": audit.intent.value,
        "ok": audit.ok,
        "findings": [
            {
                "code": f.code,
                "message": f.message,
                "severity": f.severity,
                "case_code": f.case_code,
                "step_no": f.step_no,
                "confidence": f.confidence,
                "remediation": f.remediation,
                "previous_selector": f.previous_selector,
                "healed_selector": f.healed_selector,
                "failure_class": f.failure_class,
            }
            for f in audit.findings
        ],
        "coverage_gaps": audit.coverage_gaps,
        "evidence_dir": str(audit.evidence_dir) if audit.evidence_dir else "",
        "metrics": {
            "case_total": audit.metrics.case_total,
            "pure_pass": audit.metrics.pure_pass,
            "healed": audit.metrics.healed,
            "failed": audit.metrics.failed,
            "blocked": audit.metrics.blocked,
            "requirement_coverage_pct": audit.metrics.requirement_coverage_pct,
            "pure_pass_pct": audit.metrics.pure_pass_pct,
            "healed_pct": audit.metrics.healed_pct,
            "bound_ready_pct": audit.metrics.bound_ready_pct,
        },
    }


def format_swarm_audit_markdown(audit: SwarmAuditReport) -> str:
    """User-facing Markdown card — never dump raw JSON."""
    m = audit.metrics
    lines = [
        "## Báo cáo kiểm toán (SWARM_AUDIT)",
        "",
        "### 1. Chỉ số tổng quan",
        "",
        "| Chỉ số | Giá trị |",
        "| --- | ---: |",
        f"| Tổng Case | {m.case_total} |",
        f"| Pass (thuần) | {m.pure_pass} ({m.pure_pass_pct}%) |",
        f"| Healed | {m.healed} ({m.healed_pct}%) |",
        f"| Failed | {m.failed} |",
        f"| Blocked | {m.blocked} |",
        f"| Độ phủ yêu cầu | {m.requirement_coverage_pct}% |",
        f"| BOUND sẵn sàng | {m.bound_ready_pct}% |",
        "",
        "### 2. Lỗi & cảnh báo (RealityChecker)",
        "",
    ]
    warnings = [
        f
        for f in audit.findings
        if f.code
        in {
            AUDIT_CODE_FALSE_POSITIVE_PASS,
            AUDIT_CODE_HEAL_REVIEW,
            AUDIT_CODE_MISSING_EVIDENCE,
        }
        or f.failure_class
        or f.code.startswith("SELECTOR")
        or f.code.startswith("ASSERT")
        or f.code.startswith("APP_")
        or f.code.startswith("ENVIRONMENT")
        or f.code
        in {
            "SELECTOR_DRIFT",
            "ASSERTION_MISMATCH",
            "APP_RUNTIME_ERROR",
            "ENVIRONMENT_TIMEOUT",
            "failed_cases",
            "heal_not_persisted",
        }
    ]
    if not warnings:
        warnings = [f for f in audit.findings if f.severity != "info"][:12]
    if not warnings:
        lines.append("_Không có cảnh báo RealityChecker._")
    else:
        lines.extend(
            [
                "| Mã / Case | Bước | Confidence | Chi tiết |",
                "| --- | ---: | ---: | --- |",
            ]
        )
        for finding in warnings[:20]:
            case = finding.case_code or "—"
            step = str(finding.step_no) if finding.step_no is not None else "—"
            conf = f"{finding.confidence:.2f}" if finding.confidence is not None else "—"
            detail = finding.message
            if finding.previous_selector or finding.healed_selector:
                detail += (
                    f" (`{finding.previous_selector or '?'}` → "
                    f"`{finding.healed_selector or '?'}`)"
                )
            if finding.remediation:
                detail += f" · _{finding.remediation}_"
            lines.append(f"| `{finding.code}` / {case} | {step} | {conf} | {detail} |")

    lines.extend(["", "### 3. Khuyến nghị hành động tiếp theo", ""])
    actions: list[str] = []
    if any(f.code == AUDIT_CODE_HEAL_REVIEW for f in audit.findings):
        actions.append("Re-sync locator yếu (0.65 ≤ confidence < 0.85) vào YAML.")
    if any(f.code == AUDIT_CODE_FALSE_POSITIVE_PASS for f in audit.findings):
        actions.append("Bổ sung bước Assert cho các case Pass dương tính giả.")
    if any(f.code == "traceability_gap" for f in audit.findings) or audit.coverage_gaps:
        actions.append("Viết thêm TestCase cho Requirement / Scenario còn trống hoặc Blocked.")
    if any(f.code == AUDIT_CODE_MISSING_EVIDENCE for f in audit.findings):
        actions.append("Thu thập lại screenshot/audit cho bước Failed/Healed thiếu bằng chứng.")
    if not actions:
        actions.append("Không có hành động bắt buộc — giữ Evidence RO và theo dõi độ phủ.")
    for idx, action in enumerate(actions, start=1):
        lines.append(f"{idx}. {action}")
    if audit.evidence_dir is not None:
        lines.extend(["", f"_Evidence:_ `{audit.evidence_dir}`"])
    return "\n".join(lines).strip()


def swarm_audit_structured_payload(audit: SwarmAuditReport) -> dict[str, Any]:
    ctx = swarm_audit_context(audit)
    return {
        "kind": APPLY_KIND_SWARM_AUDIT,
        "ok": audit.ok,
        "markdown": format_swarm_audit_markdown(audit),
        "metrics": ctx["metrics"],
        "findings": ctx["findings"],
        "coverage_gaps": ctx["coverage_gaps"],
        "evidence_dir": ctx["evidence_dir"],
        "thinking": audit.thinking.value,
        "intent": audit.intent.value,
    }
