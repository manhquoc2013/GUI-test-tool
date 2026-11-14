"""HTML / Excel / pytest export actions — off-thread IO + StateToolTip feedback."""

from __future__ import annotations

import asyncio
import subprocess
import sys
from pathlib import Path
from typing import TYPE_CHECKING

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QWidget
from qfluentwidgets import InfoBar, InfoBarPosition, PushButton, StateToolTip

from ai_qa_test_studio.constants import (
    REPORT_EXPORT_TASK_KEY,
    UI_EXPORT_PROGRESS_BODY,
    UI_EXPORT_PROGRESS_TITLE,
    UI_INFOBAR_DURATION_MS,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.report_service import ReportService

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class _ExportDoneBridge(QWidget):
    """Host widget so emit_if_alive can deliver export completion to the UI thread."""

    finished = Signal(object, object)  # path: Path | None, error: str | None
    progress = Signal(bool)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.hide()


class ExportController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window
        self._tooltip: StateToolTip | None = None
        self._bridge = _ExportDoneBridge(window)
        self._bridge.finished.connect(self._on_export_finished)
        self._bridge.progress.connect(self._show_progress)

    def export(self, kind: str) -> None:
        w = self._w
        if w.project is None:
            return
        if kind == "html" and w._last_report is None:
            w._toast_info("Export", "Chạy test trước để có báo cáo HTML.")
            return
        get_async_task_supervisor().register(
            self._export_async(kind),
            key=REPORT_EXPORT_TASK_KEY,
            replace=True,
        )

    async def _export_async(self, kind: str) -> None:
        w = self._w
        if w.project is None:
            return
        exports = w.data_root / "exports"
        supervisor = get_async_task_supervisor()
        supervisor.emit_if_alive(self._bridge, self._bridge.progress, True)
        path: Path | None = None
        error: str | None = None
        try:
            path = await self._run_export(kind, exports)
        except Exception as exc:  # noqa: BLE001
            error = str(exc)
        finally:
            supervisor.emit_if_alive(self._bridge, self._bridge.progress, False)
            supervisor.emit_if_alive(self._bridge, self._bridge.finished, path, error)

    async def _run_export(self, kind: str, exports: Path) -> Path:
        w = self._w
        assert w.project is not None
        exports.mkdir(parents=True, exist_ok=True)
        service = ReportService(w.storage.root)
        if kind == "html":
            assert w._last_report is not None
            path = exports / f"{w._last_report.run_id}.html"
            return await service.export_html(w._last_report, path)
        if kind == "excel":
            path = exports / f"{w.project.name.replace(' ', '_')}_ieee829.xlsx"
            return await service.export_excel(w.project, w._last_report, path)
        path = exports / "pytest_suite"
        await asyncio.to_thread(CodeExporter().export, w.project, path)
        return path

    def _on_export_finished(self, path: object, error: object) -> None:
        w = self._w
        if error:
            InfoBar.error(
                title="Export",
                content=str(error),
                orient=Qt.Orientation.Horizontal,
                isClosable=True,
                position=InfoBarPosition.TOP,
                duration=UI_INFOBAR_DURATION_MS,
                parent=w,
            )
            return
        if not isinstance(path, Path):
            return
        w._append_log(f"Đã xuất: {path}")
        bar = InfoBar.success(
            title="Export",
            content=f"Đã xuất:\n{path}",
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.TOP,
            duration=UI_INFOBAR_DURATION_MS,
            parent=w,
        )
        open_file = PushButton("Mở file", bar)
        open_dir = PushButton("Mở thư mục", bar)
        open_file.clicked.connect(lambda: self._reveal(path, folder=False))
        open_dir.clicked.connect(lambda: self._reveal(path, folder=True))
        bar.addWidget(open_file)
        bar.addWidget(open_dir)

    def _reveal(self, path: Path, *, folder: bool) -> None:
        target = path.parent if folder else path
        if sys.platform == "darwin":
            subprocess.Popen(["open", str(target)])  # noqa: S603
        elif sys.platform.startswith("win"):
            subprocess.Popen(["explorer", str(target)])  # noqa: S603
        else:
            subprocess.Popen(["xdg-open", str(target)])  # noqa: S603

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
