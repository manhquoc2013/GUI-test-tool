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


def _effects_force_disabled() -> bool:
    flag = (os.environ.get("AIQA_DISABLE_QT_EFFECTS") or "").strip().lower()
    return flag in {"1", "true", "yes", "on"}


def _running_in_ci() -> bool:
    return (os.environ.get("CI") or "").strip().lower() in {"1", "true", "yes"}


def graphics_effects_supported() -> bool:
    """False where DropShadow / grab often SIGABRT (exit 134).

    Headless QPA plugins, GitHub ``CI``, and ``AIQA_DISABLE_QT_EFFECTS=1`` disable
    shadows. Prefer ``offscreen`` over ``xcb``+Xvfb in CI — ``xcb`` is not in the
    headless set and would re-enable the aborting shadow path.
    """
    from ai_qa_test_studio.constants import QT_QPA_HEADLESS_PLATFORMS

    if _effects_force_disabled() or _running_in_ci():
        return False
    platform = (os.environ.get("QT_QPA_PLATFORM") or "").strip().lower()
    return platform not in QT_QPA_HEADLESS_PLATFORMS


def opacity_effects_supported() -> bool:
    """False where ``QGraphicsOpacityEffect`` paint often SIGABRT on Linux CI.

    macOS ``offscreen`` usually tolerates opacity fades; Linux headless does not.
    """
    from ai_qa_test_studio.constants import QT_QPA_HEADLESS_PLATFORMS

    if _effects_force_disabled():
        return False
    if _running_in_ci() and sys.platform.startswith("linux"):
        return False
    if sys.platform.startswith("linux"):
        platform = (os.environ.get("QT_QPA_PLATFORM") or "").strip().lower()
        if platform in QT_QPA_HEADLESS_PLATFORMS:
            return False
    return True


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
