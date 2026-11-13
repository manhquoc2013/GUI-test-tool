"""Compatibility shims for PySide6-Essentials + qtpy/qtawesome."""

from __future__ import annotations

import os


def ensure_qt_api() -> None:
    """Make qtpy see PySide6-Essentials as a valid binding.

    qtawesome → qtpy does ``from PySide6 import __version__``. Essentials
    installs working Qt modules but omits the package-level ``__version__``,
    which raises ``QtBindingsNotFoundError``.
    """
    os.environ.setdefault("QT_API", "pyside6")
    import PySide6
    from PySide6.QtCore import __version__ as qt_version

    if getattr(PySide6, "__version__", None) is None:
        PySide6.__version__ = qt_version
