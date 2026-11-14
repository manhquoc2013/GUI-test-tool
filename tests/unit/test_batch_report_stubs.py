"""CANCELLED stubs and empty batch guards for aggregate reports."""

from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.constants import BATCH_CANCELLED_MESSAGE, CASE_STATUS_CANCELLED
from ai_qa_test_studio.core.models import CaseRunResult, RunReport, TestCase
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter
from ai_qa_test_studio.ui.controllers.run_controller import RunController


def test_cancelled_stub_has_exporter_safe_fields(tmp_path: Path) -> None:
    case = TestCase(id="c1", code="TC1", title="One")
    stub = RunController._cancelled_stub(case)
    assert stub.status == CASE_STATUS_CANCELLED
    assert stub.steps == []
    assert stub.skip_reason == BATCH_CANCELLED_MESSAGE
    report = RunReport(
        run_id="r1",
        project_name="P",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c0",
                case_code="TC0",
                title="Done",
                status="PASS",
                steps=[],
            ),
            stub,
        ],
    )
    html_path = tmp_path / "batch.html"
    HtmlReporter().write(report, html_path)
    text = html_path.read_text(encoding="utf-8")
    assert "CANCELLED" in text
    assert BATCH_CANCELLED_MESSAGE in text
    assert "PASS:" in text or "PASS" in text


def test_extend_preserves_multiple_case_results() -> None:
    collected: list[CaseRunResult] = []
    sub = [
        CaseRunResult(case_id="c1", case_code="TC1", title="A", status="PASS", steps=[]),
        CaseRunResult(case_id="c1", case_code="TC1", title="A row2", status="FAIL", steps=[]),
    ]
    collected.extend(sub)
    assert len(collected) == 2
