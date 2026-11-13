"""Consistent toolbar / tree icons via qtawesome."""

from __future__ import annotations

from PySide6.QtGui import QIcon

from ai_qa_test_studio.constants import (
    ICON_COLOR_DANGER,
    ICON_COLOR_DEFAULT,
    ICON_COLOR_PRIMARY,
    ICON_COLOR_SUCCESS,
    ICON_COLOR_WARN,
)
from ai_qa_test_studio.ui.qt_compat import ensure_qt_api

ICON_WIZARD = "mdi6.auto-fix"
ICON_NEW_CASE = "mdi6.file-plus-outline"
ICON_SYNC = "mdi6.link-variant"
ICON_RUN = "mdi6.play"
ICON_STOP = "mdi6.stop"
ICON_EXPORT = "mdi6.export-variant"
ICON_LAUNCH = "mdi6.web"
ICON_INSPECT = "mdi6.crosshairs-gps"
ICON_SEARCH = "mdi6.magnify"
ICON_DRAWER_COLLAPSE = "mdi6.chevron-down"
ICON_DRAWER_EXPAND = "mdi6.chevron-up"
ICON_SUITE = "mdi6.folder-outline"
ICON_CASE = "mdi6.file-document-outline"
ICON_ELEMENT = "mdi6.vector-square"

def fa_icon(name: str, color: str = ICON_COLOR_DEFAULT) -> QIcon:
    ensure_qt_api()
    import qtawesome as qta

    return qta.icon(name, color=color)


def wizard_icon() -> QIcon:
    return fa_icon(ICON_WIZARD, ICON_COLOR_PRIMARY)


def new_case_icon() -> QIcon:
    return fa_icon(ICON_NEW_CASE)


def sync_icon() -> QIcon:
    return fa_icon(ICON_SYNC, ICON_COLOR_WARN)


def run_icon() -> QIcon:
    return fa_icon(ICON_RUN, ICON_COLOR_SUCCESS)


def stop_icon() -> QIcon:
    return fa_icon(ICON_STOP, ICON_COLOR_DANGER)


def export_icon() -> QIcon:
    return fa_icon(ICON_EXPORT)


def launch_icon() -> QIcon:
    return fa_icon(ICON_LAUNCH)


def inspect_icon() -> QIcon:
    return fa_icon(ICON_INSPECT, ICON_COLOR_WARN)


def search_icon() -> QIcon:
    return fa_icon(ICON_SEARCH)


def drawer_collapse_icon() -> QIcon:
    return fa_icon(ICON_DRAWER_COLLAPSE)


def drawer_expand_icon() -> QIcon:
    return fa_icon(ICON_DRAWER_EXPAND)


def suite_icon() -> QIcon:
    return fa_icon(ICON_SUITE)


def case_icon() -> QIcon:
    return fa_icon(ICON_CASE)


def element_icon() -> QIcon:
    return fa_icon(ICON_ELEMENT)
