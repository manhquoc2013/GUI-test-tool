"""Single transcript row: bubble text + per-message Copy / Undo icons."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QHBoxLayout, QLabel, QVBoxLayout, QWidget
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import TransparentToolButton

from ai_qa_test_studio.constants import (
    ICON_COLOR_PRIMARY,
    ROLE_COMPOSER,
    ROLE_CREW,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
    THEME_TEXT_MUTED,
    THEME_TEXT_PRIMARY,
    THEME_TEXT_SECONDARY,
    UI_MSG_ACTION_BTN_SIZE,
)

_ROLE_LABEL = {
    "user": "Bạn",
    ROLE_PLANNER: "Planner",
    ROLE_COMPOSER: "Composer",
    ROLE_CRITIC: "Critic",
    ROLE_CREW: "Crew",
    ROLE_SYSTEM: "Hệ thống",
    "assistant": "AI",
}

_ROLE_COLOR = {
    "user": "#22D3EE",
    ROLE_PLANNER: "#FACC15",
    ROLE_COMPOSER: ICON_COLOR_PRIMARY,
    ROLE_CRITIC: "#E879F9",
    ROLE_CREW: THEME_TEXT_MUTED,
    ROLE_SYSTEM: THEME_TEXT_MUTED,
}


class ChatMessageRow(QWidget):
    copy_clicked = Signal(str)
    undo_clicked = Signal(str)

    def __init__(
        self,
        message_id: str,
        role: str,
        text: str,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.message_id = message_id
        self.role = role
        self._text = text
        layout = QHBoxLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.setSpacing(6)

        body = QVBoxLayout()
        body.setContentsMargins(0, 0, 0, 0)
        body.setSpacing(2)
        label = _ROLE_LABEL.get(role, role)
        self.meta = QLabel(f"[{label}]", self)
        color = _ROLE_COLOR.get(role, THEME_TEXT_SECONDARY)
        self.meta.setStyleSheet(f"color: {color}; font-size: 11px;")
        self.body = QLabel(text or "…", self)
        self.body.setWordWrap(True)
        self.body.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        self.body.setStyleSheet(f"color: {THEME_TEXT_PRIMARY}; font-size: 13px;")
        body.addWidget(self.meta)
        body.addWidget(self.body)
        layout.addLayout(body, stretch=1)

        actions = QVBoxLayout()
        actions.setContentsMargins(0, 0, 0, 0)
        actions.setSpacing(2)
        self.btn_copy = TransparentToolButton(FIF.COPY, self)
        self.btn_copy.setFixedSize(UI_MSG_ACTION_BTN_SIZE, UI_MSG_ACTION_BTN_SIZE)
        self.btn_copy.setToolTip("Copy tin nhắn")
        self.btn_copy.clicked.connect(lambda: self.copy_clicked.emit(self._text))
        actions.addWidget(self.btn_copy)
        self.btn_undo = TransparentToolButton(FIF.CANCEL, self)
        self.btn_undo.setFixedSize(UI_MSG_ACTION_BTN_SIZE, UI_MSG_ACTION_BTN_SIZE)
        self.btn_undo.setToolTip("Hoàn tác từ tin này")
        self.btn_undo.setVisible(role == "user")
        self.btn_undo.clicked.connect(lambda: self.undo_clicked.emit(self.message_id))
        actions.addWidget(self.btn_undo)
        actions.addStretch(1)
        layout.addLayout(actions)

    def set_body(self, text: str) -> None:
        self._text = text
        self.body.setText(text or "…")
