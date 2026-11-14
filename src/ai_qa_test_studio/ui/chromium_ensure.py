"""UI gate: ensure Chromium before browser engine launch (lazy download dialog)."""

from __future__ import annotations

from typing import TYPE_CHECKING

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QMessageBox, QProgressDialog
from qfluentwidgets import InfoBar, InfoBarPosition

from ai_qa_test_studio.browser.chromium_bootstrap import (
    chromium_ready,
    ensure_chromium,
    request_cancel_install,
)
from ai_qa_test_studio.constants import (
    UI_CHROMIUM_CANCELLED,
    UI_CHROMIUM_DOWNLOAD_BODY,
    UI_CHROMIUM_DOWNLOAD_TITLE,
    UI_CHROMIUM_FAILED,
)

if TYPE_CHECKING:
    from PySide6.QtWidgets import QWidget


def _make_dialog(parent: QWidget | None) -> QProgressDialog:
    dialog = QProgressDialog(UI_CHROMIUM_DOWNLOAD_BODY, "Hủy", 0, 100, parent)
    dialog.setWindowTitle(UI_CHROMIUM_DOWNLOAD_TITLE)
    dialog.setWindowModality(Qt.WindowModality.ApplicationModal)
    dialog.setMinimumDuration(0)
    dialog.setValue(0)
    dialog.setAutoClose(False)
    dialog.setAutoReset(False)
    return dialog


async def ensure_chromium_with_dialog(parent: QWidget | None) -> bool:
    """Return True when Chromium is ready; False if user cancelled or gave up."""
    if chromium_ready():
        return True

    while True:
        dialog = _make_dialog(parent)
        cancelled = False

        def _on_cancel() -> None:
            nonlocal cancelled
            cancelled = True
            request_cancel_install()

        dialog.canceled.connect(_on_cancel)
        dialog.show()
        progress_target = dialog

        def _progress(percent: int | None, _line: str, dlg: QProgressDialog = progress_target) -> None:
            if percent is not None:
                dlg.setValue(percent)

        try:
            await ensure_chromium(on_progress=_progress)
            dialog.setValue(100)
            return True
        except InterruptedError:
            if parent is not None:
                InfoBar.warning(
                    UI_CHROMIUM_CANCELLED,
                    "",
                    parent=parent,
                    position=InfoBarPosition.TOP,
                    duration=3000,
                )
            return False
        except Exception as exc:
            if cancelled:
                if parent is not None:
                    InfoBar.warning(
                        UI_CHROMIUM_CANCELLED,
                        str(exc),
                        parent=parent,
                        position=InfoBarPosition.TOP,
                        duration=4000,
                    )
                return False
            choice = QMessageBox.question(
                parent,
                UI_CHROMIUM_FAILED,
                f"{exc}\n\nThử lại?",
                QMessageBox.StandardButton.Retry | QMessageBox.StandardButton.Cancel,
            )
            if choice != QMessageBox.StandardButton.Retry:
                return False
        finally:
            dialog.close()
