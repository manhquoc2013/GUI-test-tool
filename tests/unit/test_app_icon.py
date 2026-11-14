"""Packaged application icon assets."""

from __future__ import annotations

from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import APP_ICON_SIZES
from ai_qa_test_studio.resources import app_icon_path, icons_dir, load_app_icon


def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    assert isinstance(app, QApplication)
    return app


def test_icon_assets_exist_on_disk() -> None:
    assert app_icon_path().is_file()
    directory = icons_dir()
    for size in APP_ICON_SIZES:
        assert (directory / f"app_icon_{size}.png").is_file()
    assert (directory / "app_icon.ico").is_file()
    assert (directory / "app_icon.icns").is_file()


def test_load_app_icon_is_usable() -> None:
    _ = qapp()
    icon = load_app_icon()
    assert isinstance(icon, QIcon)
    assert not icon.isNull()
    assert icon.availableSizes()
