from __future__ import annotations

from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.ui.icons import make_stop_icon


def test_make_stop_icon_is_non_null(qapp: QApplication) -> None:
    del qapp
    icon = make_stop_icon(color="#0F172A", size=36)
    assert not icon.isNull()
