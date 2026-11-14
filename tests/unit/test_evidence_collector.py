from __future__ import annotations

import base64
from pathlib import Path

from ai_qa_test_studio.constants import SECRET_MASK_PLACEHOLDER
from ai_qa_test_studio.core.models import CaseRunResult, RunReport, RunStepResult
from ai_qa_test_studio.reporting.evidence import EvidenceCollector


def test_evidence_masks_secrets_and_writes_paths(tmp_path: Path) -> None:
    png = base64.b64encode(b"\x89PNG\r\n\x1a\nfake").decode("ascii")
    report = RunReport(
        run_id="run-1",
        project_name="demo",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c1",
                case_code="TC1",
                title="Login",
                status="PASS",
                steps=[
                    RunStepResult(
                        step_no=1,
                        action="fill",
                        status="PASS",
                        message="password=SuperSecret99",
                        screenshot_b64=png,
                    )
                ],
            )
        ],
    )
    collector = EvidenceCollector(tmp_path)
    out = collector.collect(report)
    assert out.exists()
    manifest = (out / "manifest.json").read_text(encoding="utf-8")
    assert "SuperSecret99" not in manifest
    assert SECRET_MASK_PLACEHOLDER in manifest
    assert report.cases[0].steps[0].screenshot_path
    assert Path(report.cases[0].steps[0].screenshot_path or "").is_file()
