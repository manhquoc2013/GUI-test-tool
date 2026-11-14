"""Live typing-status updates on Copilot bubbles."""

from __future__ import annotations

from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    CHAT_ROLE_LABELS,
    COPILOT_STATUS_COORD_DECIDE,
    COPILOT_STATUS_ROLE_HANDOFF,
    COPILOT_STATUS_WAITING_LLM,
    COPILOT_STATUS_WORKING,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
)
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane


def test_begin_role_sets_working_typing_and_handoff(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    assert pane._streaming_row is not None
    assert pane._streaming_row.is_typing()
    assert pane._streaming_row.typing_label.text() == COPILOT_STATUS_WORKING.format(
        role=CHAT_ROLE_LABELS[ROLE_COORDINATOR]
    )

    pane.begin_role(ROLE_COMPOSER)
    coord = pane._rows[0]
    assert coord.is_typing()
    assert coord.typing_label.text() == COPILOT_STATUS_ROLE_HANDOFF.format(
        role=CHAT_ROLE_LABELS[ROLE_COORDINATOR],
        next_role=CHAT_ROLE_LABELS[ROLE_COMPOSER],
    )
    assert pane._streaming_row is not None
    assert pane._streaming_row.role == ROLE_COMPOSER
    assert pane._streaming_row.typing_label.text() == COPILOT_STATUS_WORKING.format(
        role=CHAT_ROLE_LABELS[ROLE_COMPOSER]
    )
    pane.close()


def test_set_streaming_status_targets_role_bubble(qapp: QApplication) -> None:
    del qapp
    pane = CopilotChatPane()
    pane.begin_role(ROLE_COORDINATOR)
    pane.begin_role(ROLE_COMPOSER)
    pane.set_streaming_status(COPILOT_STATUS_WAITING_LLM, role=ROLE_COMPOSER)
    assert pane._rows[1].typing_label.text() == COPILOT_STATUS_WAITING_LLM
    pane.set_streaming_status(COPILOT_STATUS_COORD_DECIDE, role=ROLE_COORDINATOR)
    assert pane._rows[0].typing_label.text() == COPILOT_STATUS_COORD_DECIDE
    pane.close()
