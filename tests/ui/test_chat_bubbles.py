from __future__ import annotations

from PySide6.QtWidgets import QApplication, QHBoxLayout

from ai_qa_test_studio.constants import (
    CHAT_BUBBLE_MIN_WIDTH,
    CHAT_BUBBLE_PANEL_RATIO,
    CHAT_BUBBLE_ROW_MARGIN,
    CHAT_ROLE_LABELS,
    CHAT_TYPING_LABEL,
    ROLE_COMPOSER,
    ROLE_PLANNER,
    ROLE_SYSTEM,
)
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow


def test_user_bubble_aligns_right(qapp: QApplication) -> None:
    row = ChatMessageRow("m1", "user", "xin chào")
    row.resize(400, 80)
    row.show()
    qapp.processEvents()
    layout = row.layout()
    assert isinstance(layout, QHBoxLayout)
    # stretch, bubble
    assert layout.count() == 2
    assert row.btn_undo.isVisible()
    assert row.badge.text() == CHAT_ROLE_LABELS["user"]
    assert "ChatBubble" in row.bubble.styleSheet() or row.bubble.objectName() == "ChatBubble"
    assert "border-radius" in row.bubble.styleSheet()
    assert not row.is_typing()
    row.close()


def test_bubble_max_width_tracks_panel_width(qapp: QApplication) -> None:
    row = ChatMessageRow("m1b", ROLE_COMPOSER, "nội dung dài " * 20)
    row.show()
    row.resize(360, 120)
    qapp.processEvents()
    row.sync_bubble_width()
    expected = max(
        CHAT_BUBBLE_MIN_WIDTH,
        int((360 - CHAT_BUBBLE_ROW_MARGIN * 2) * CHAT_BUBBLE_PANEL_RATIO),
    )
    assert row.bubble.maximumWidth() == expected

    row.resize(640, 120)
    qapp.processEvents()
    row.sync_bubble_width()
    expected_wide = max(
        CHAT_BUBBLE_MIN_WIDTH,
        int((640 - CHAT_BUBBLE_ROW_MARGIN * 2) * CHAT_BUBBLE_PANEL_RATIO),
    )
    assert row.bubble.maximumWidth() == expected_wide
    assert expected_wide > expected
    row.close()


def test_agent_bubble_aligns_left_with_role_badge(qapp: QApplication) -> None:
    row = ChatMessageRow("m2", ROLE_PLANNER, "Kế hoạch 1-2-3")
    row.show()
    qapp.processEvents()
    layout = row.layout()
    assert isinstance(layout, QHBoxLayout)
    assert layout.count() == 2
    assert not row.btn_undo.isVisible()
    assert row.badge.text() == CHAT_ROLE_LABELS[ROLE_PLANNER]
    assert ROLE_PLANNER in row.bubble.styleSheet() or "border-left" in row.bubble.styleSheet()
    row.close()


def test_system_bubble_uses_warning_surface(qapp: QApplication) -> None:
    row = ChatMessageRow("m3", ROLE_SYSTEM, "Clarify: phạm vi?")
    row.show()
    qapp.processEvents()
    assert row.badge.text() == CHAT_ROLE_LABELS[ROLE_SYSTEM]
    style = row.bubble.styleSheet()
    assert "border-left" in style
    row.set_body("updated")
    assert row.body.text() == "updated"
    row.close()


def test_composer_bubble_label(qapp: QApplication) -> None:
    row = ChatMessageRow("m4", ROLE_COMPOSER, "Draft UNBOUND")
    assert row.badge.text() == CHAT_ROLE_LABELS[ROLE_COMPOSER]
    assert "kiểm thử" in row.badge.text().lower()
    assert row.meta is row.badge
    row.close()


def test_empty_agent_bubble_shows_typing_indicator(qapp: QApplication) -> None:
    row = ChatMessageRow("m5", ROLE_COMPOSER, "")
    row.show()
    qapp.processEvents()
    assert row.is_typing()
    assert row.typing_label.text() == CHAT_TYPING_LABEL
    assert row.typing.isVisible()
    assert row.body.isHidden()
    assert row.typing.is_running()

    row.set_body("…")
    qapp.processEvents()
    assert row.is_typing()

    row.set_body("Đã soạn xong")
    qapp.processEvents()
    assert not row.is_typing()
    assert row.body.isVisible()
    assert row.body.text() == "Đã soạn xong"
    assert not row.typing.is_running()
    row.close()
