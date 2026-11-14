"""Single transcript row: role bubble (user right / agent left) + Copy / Undo."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QSizePolicy,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import TransparentToolButton

from ai_qa_test_studio.constants import (
    CHAT_BODY_FONT_PX,
    CHAT_BUBBLE_GAP,
    CHAT_BUBBLE_MAX_WIDTH,
    CHAT_BUBBLE_PADDING_H,
    CHAT_BUBBLE_PADDING_V,
    CHAT_BUBBLE_RADIUS,
    CHAT_BUBBLE_ROW_MARGIN,
    CHAT_ROLE_BADGE_FONT_PX,
    CHAT_ROLE_BADGE_PAD_H,
    CHAT_ROLE_BADGE_PAD_V,
    CHAT_ROLE_BADGE_RADIUS,
    CHAT_ROLE_LABELS,
    ROLE_SYSTEM,
    UI_MSG_ACTION_BTN_SIZE,
)
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme


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
        self._is_user = role == "user"
        self._is_system = role == ROLE_SYSTEM

        row = QHBoxLayout(self)
        row.setContentsMargins(
            CHAT_BUBBLE_ROW_MARGIN,
            CHAT_BUBBLE_ROW_MARGIN // 2,
            CHAT_BUBBLE_ROW_MARGIN,
            CHAT_BUBBLE_ROW_MARGIN // 2,
        )
        row.setSpacing(0)

        self.bubble = QFrame(self)
        self.bubble.setObjectName("ChatBubble")
        self.bubble.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Minimum)
        self.bubble.setMaximumWidth(CHAT_BUBBLE_MAX_WIDTH)

        bubble_col = QVBoxLayout(self.bubble)
        bubble_col.setContentsMargins(
            CHAT_BUBBLE_PADDING_H,
            CHAT_BUBBLE_PADDING_V,
            CHAT_BUBBLE_PADDING_H,
            CHAT_BUBBLE_PADDING_V,
        )
        bubble_col.setSpacing(CHAT_BUBBLE_GAP)

        header = QHBoxLayout()
        header.setContentsMargins(0, 0, 0, 0)
        header.setSpacing(6)

        label = CHAT_ROLE_LABELS.get(role, role)
        self.badge = QLabel(label, self.bubble)
        self.badge.setObjectName("ChatRoleBadge")
        self.badge.setAlignment(Qt.AlignmentFlag.AlignCenter)

        header.addWidget(self.badge, 0, Qt.AlignmentFlag.AlignVCenter)
        header.addStretch(1)

        self.btn_copy = TransparentToolButton(FIF.COPY, self.bubble)
        self.btn_copy.setFixedSize(UI_MSG_ACTION_BTN_SIZE, UI_MSG_ACTION_BTN_SIZE)
        self.btn_copy.setToolTip("Copy tin nhắn")
        self.btn_copy.setAccessibleName("Copy tin nhắn")
        self.btn_copy.clicked.connect(lambda: self.copy_clicked.emit(self.message_id))
        header.addWidget(self.btn_copy, 0, Qt.AlignmentFlag.AlignTop)

        self.btn_undo = TransparentToolButton(FIF.CANCEL, self.bubble)
        self.btn_undo.setFixedSize(UI_MSG_ACTION_BTN_SIZE, UI_MSG_ACTION_BTN_SIZE)
        self.btn_undo.setToolTip("Hoàn tác từ tin này")
        self.btn_undo.setAccessibleName("Hoàn tác tin nhắn")
        self.btn_undo.setVisible(self._is_user)
        self.btn_undo.clicked.connect(lambda: self.undo_clicked.emit(self.message_id))
        header.addWidget(self.btn_undo, 0, Qt.AlignmentFlag.AlignTop)

        bubble_col.addLayout(header)

        self.body = QLabel(text or "…", self.bubble)
        self.body.setObjectName("ChatBubbleBody")
        self.body.setWordWrap(True)
        self.body.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        self.body.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Minimum)
        bubble_col.addWidget(self.body)

        if self._is_user:
            row.addStretch(1)
            row.addWidget(self.bubble, 0, Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignTop)
        else:
            row.addWidget(self.bubble, 0, Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignTop)
            row.addStretch(1)

        # Compat: older tests / callers may look for `.meta`
        self.meta = self.badge

        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        accent = t.bubble_role_accents.get(self.role, t.text_secondary)
        if self._is_user:
            bg = t.bubble_user_bg
            border = t.bubble_user_border
            body_fg = t.bubble_user_fg
            radius = (
                f"{CHAT_BUBBLE_RADIUS}px {CHAT_BUBBLE_RADIUS}px "
                f"2px {CHAT_BUBBLE_RADIUS}px"
            )
            left_accent = "0"
        elif self._is_system:
            bg = t.bubble_system_bg
            border = t.bubble_system_border
            body_fg = t.warning_desc if t.warning_desc else t.text_secondary
            radius = f"{CHAT_BUBBLE_RADIUS}px"
            left_accent = "3px"
        else:
            bg = t.bubble_agent_bg
            border = t.bubble_agent_border
            body_fg = t.text_primary
            radius = (
                f"{CHAT_BUBBLE_RADIUS}px {CHAT_BUBBLE_RADIUS}px "
                f"{CHAT_BUBBLE_RADIUS}px 2px"
            )
            left_accent = "3px"

        self.bubble.setStyleSheet(
            f"#ChatBubble {{"
            f"background-color: {bg};"
            f"border: 1px solid {border};"
            f"border-left: {left_accent} solid {accent};"
            f"border-radius: {radius};"
            f"}}"
        )
        self.badge.setStyleSheet(
            f"#ChatRoleBadge {{"
            f"color: {accent};"
            f"background-color: transparent;"
            f"border: 1px solid {accent};"
            f"border-radius: {CHAT_ROLE_BADGE_RADIUS}px;"
            f"padding: {CHAT_ROLE_BADGE_PAD_V}px {CHAT_ROLE_BADGE_PAD_H}px;"
            f"font-size: {CHAT_ROLE_BADGE_FONT_PX}px;"
            f"font-weight: 600;"
            f"}}"
        )
        self.body.setStyleSheet(
            f"#ChatBubbleBody {{"
            f"color: {body_fg};"
            f"font-size: {CHAT_BODY_FONT_PX}px;"
            f"background: transparent;"
            f"border: none;"
            f"}}"
        )

    def set_body(self, text: str) -> None:
        self._text = text
        self.body.setText(text or "…")
