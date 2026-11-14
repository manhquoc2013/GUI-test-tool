"""TopBar theme toggle persists preference and flips Fluent theme."""

from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication
from qfluentwidgets import Theme, isDarkTheme, setTheme

from ai_qa_test_studio.constants import THEME_PREF_DARK, THEME_PREF_LIGHT
from ai_qa_test_studio.core.models import AppSettings
from ai_qa_test_studio.ui.main_window import MainWindow
from ai_qa_test_studio.ui.widgets.system_settings_dialog import SystemSettingsDialog


@pytest.fixture
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_topbar_theme_toggle_persists_override(qapp: QApplication, tmp_path: Path) -> None:
    setTheme(Theme.DARK)
    window = MainWindow(data_root=tmp_path)
    window.show()
    qapp.processEvents()
    assert hasattr(window.top_bar, "btn_theme")
    before = isDarkTheme()
    window.top_bar.theme_toggle_requested.emit()
    qapp.processEvents()
    assert isDarkTheme() is not before
    pref = window.env.settings.theme
    assert pref in {THEME_PREF_DARK, THEME_PREF_LIGHT}
    assert pref == (THEME_PREF_DARK if isDarkTheme() else THEME_PREF_LIGHT)
    window.close()


def test_settings_theme_combo_includes_auto(qapp: QApplication) -> None:
    from PySide6.QtWidgets import QWidget

    host = QWidget()
    host.show()
    dialog = SystemSettingsDialog(AppSettings(theme="auto"), host)
    dialog.show()
    qapp.processEvents()
    items = [dialog.theme_combo.itemText(i) for i in range(dialog.theme_combo.count())]
    assert items == ["auto", "dark", "light"]
    assert dialog.theme_combo.currentText() == "auto"
    dialog.close()
    host.close()
