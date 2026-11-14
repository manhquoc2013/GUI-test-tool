"""Packaged static assets (icons, etc.)."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QSize
from PySide6.QtGui import QIcon

from ai_qa_test_studio.constants import (
    APP_ICON_DIRNAME,
    APP_ICON_FILENAME_TEMPLATE,
    APP_ICON_MASTER_FILENAME,
    APP_ICON_SIZES,
)

_RESOURCES_ROOT = Path(__file__).resolve().parent


def icons_dir() -> Path:
    return _RESOURCES_ROOT / APP_ICON_DIRNAME


def app_icon_path() -> Path:
    """Master PNG used as fallback when sized variants are missing."""
    return icons_dir() / APP_ICON_MASTER_FILENAME


def load_app_icon() -> QIcon:
    """Build a multi-resolution window/dock icon from packaged PNGs."""
    icon = QIcon()
    directory = icons_dir()
    for size in APP_ICON_SIZES:
        path = directory / APP_ICON_FILENAME_TEMPLATE.format(size=size)
        if path.is_file():
            icon.addFile(str(path), QSize(size, size))
    master = app_icon_path()
    if master.is_file():
        icon.addFile(str(master))
    return icon
