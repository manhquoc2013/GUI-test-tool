"""Pytest bootstrap — patch PySide6-Essentials for pytest-qt compatibility."""

from __future__ import annotations

import importlib.metadata
import os
import sys

# Force pytest-qt onto PySide6 before it probes backends.
os.environ.setdefault("QT_API", "pyside6")
# Headless CI / no display: avoid plugin load requiring a real GPU/EGL stack.
# Keep offscreen (not xcb): xcb enables DropShadow and SIGABRT on Linux CI.
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
# Belt-and-suspenders with graphics_effects_supported() — opacity/shadow paint aborts.
if (os.environ.get("CI") or "").strip().lower() in {"1", "true", "yes"}:
    os.environ.setdefault("AIQA_DISABLE_QT_EFFECTS", "1")

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


def pytest_collection_modifyitems(config: object, items: list[object]) -> None:  # noqa: ARG001
    """Skip full-window Fluent suites on Linux CI — qfluentwidgets shadows SIGABRT (134)."""
    if not sys.platform.startswith("linux"):
        return
    if (os.environ.get("CI") or "").strip().lower() not in {"1", "true", "yes"}:
        return
    import pytest

    skip = pytest.mark.skip(reason="Full Qt/Fluent window suite SIGABRT on Linux CI headless")
    skip_prefixes = (
        "tests/ui/",
        "tests/integration/test_toolbar_sync_flow.py",
        "tests/unit/test_ui_motion_and_smoothness.py",
        "tests/unit/test_chat_history_rail.py",
        "tests/unit/test_stream_length_and_errors.py",
        "tests/unit/test_chat_status_settle.py",
        "tests/unit/test_settle_incomplete_messages.py",
        "tests/unit/test_structured_chat_payload.py",
        "tests/unit/test_chat_ingest.py",
        "tests/unit/test_evidence_panel_and_report_service.py",
        "tests/unit/test_theme_tokens.py",
    )
    for item in items:
        nodeid = str(getattr(item, "nodeid", ""))
        if any(nodeid.startswith(prefix) for prefix in skip_prefixes):
            item.add_marker(skip)  # type: ignore[attr-defined]
