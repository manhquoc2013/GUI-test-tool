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


def test_settings_reduce_motion_switch(qapp: QApplication) -> None:
    from PySide6.QtWidgets import QWidget

    host = QWidget()
    host.show()
    dialog = SystemSettingsDialog(AppSettings(reduce_motion=True), host)
    dialog.show()
    qapp.processEvents()
    assert dialog.reduce_motion_switch.isChecked() is True
    dialog.reduce_motion_switch.setChecked(False)
    dialog.validate()
    result = dialog.result_settings()
    assert result is not None
    assert result.reduce_motion is False
    dialog.close()
    host.close()


def test_settings_commit_skips_theme_repolish_when_unchanged(
    qapp: QApplication, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Saving with the same theme must not re-polish the whole widget tree."""
    window = MainWindow(data_root=tmp_path)
    window.show()
    qapp.processEvents()
    calls: list[str] = []
    monkeypatch.setattr(
        "ai_qa_test_studio.ui.main_window.apply_studio_theme",
        lambda _app, *, preference=None: calls.append(str(preference)),
    )

    result = window.env.settings.model_copy(deep=True)
    window._commit_system_settings(result, theme_changed=False)
    assert calls == []

    window._commit_system_settings(result, theme_changed=True)
    assert calls == [result.theme]
    window.close()
