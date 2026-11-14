"""Evidence panel heal-diff / lazy shot + ReportService masked Excel/HTML."""

from __future__ import annotations

import asyncio
import struct
import zlib
from pathlib import Path

import pytest
from openpyxl import load_workbook
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    SECRET_MASK_PLACEHOLDER,
    UI_EVIDENCE_ACCEPT_HEAL,
    UI_EVIDENCE_HEAL_PERSISTED,
    UI_EVIDENCE_HEAL_TEMP,
    UI_EVIDENCE_SELECTOR_HEALED,
    UI_EVIDENCE_SELECTOR_PREV,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    Project,
    RunReport,
    RunStepResult,
    StepAuditEntry,
    TestCase,
    TestGroup,
    TestScenario,
    TestStep,
)
from ai_qa_test_studio.reporting.report_service import ReportService
from ai_qa_test_studio.ui.widgets.evidence_panel import EvidencePanel


def _qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def _minimal_png(path: Path, *, width: int = 8, height: int = 8) -> Path:
    """Write a tiny valid RGB PNG (no external deps)."""

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    raw = b"".join(b"\x00" + (b"\x00\x80\xff" * width) for _ in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    path.write_bytes(png)
    return path


def _healed_report(
    *,
    confidence: float,
    persisted: bool,
    screenshot: Path | None = None,
) -> RunReport:
    audit = StepAuditEntry(
        step_no=1,
        old_locator="#old-login",
        new_locator="[data-testid=login]",
        reason="stale ref",
        confidence_score=confidence,
        previous_selector="#old-login",
        healed_selector="[data-testid=login]",
        persisted=persisted,
    )
    step = RunStepResult(
        step_no=1,
        action="click",
        status="HEALED",
        step_status=StepStatus.HEALED,
        duration_ms=120,
        message="password=SuperSecret99 healed",
        console_logs=["api_key=sk-live-abc123"],
        screenshot_path=str(screenshot) if screenshot else None,
        audit=audit,
        previous_selector="#old-login",
        confidence_score=confidence,
    )
    return RunReport(
        run_id="run-evidence",
        project_name="demo",
        started_at="t0",
        finished_at="t1",
        cases=[
            CaseRunResult(
                case_id="c1",
                case_code="TC1",
                title="Login",
                status="HEALED",
                steps=[step],
            )
        ],
        healing_audit=[audit],
    )


def test_evidence_panel_healed_temp_vs_persisted(qapp: QApplication) -> None:
    _ = qapp
    panel_temp = EvidencePanel()
    panel_temp.show()
    panel_temp.set_report(_healed_report(confidence=0.72, persisted=False))
    qapp.processEvents()
    assert panel_temp.step_list.count() == 1
    assert not panel_temp.heal_card.isHidden()
    assert "0.72" in panel_temp.heal_badge.text()
    assert UI_EVIDENCE_SELECTOR_PREV in panel_temp.heal_prev.text()
    assert "#old-login" in panel_temp.heal_prev.text()
    assert UI_EVIDENCE_SELECTOR_HEALED in panel_temp.heal_new.text()
    assert "[data-testid=login]" in panel_temp.heal_new.text()
    assert UI_EVIDENCE_HEAL_TEMP in panel_temp.heal_note.text()
    assert not panel_temp.btn_accept_heal.isHidden()
    assert panel_temp.btn_accept_heal.text() == UI_EVIDENCE_ACCEPT_HEAL
    assert "120" in panel_temp.meta_duration.text()

    panel_hi = EvidencePanel()
    panel_hi.show()
    panel_hi.set_report(_healed_report(confidence=0.90, persisted=True))
    qapp.processEvents()
    assert not panel_hi.heal_card.isHidden()
    assert "0.90" in panel_hi.heal_badge.text()
    assert UI_EVIDENCE_HEAL_PERSISTED in panel_hi.heal_note.text()
    assert panel_hi.btn_accept_heal.isHidden()
    panel_temp.deleteLater()
    panel_hi.deleteLater()


@pytest.mark.asyncio
async def test_evidence_panel_lazy_shot_and_destroy_safe(
    qapp: QApplication, tmp_path: Path
) -> None:
    _ = qapp
    png = _minimal_png(tmp_path / "step-1.png")
    panel = EvidencePanel()
    panel.set_report(
        _healed_report(confidence=0.72, persisted=False, screenshot=png)
    )
    # Allow async lazy-load task to finish.
    for _ in range(40):
        qapp.processEvents()
        await asyncio.sleep(0.05)
        if panel._pixmap is not None and not panel._pixmap.isNull():
            break
    assert panel._pixmap is not None and not panel._pixmap.isNull()

    doomed = EvidencePanel()
    doomed.set_report(
        _healed_report(confidence=0.72, persisted=False, screenshot=png)
    )
    doomed.deleteLater()
    qapp.processEvents()
    await asyncio.sleep(0.15)
    qapp.processEvents()
    # Destroyed mid-load must not raise RuntimeError / shiboken errors.


@pytest.mark.asyncio
async def test_report_service_masks_secrets_and_preconditions(
    tmp_path: Path,
) -> None:
    project = Project(
        id="p1",
        name="demo",
        root_group=TestGroup(
            id="g1",
            name="root",
            scenarios=[
                TestScenario(
                    id="s1",
                    code="TS_01",
                    title="Scenario",
                    cases=[
                        TestCase(
                            id="c1",
                            code="TC_01",
                            title="Case",
                            preconditions="Trang pricing tải thành công",  # type: ignore[arg-type]
                            steps=[
                                TestStep(
                                    step_no=1,
                                    action="fill",
                                    intent_hint="Ô mật khẩu",
                                    input_data="password=SuperSecret99",
                                    expected_result="Đăng nhập OK",
                                )
                            ],
                        )
                    ],
                )
            ],
        ),
    )
    png = _minimal_png(tmp_path / "shot.png")
    report = _healed_report(confidence=0.72, persisted=False, screenshot=png)
    report.cases[0].steps[0].message = "password=SuperSecret99"
    report.cases[0].steps[0].console_logs = ["api_key=sk-live-abc123"]

    service = ReportService(tmp_path)
    xlsx = tmp_path / "out.xlsx"
    html = tmp_path / "out.html"
    await service.export_excel(project, report, xlsx)
    await service.export_html(report, html)

    assert xlsx.is_file() and html.is_file()
    wb = load_workbook(xlsx)
    assert "Tổng quan tỷ lệ" in wb.sheetnames
    assert "Chi tiết Healing Audit" in wb.sheetnames
    cases = wb["Test Cases (IEEE 829)"]
    headers = [cases.cell(1, c).value for c in range(1, cases.max_column + 1)]
    assert "Điều kiện tiên quyết" in headers
    assert "Action" in headers
    assert "Target" in headers
    assert "Expected Result" in headers
    pre_col = headers.index("Điều kiện tiên quyết") + 1
    pre = str(cases.cell(2, pre_col).value or "")
    assert pre == "Trang pricing tải thành công"
    assert "; " not in pre
    assert not pre.startswith("T; r")

    blob = "\n".join(
        str(cases.cell(r, c).value or "")
        for r in range(1, cases.max_row + 1)
        for c in range(1, cases.max_column + 1)
    )
    heal_sheet = wb["Chi tiết Healing Audit"]
    heal_blob = "\n".join(
        str(heal_sheet.cell(r, c).value or "")
        for r in range(1, heal_sheet.max_row + 1)
        for c in range(1, heal_sheet.max_column + 1)
    )
    assert "SuperSecret99" not in blob
    assert "SuperSecret99" not in heal_blob
    assert "sk-live-abc123" not in heal_blob
    assert SECRET_MASK_PLACEHOLDER in heal_blob or SECRET_MASK_PLACEHOLDER in blob

    html_text = html.read_text(encoding="utf-8")
    assert "SuperSecret99" not in html_text
    assert "sk-live-abc123" not in html_text
    assert 'data-filter="all"' in html_text
    assert 'data-filter="healed"' in html_text
    assert "Previous Selector" in html_text
    assert "Healed Selector" in html_text
    assert "openLightbox" in html_text
