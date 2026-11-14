from __future__ import annotations

from PySide6.QtWidgets import QApplication, QHBoxLayout

from ai_qa_test_studio.constants import (
    CHAT_BUBBLE_MAX_WIDTH,
    CHAT_ROLE_LABELS,
    ROLE_COMPOSER,
    ROLE_PLANNER,
    ROLE_SYSTEM,
)
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow


def test_user_bubble_aligns_right(qapp: QApplication) -> None:
    row = ChatMessageRow("m1", "user", "xin chào")
    row.show()
    qapp.processEvents()
    layout = row.layout()
    assert isinstance(layout, QHBoxLayout)
    # stretch, bubble
    assert layout.count() == 2
    assert row.bubble.maximumWidth() == CHAT_BUBBLE_MAX_WIDTH
    assert row.btn_undo.isVisible()
    assert row.badge.text() == CHAT_ROLE_LABELS["user"]
    assert "ChatBubble" in row.bubble.styleSheet() or row.bubble.objectName() == "ChatBubble"
    assert "border-radius" in row.bubble.styleSheet()
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
