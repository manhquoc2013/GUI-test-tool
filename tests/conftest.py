"""Pytest bootstrap — patch PySide6-Essentials for pytest-qt compatibility."""

from __future__ import annotations

import importlib.metadata
import os

# Force pytest-qt onto PySide6 before it probes backends.
os.environ.setdefault("QT_API", "pyside6")

import PySide6

if not getattr(PySide6, "__version__", None):
    for dist_name in ("PySide6_Essentials", "PySide6-Essentials", "PySide6"):
        try:
            PySide6.__version__ = importlib.metadata.version(dist_name)
            break
        except importlib.metadata.PackageNotFoundError:
            continue
    else:
        from PySide6.QtCore import qVersion

        PySide6.__version__ = qVersion()
