"""Coordinator status cards must settle after handoff / turn finish (no stuck dots)."""

from __future__ import annotations

from PySide6.QtWidgets import QApplication, QSizePolicy

from ai_qa_test_studio.constants import (
    CHAT_ROLE_LABELS,
    COPILOT_STATUS_ROLE_ROUTED,
    COPILOT_STATUS_WAITING_FRAGMENT,
    COPILOT_STATUS_WORKING,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
)
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


def test_on_turn_finished_stops_coordinator_typing_dots(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.begin_role(ROLE_COMPOSER)
    coord = pane._rows[0]
    assert not coord.is_typing()
    assert not coord.typing.is_running()

    # Leave a zombie typing card, then force turn cleanup.
    pane.begin_role(ROLE_COORDINATOR)
    assert pane._streaming_row is not None
    assert pane._streaming_row.is_typing()
    pane.on_turn_finished()

    for row in pane._rows:
        assert not row.is_typing()
        assert not row.typing.is_running()
        assert COPILOT_STATUS_WAITING_FRAGMENT not in (row._text or "").casefold()
        assert COPILOT_STATUS_WAITING_FRAGMENT not in row.typing_label.text().casefold()
    pane.close()


def test_status_pill_long_text_not_clipped(qapp: QApplication) -> None:
    row = ChatMessageRow("status-1", ROLE_COORDINATOR, "")
    row.show()
    row.resize(280, 40)
    qapp.processEvents()
    long_label = COPILOT_STATUS_WORKING.format(role=CHAT_ROLE_LABELS[ROLE_COORDINATOR])
    long_label = (
        f"{long_label} · đã giao → {CHAT_ROLE_LABELS[ROLE_COMPOSER]} "
        f"· đang chờ phản hồi model và cập nhật board"
    )
    row.set_typing_status(long_label)
    row.sync_bubble_width()
    row.adjustSize()
    qapp.processEvents()

    assert row.typing_label.wordWrap() is True
    policy = row.typing_label.sizePolicy()
    assert policy.horizontalPolicy() == QSizePolicy.Policy.Expanding
    assert policy.verticalPolicy() == QSizePolicy.Policy.Minimum
    # Status pill itself must grow with wrapped text (no horizontal clip).
    assert row.typing_label.height() >= row.typing_label.sizeHint().height()
    row.close()


def test_handoff_hides_copy_on_status_card(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.begin_role(ROLE_COMPOSER)
    coord = pane._rows[0]
    assert coord.body.text() == COPILOT_STATUS_ROLE_ROUTED.format(
        next_role=CHAT_ROLE_LABELS[ROLE_COMPOSER]
    )
    assert coord.is_status_card()
    assert coord.btn_copy.isHidden()

    pane.end_role(ROLE_COMPOSER, "Xin chào, đây là câu trả lời thật.")
    specialist = pane._rows[1]
    assert not specialist.btn_copy.isHidden()
    assert not specialist.is_status_card()
    pane.close()
