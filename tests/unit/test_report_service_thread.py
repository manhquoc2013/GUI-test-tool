from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.core.models import (
    CaseRunResult,
    Project,
    RunReport,
    TestGroup,
)
from ai_qa_test_studio.reporting.report_service import ReportService


@pytest.mark.asyncio
async def test_export_all_runs_off_thread(tmp_path: Path) -> None:
    project = Project(id="p1", name="demo", root_group=TestGroup(id="g1", name="Root"))
    report = RunReport(
        run_id="run-export",
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
    service = ReportService(tmp_path)
    bundle = await service.export_all(project, report)
    assert bundle.evidence_dir.exists()
    assert bundle.html_path is not None and bundle.html_path.is_file()
    assert bundle.excel_path is not None and bundle.excel_path.is_file()
