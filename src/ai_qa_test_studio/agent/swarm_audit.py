"""SWARM_AUDIT — Evidence + Traceability + RealityChecker over a RunReport."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.reality_checker import RealityChecker, RealityFinding
from ai_qa_test_studio.core.enums import IntentType, ThinkingLevel
from ai_qa_test_studio.core.models import Project, RunReport
from ai_qa_test_studio.reporting.evidence import EvidenceCollector


@dataclass(frozen=True)
class SwarmAuditReport:
    intent: IntentType = IntentType.SWARM_AUDIT
    thinking: ThinkingLevel = ThinkingLevel.HIGH
    ok: bool = True
    findings: list[RealityFinding] = field(default_factory=list)
    evidence_dir: Path | None = None
    coverage_gaps: list[str] = field(default_factory=list)


class SwarmAuditor:
    def __init__(
        self,
        *,
        reality: RealityChecker | None = None,
        evidence: EvidenceCollector | None = None,
    ) -> None:
        self.reality = reality or RealityChecker()
        self.evidence = evidence

    def run(
        self,
        report: RunReport,
        project: Project | None = None,
        *,
        project_root: Path | None = None,
    ) -> SwarmAuditReport:
        reality = self.reality.audit_report(report)
        gaps: list[str] = []
        if project is not None:
            gaps = _traceability_gaps(project, report)
        evidence_dir: Path | None = None
        root = project_root
        if root is None and self.evidence is not None:
            root = self.evidence.project_root
        if root is not None:
            collector = self.evidence or EvidenceCollector(root)
            evidence_dir = collector.collect(report)
        findings = list(reality.findings)
        for gap in gaps:
            findings.append(
                RealityFinding(code="traceability_gap", message=gap, severity="warning")
            )
        ok = reality.ok and not any(f.severity == "error" for f in findings)
        return SwarmAuditReport(
            ok=ok,
            findings=findings,
            evidence_dir=evidence_dir,
            coverage_gaps=gaps,
        )


def _traceability_gaps(project: Project, report: RunReport) -> list[str]:
    reported = {c.case_id for c in report.cases}
    gaps: list[str] = []
    for group in [project.root_group, *project.root_group.children]:
        for scenario in group.scenarios:
            for case in scenario.cases:
                if case.id not in reported and case.last_status not in {"SKIPPED", "NOT_RUN"}:
                    gaps.append(f"case {case.code} missing from run report")
                if not (case.requirement_ref or scenario.requirement_ref):
                    gaps.append(f"case {case.code} missing requirement_ref")
    return gaps[:50]


def swarm_audit_context(audit: SwarmAuditReport) -> dict[str, Any]:
    return {
        "intent": audit.intent.value,
        "ok": audit.ok,
        "findings": [f.message for f in audit.findings],
        "coverage_gaps": audit.coverage_gaps,
        "evidence_dir": str(audit.evidence_dir) if audit.evidence_dir else "",
    }
