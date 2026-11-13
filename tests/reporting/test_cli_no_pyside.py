from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from ai_qa_test_studio.core.models import (
    CaseRunResult,
    Project,
    RunReport,
    RunStepResult,
    TestGroup,
)
from ai_qa_test_studio.core.storage import ProjectStorage


def test_reporting_cli_does_not_import_pyside(tmp_path: Path) -> None:
    storage = ProjectStorage(tmp_path)
    project = Project(
        id="p1",
        name="Demo",
        root_group=TestGroup(id="g1", name="Root"),
    )
    storage.save(project)
    report = RunReport(
        run_id="r1",
        project_name="Demo",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c1",
                case_code="TC1",
                title="t",
                status="PASS",
                steps=[
                    RunStepResult(step_no=1, action="goto", status="PASS", message="ok"),
                ],
            )
        ],
    )
    report_path = tmp_path / "report.json"
    report_path.write_text(report.model_dump_json(), encoding="utf-8")
    html_path = tmp_path / "out.html"
    excel_path = tmp_path / "out.xlsx"

    code = f"""
import sys
from ai_qa_test_studio.reporting.cli import main
rc = main([
    '--project', {str(tmp_path)!r},
    '--report', {str(report_path)!r},
    '--html', {str(html_path)!r},
    '--excel', {str(excel_path)!r},
])
assert rc == 0
assert 'PySide6' not in sys.modules
print('OK')
"""
    proc = subprocess.run(
        [sys.executable, "-c", code],
        check=False,
        capture_output=True,
        text=True,
        cwd=str(Path.cwd()),
        env={**dict(**__import__("os").environ), "PYTHONPATH": "src"},
    )
    assert proc.returncode == 0, proc.stderr
    assert "OK" in proc.stdout
    assert html_path.is_file()
    assert excel_path.is_file()
