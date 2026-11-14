"""HTML / Excel / pytest export actions — off-thread IO + StateToolTip feedback."""

from __future__ import annotations

import asyncio
from pathlib import Path
from typing import TYPE_CHECKING

from PySide6.QtCore import Qt
from qfluentwidgets import InfoBar, InfoBarPosition, StateToolTip

from ai_qa_test_studio.constants import (
    UI_EXPORT_PROGRESS_BODY,
    UI_EXPORT_PROGRESS_TITLE,
    UI_INFOBAR_DURATION_MS,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class ExportController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window
        self._tooltip: StateToolTip | None = None

    def export(self, kind: str) -> None:
        w = self._w
        if w.project is None:
            return
        if kind == "html" and w._last_report is None:
            w._toast_info("Export", "Chạy test trước để có báo cáo HTML.")
            return
        get_async_task_supervisor().register(
            self._export_async(kind),
            key=f"export_{kind}",
        )

    async def _export_async(self, kind: str) -> None:
        w = self._w
        if w.project is None:
            return
        exports = w.data_root / "exports"
        self._show_progress(True)
        try:
            path = await asyncio.to_thread(self._write_export, kind, exports)
            w._append_log(f"Đã xuất: {path}")
            InfoBar.success(
                title="Export",
                content=f"Đã xuất:\n{path}",
                orient=Qt.Orientation.Horizontal,
                isClosable=True,
                position=InfoBarPosition.TOP,
                duration=UI_INFOBAR_DURATION_MS,
                parent=w,
            )
        except Exception as exc:  # noqa: BLE001
            InfoBar.error(
                title="Export",
                content=str(exc),
                orient=Qt.Orientation.Horizontal,
                isClosable=True,
                position=InfoBarPosition.TOP,
                duration=UI_INFOBAR_DURATION_MS,
                parent=w,
            )
        finally:
            self._show_progress(False)

    def _write_export(self, kind: str, exports: Path) -> Path:
        w = self._w
        assert w.project is not None
        exports.mkdir(parents=True, exist_ok=True)
        if kind == "html":
            assert w._last_report is not None
            path = exports / f"{w._last_report.run_id}.html"
            HtmlReporter().write(w._last_report, path)
            return path
        if kind == "excel":
            path = exports / f"{w.project.name.replace(' ', '_')}_ieee829.xlsx"
            ExcelExporter().write(w.project, w._last_report, path)
            return path
        path = exports / "pytest_suite"
        CodeExporter().export(w.project, path)
        return path

    def _show_progress(self, visible: bool) -> None:
        w = self._w
        if visible:
            tip = StateToolTip(UI_EXPORT_PROGRESS_TITLE, UI_EXPORT_PROGRESS_BODY, w)
            tip.move(tip.getSuitablePos())
            tip.show()
            self._tooltip = tip
            return
        tip = self._tooltip
        self._tooltip = None
        if tip is None:
            return
        tip.setContent("Hoàn tất")
        tip.setState(True)
