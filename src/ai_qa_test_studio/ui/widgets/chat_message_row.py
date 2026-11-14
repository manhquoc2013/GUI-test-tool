"""Single transcript row: role bubble (user right / agent left) + Copy / Undo."""

from __future__ import annotations

import math

from PySide6.QtCore import QSize, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QPainter, QPaintEvent
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
    CHAT_TYPING_DOT_BOUNCE_PX,
    CHAT_TYPING_DOT_COUNT,
    CHAT_TYPING_DOT_DIAMETER,
    CHAT_TYPING_DOT_GAP,
    CHAT_TYPING_EMPTY_MARKERS,
    CHAT_TYPING_INTERVAL_MS,
    CHAT_TYPING_LABEL,
    CHAT_TYPING_PHASES,
    ROLE_SYSTEM,
    UI_MSG_ACTION_BTN_SIZE,
)
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme


class TypingDots(QWidget):
    """Messenger-style bouncing dots for an in-flight agent reply."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("ChatTypingDots")
        self.setAccessibleName(CHAT_TYPING_LABEL)
        self._phase = 0
        self._color = QColor("#64748B")
        self._timer = QTimer(self)
        self._timer.setInterval(CHAT_TYPING_INTERVAL_MS)
        self._timer.timeout.connect(self._on_tick)
        height = CHAT_TYPING_DOT_DIAMETER + (CHAT_TYPING_DOT_BOUNCE_PX * 2) + 4
        width = (
            CHAT_TYPING_DOT_COUNT * CHAT_TYPING_DOT_DIAMETER
            + (CHAT_TYPING_DOT_COUNT - 1) * CHAT_TYPING_DOT_GAP
            + 4
        )
        self.setFixedSize(width, height)

    def sizeHint(self) -> QSize:
        return self.size()

    def set_color(self, color: str) -> None:
        parsed = QColor(color)
        if parsed.isValid():
            self._color = parsed
            self.update()

    def is_running(self) -> bool:
        return self._timer.isActive()

    def start(self) -> None:
        if not self._timer.isActive():
            self._timer.start()
        self.show()

    def stop(self) -> None:
        self._timer.stop()
        self._phase = 0
        self.hide()

    def _on_tick(self) -> None:
        self._phase = (self._phase + 1) % CHAT_TYPING_PHASES
        self.update()

    def paintEvent(self, event: QPaintEvent) -> None:
        del event
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)
        base_y = (self.height() - CHAT_TYPING_DOT_DIAMETER) / 2.0
        for index in range(CHAT_TYPING_DOT_COUNT):
            wave = (self._phase + index * 2) % CHAT_TYPING_PHASES
            # Smooth bounce: peak when wave ≈ 0, settle toward trough.
            lift = math.sin((wave / CHAT_TYPING_PHASES) * math.pi * 2.0)
            offset_y = -CHAT_TYPING_DOT_BOUNCE_PX * max(0.0, lift)
            alpha = int(90 + 140 * max(0.0, lift))
            color = QColor(self._color)
            color.setAlpha(alpha)
            painter.setBrush(color)
            x = 2 + index * (CHAT_TYPING_DOT_DIAMETER + CHAT_TYPING_DOT_GAP)
            painter.drawEllipse(
                int(x),
                int(base_y + offset_y),
                CHAT_TYPING_DOT_DIAMETER,
                CHAT_TYPING_DOT_DIAMETER,
            )


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

        self.typing = TypingDots(self.bubble)
        self.typing_label = QLabel(CHAT_TYPING_LABEL, self.bubble)
        self.typing_label.setObjectName("ChatTypingLabel")
        typing_row = QHBoxLayout()
        typing_row.setContentsMargins(0, 2, 0, 0)
        typing_row.setSpacing(8)
        typing_row.addWidget(self.typing, 0, Qt.AlignmentFlag.AlignVCenter)
        typing_row.addWidget(self.typing_label, 0, Qt.AlignmentFlag.AlignVCenter)
        typing_row.addStretch(1)
        self._typing_wrap = QWidget(self.bubble)
        self._typing_wrap.setObjectName("ChatTypingWrap")
        self._typing_wrap.setLayout(typing_row)
        bubble_col.addWidget(self._typing_wrap)

        self.body = QLabel("", self.bubble)
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
        self.set_body(text)

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
        self.typing_label.setStyleSheet(
            f"#ChatTypingLabel {{"
            f"color: {t.text_muted};"
            f"font-size: {CHAT_ROLE_BADGE_FONT_PX}px;"
            f"background: transparent;"
            f"border: none;"
            f"}}"
        )
        self.typing.set_color(accent if not self._is_user else t.text_muted)

    @staticmethod
    def _is_pending_body(text: str) -> bool:
        return text.strip() in CHAT_TYPING_EMPTY_MARKERS

    def is_typing(self) -> bool:
        return self._typing_wrap.isVisible()

    def set_body(self, text: str) -> None:
        if self._is_pending_body(text):
            self._text = ""
            self.body.clear()
            self.body.hide()
            self._typing_wrap.show()
            self.typing.start()
            return
        self._text = text
        self.typing.stop()
        self._typing_wrap.hide()
        self.body.setText(text)
        self.body.show()
