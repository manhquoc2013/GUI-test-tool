"""System settings dialog layout + save smoke."""

from __future__ import annotations

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication, QStackedWidget, QWidget

from ai_qa_test_studio.constants import (
    UI_SETTINGS_BTN_MAX_WIDTH,
    UI_SETTINGS_MIN_HEIGHT,
    UI_SETTINGS_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import AppSettings
from ai_qa_test_studio.ui.widgets.system_settings_dialog import SystemSettingsDialog


@pytest.fixture(scope="session")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_settings_dialog_uses_stack_and_saves(qapp: QApplication) -> None:
    host = QWidget()
    host.resize(1200, 800)
    host.show()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()

    assert dialog.widget.minimumWidth() >= UI_SETTINGS_MIN_WIDTH
    assert dialog.widget.minimumHeight() >= UI_SETTINGS_MIN_HEIGHT
    assert isinstance(dialog.stack, QStackedWidget)
    assert dialog.stack.count() == 7
    assert dialog.yesButton.maximumWidth() <= UI_SETTINGS_BTN_MAX_WIDTH
    assert dialog.cancelButton.maximumWidth() <= UI_SETTINGS_BTN_MAX_WIDTH

    dialog.env_edit.setText("STAGING")
    dialog.spin_summary.setValue(120)
    assert dialog.validate() is True
    result = dialog.result_settings()
    assert result is not None
    assert result.active_environment == "STAGING"
    assert result.copilot.summary_max_chars == 120
    dialog.close()
    host.close()


def test_settings_tab_switch_reloads_api_list(qapp: QApplication) -> None:
    host = QWidget()
    host.resize(1200, 800)
    host.show()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    dialog._show("apis", 2)
    qapp.processEvents()
    assert dialog.stack.currentIndex() == 2
    assert dialog.api_list.count() >= 1
    dialog.close()
    host.close()


def test_apply_api_seals_key_into_draft_immediately(
    qapp: QApplication, monkeypatch: pytest.MonkeyPatch
) -> None:
    from ai_qa_test_studio.constants import KEYRING_REF_PREFIX

    sealed: list[tuple[str, str]] = []

    def _fake_seal(slot: str, value: str) -> str:
        sealed.append((slot, value))
        return f"{KEYRING_REF_PREFIX}{slot}"

    monkeypatch.setattr(
        "ai_qa_test_studio.ui.widgets.system_settings_dialog.seal_api_key_ref",
        _fake_seal,
    )
    host = QWidget()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    dialog._show("apis", 2)
    qapp.processEvents()
    dialog.api_list.setCurrentRow(0)
    qapp.processEvents()
    dialog.api_key.setText("sk-live-plaintext")
    dialog._apply_api()
    conn = dialog._current_conn()
    assert conn is not None
    assert sealed
    assert sealed[-1][1] == "sk-live-plaintext"
    assert conn.api_key_ref.startswith(KEYRING_REF_PREFIX)
    assert "sk-live-plaintext" not in conn.api_key_ref
    dialog.close()
    host.close()
