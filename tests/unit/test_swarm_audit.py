from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.agent.swarm_audit import SwarmAuditor
from ai_qa_test_studio.core.enums import IntentType
from ai_qa_test_studio.core.models import CaseRunResult, Project, RunReport, TestGroup


def test_swarm_audit_collects_evidence(tmp_path: Path) -> None:
    project = Project(id="p1", name="demo", root_group=TestGroup(id="g", name="Root"))
    report = RunReport(
        run_id="swarm-1",
        project_name="demo",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c1",
                case_code="TC1",
                title="T",
                status="PASS",
                steps=[],
            )
        ],
    )
    audit = SwarmAuditor().run(report, project, project_root=tmp_path)
    assert audit.intent is IntentType.SWARM_AUDIT
    assert audit.ok is True
    assert audit.evidence_dir is not None
    assert audit.evidence_dir.exists()
