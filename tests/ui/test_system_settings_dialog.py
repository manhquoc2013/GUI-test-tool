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
    assert "120" in dialog.spin_summary.text()
    assert dialog.spin_summary.suffix()
    assert dialog.spin_clarify.value() >= 5_000
    assert dialog.validate() is True
    result = dialog.result_settings()
    assert result is not None
    assert result.active_environment == "STAGING"
    assert result.copilot.summary_max_chars == 120
    dialog.close()
    host.close()


def test_settings_scroll_and_spins_are_opaque_for_paint(qapp: QApplication) -> None:
    """Translucent CardWidget/SpinBox + transparent viewport ghosts while scrolling."""
    from PySide6.QtCore import Qt
    from PySide6.QtWidgets import QScrollArea

    host = QWidget()
    host.resize(1200, 800)
    host.show()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()

    page = dialog.stack.currentWidget()
    assert isinstance(page, QScrollArea)
    assert page.objectName() == "SystemSettingsScroll"
    assert "transparent" not in page.styleSheet().lower()
    assert dialog.spin_summary.focusPolicy() == Qt.FocusPolicy.StrongFocus
    assert "transparent" not in dialog.spin_summary.styleSheet().lower()
    dialog.close()
    host.close()


def test_settings_lists_are_top_aligned_fixed_height(qapp: QApplication) -> None:
    from ai_qa_test_studio.constants import UI_SETTINGS_LIST_HEIGHT

    host = QWidget()
    host.resize(1200, 800)
    host.show()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    from PySide6.QtWidgets import QSizePolicy

    assert dialog.api_list.height() == UI_SETTINGS_LIST_HEIGHT
    assert dialog.model_list.height() == UI_SETTINGS_LIST_HEIGHT
    assert dialog.api_list.sizePolicy().verticalPolicy() == QSizePolicy.Policy.Fixed
    assert dialog.model_list.sizePolicy().verticalPolicy() == QSizePolicy.Policy.Fixed
    dialog.close()
    host.close()


def test_models_tab_uses_editable_combo_for_model_id(qapp: QApplication) -> None:
    from qfluentwidgets import EditableComboBox

    host = QWidget()
    host.resize(1200, 800)
    host.show()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    dialog._show("models", 3)
    qapp.processEvents()
    assert isinstance(dialog.model_id, EditableComboBox)
    assert hasattr(dialog, "btn_fetch_models")
    dialog.close()
    host.close()


def test_model_id_combo_stays_api_only_and_searchable(qapp: QApplication) -> None:
    """Dropdown items = provider catalog only; typing filters via completer."""
    from ai_qa_test_studio.constants import UI_SETTINGS_MODEL_UNSET_LABEL

    host = QWidget()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    dialog._show("models", 3)
    qapp.processEvents()

    api_ids = ["cinb/glm-5.3-flash", "cinb/deepseek-v4.1-flash"]
    dialog._on_remote_models_ok(api_ids)
    assert [dialog.model_id.itemText(i) for i in range(dialog.model_id.count())] == api_ids
    completer = dialog.model_id.completer()
    assert completer is not None
    assert completer.filterMode()  # MatchContains enabled for name search

    dialog._set_model_id_text("local-only-id")
    assert dialog.model_id.currentText() == "local-only-id"
    assert dialog.model_id.findText("local-only-id") < 0
    assert dialog.model_id.count() == len(api_ids)

    dialog._add_model()
    conn = dialog._models_conn()
    assert conn is not None
    assert conn.models[-1].id == "cinb/glm-5.3-flash"
    dialog._add_model()
    assert conn.models[-1].id == "cinb/deepseek-v4.1-flash"
    dialog._add_model()
    assert conn.models[-1].id == ""
    assert UI_SETTINGS_MODEL_UNSET_LABEL in dialog.model_list.item(dialog.model_list.count() - 1).text()
    assert dialog.model_id.count() == len(api_ids)
    assert all(dialog.model_id.itemText(i) in api_ids for i in range(dialog.model_id.count()))

    dialog.close()
    host.close()


def test_models_api_combo_binds_connection_userdata(qapp: QApplication) -> None:
    """Fluent ComboBox.addItem(text, icon, userData) — conn id must be userData=."""
    host = QWidget()
    dialog = SystemSettingsDialog(AppSettings(), host)
    dialog.show()
    qapp.processEvents()
    assert dialog.model_api_combo.count() >= 1
    conn = dialog._models_conn()
    assert conn is not None
    assert conn.id == dialog._draft.ai.connections[0].id
    data = dialog.model_api_combo.currentData()
    assert data == conn.id
    dialog._fetch_remote_models()
    # Default ensure is offline → status must not be "Chọn một API trước."
    assert "Chọn một API trước" not in dialog.model_fetch_status.text()
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
