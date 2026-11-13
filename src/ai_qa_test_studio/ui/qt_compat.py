"""Qt/macOS compatibility — version shim + Cocoa accessibility noise guards."""

from __future__ import annotations

import os
import sys
from typing import TextIO

_INVALID_AX_MARKERS = (
    "Called accessibilityLabel on invalid object",
    "Called attribute on invalid object",
)


class _LineFilteringStream:
    """Drop known Cocoa/Qt AX false-positive lines (often NSLog, not qWarning)."""

    __slots__ = ("_real", "_buf")

    def __init__(self, real: TextIO) -> None:
        self._real = real
        self._buf = ""

    def write(self, data: str) -> int:
        if not isinstance(data, str):
            data = str(data)
        if not data:
            return 0
        self._buf += data
        written = 0
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            if any(marker in line for marker in _INVALID_AX_MARKERS):
                continue
            written += self._real.write(line + "\n")
        return written

    def flush(self) -> None:
        if self._buf:
            if not any(marker in self._buf for marker in _INVALID_AX_MARKERS):
                self._real.write(self._buf)
            self._buf = ""
        self._real.flush()

    def fileno(self) -> int:
        return self._real.fileno()

    def isatty(self) -> bool:
        return self._real.isatty()

    def __getattr__(self, name: str) -> object:
        return getattr(self._real, name)


_message_handler_installed = False
_stderr_filter_installed = False


def ensure_qt_api() -> None:
    """Ensure ``PySide6.__version__`` exists for qtpy and tooling."""
    os.environ.setdefault("QT_API", "pyside6")
    import PySide6
    from PySide6 import QtCore

    qt_version = str(getattr(QtCore, "__version__", None) or getattr(PySide6, "__version__", "0"))
    if getattr(PySide6, "__version__", None) is None:
        PySide6.__version__ = qt_version


def install_platform_noise_filters() -> None:
    """Silence macOS Cocoa AX spam from Fluent/Qt temporary widgets.

    Call **before** ``QApplication`` is created. Filters both Qt message
    handler path and raw stderr (NSLog) for ``* on invalid object``.
    """
    global _message_handler_installed, _stderr_filter_installed

    if sys.platform == "darwin" and not _stderr_filter_installed:
        sys.stderr = _LineFilteringStream(sys.stderr)  # type: ignore[assignment]
        _stderr_filter_installed = True

    if _message_handler_installed:
        return

    from PySide6.QtCore import QMessageLogContext, QtMsgType, qInstallMessageHandler

    previous = qInstallMessageHandler(None)

    def _handler(mode: QtMsgType, context: QMessageLogContext, message: str) -> None:
        if any(marker in message for marker in _INVALID_AX_MARKERS):
            return
        if previous is not None:
            previous(mode, context, message)

    qInstallMessageHandler(_handler)
    _message_handler_installed = True


def bind_accessible_root(window: object) -> None:
    """Register the main window as the QAccessible root (macOS VoiceOver tree)."""
    try:
        from PySide6.QtGui import QAccessible
        from PySide6.QtWidgets import QWidget

        if isinstance(window, QWidget):
            QAccessible.setRootObject(window)
    except Exception:  # noqa: BLE001
        return
