"""HTML / Excel / pytest export actions."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtWidgets import QMessageBox

from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class ExportController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window

    def export(self, kind: str) -> None:
        w = self._w
        if w.project is None:
            return
        exports = w.data_root / "exports"
        exports.mkdir(parents=True, exist_ok=True)
        try:
            if kind == "html":
                if w._last_report is None:
                    QMessageBox.information(w, "Export", "Chạy test trước để có báo cáo HTML.")
                    return
                path = exports / f"{w._last_report.run_id}.html"
                HtmlReporter().write(w._last_report, path)
            elif kind == "excel":
                path = exports / f"{w.project.name.replace(' ', '_')}_ieee829.xlsx"
                ExcelExporter().write(w.project, w._last_report, path)
            else:
                path = exports / "pytest_suite"
                CodeExporter().export(w.project, path)
            w._append_log(f"Đã xuất: {path}")
            QMessageBox.information(w, "Export", f"Đã xuất:\n{path}")
        except Exception as exc:  # noqa: BLE001
            QMessageBox.warning(w, "Export", str(exc))
