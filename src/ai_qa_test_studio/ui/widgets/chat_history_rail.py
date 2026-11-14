"""Collapsible Copilot session history rail."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QHBoxLayout,
    QListWidget,
    QListWidgetItem,
    QSizePolicy,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import BodyLabel, StrongBodyLabel, TransparentToolButton
from qfluentwidgets import FluentIcon as FIF

from ai_qa_test_studio.constants import (
    COPILOT_HISTORY_BADGE_RUNNING,
    COPILOT_HISTORY_BADGE_UNREAD,
    COPILOT_HISTORY_DELETE_TIP,
    COPILOT_HISTORY_EMPTY,
    COPILOT_HISTORY_RAIL_WIDTH,
    COPILOT_HISTORY_TITLE,
    THEME_CARD_RADIUS,
)
from ai_qa_test_studio.core.chat_models import CopilotSession
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme

_ROLE_DATA_SESSION_ID = Qt.ItemDataRole.UserRole


class ChatHistoryRail(QWidget):
    """Lists nonempty Copilot sessions; click activates, trash deletes selection."""

    session_activated = Signal(str)
    delete_requested = Signal(str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("CopilotHistoryRail")
        self.setAccessibleName(COPILOT_HISTORY_TITLE)
        self.setFixedWidth(COPILOT_HISTORY_RAIL_WIDTH)
        self.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Expanding)

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(4)

        header = QWidget(self)
        header_row = QHBoxLayout(header)
        header_row.setContentsMargins(4, 0, 0, 0)
        header_row.setSpacing(2)
        self.title_label = StrongBodyLabel(COPILOT_HISTORY_TITLE, header)
        self.btn_delete = TransparentToolButton(FIF.DELETE, header)
        self.btn_delete.setFixedSize(28, 28)
        self.btn_delete.setToolTip(COPILOT_HISTORY_DELETE_TIP)
        self.btn_delete.clicked.connect(self._emit_delete_selected)
        header_row.addWidget(self.title_label, stretch=1)
        header_row.addWidget(self.btn_delete, stretch=0)
        root.addWidget(header)

        self.empty_label = BodyLabel(COPILOT_HISTORY_EMPTY, self)
        self.empty_label.setWordWrap(True)
        self.empty_label.setAlignment(Qt.AlignmentFlag.AlignTop | Qt.AlignmentFlag.AlignLeft)
        root.addWidget(self.empty_label)

        self.list = QListWidget(self)
        self.list.setObjectName("CopilotHistoryList")
        self.list.setAccessibleName(COPILOT_HISTORY_TITLE)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.list.setSpacing(2)
        self.list.itemClicked.connect(self._on_item_clicked)
        root.addWidget(self.list, stretch=1)

        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)
        self._apply_theme_style()
        self.set_sessions([], active_id=None)

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(
            f"#CopilotHistoryRail {{ background: {t.bg_card};"
            f" border: 1px solid {t.border_card};"
            f" border-radius: {THEME_CARD_RADIUS}px; }}"
        )
        self.list.setStyleSheet(
            f"QListWidget {{ background: transparent; border: none; outline: none;"
            f" color: {t.text_primary}; }}"
            f"QListWidget::item {{ padding: 6px 8px; border-radius: 6px; }}"
            f"QListWidget::item:selected {{ background: {t.bg_card};"
            f" border: 1px solid {t.border_card}; }}"
            f"QListWidget::item:hover {{ background: {t.bg_card}; }}"
        )
        self.empty_label.setStyleSheet(f"color: {t.text_muted}; padding: 4px 8px;")
        self.title_label.setStyleSheet(f"color: {t.text_secondary}; padding-left: 4px;")

    def set_sessions(
        self,
        sessions: list[CopilotSession],
        *,
        active_id: str | None,
        running_ids: set[str] | None = None,
        unread_ids: set[str] | None = None,
    ) -> None:
        running = running_ids or set()
        unread = unread_ids or set()
        self.list.blockSignals(True)
        self.list.clear()
        for session in sessions:
            label = self._row_label(session, running=session.id in running, unread=session.id in unread)
            item = QListWidgetItem(label)
            item.setData(_ROLE_DATA_SESSION_ID, session.id)
            item.setToolTip(session.title)
            self.list.addItem(item)
            if active_id and session.id == active_id:
                self.list.setCurrentItem(item)
        self.list.blockSignals(False)
        has_rows = self.list.count() > 0
        self.list.setVisible(has_rows)
        self.empty_label.setVisible(not has_rows)
        self.btn_delete.setEnabled(has_rows and bool(self.list.currentItem()))

    def selected_session_id(self) -> str | None:
        item = self.list.currentItem()
        if item is None:
            return None
        value = item.data(_ROLE_DATA_SESSION_ID)
        return str(value) if value else None

    @staticmethod
    def _row_label(session: CopilotSession, *, running: bool, unread: bool) -> str:
        badges: list[str] = []
        if running:
            badges.append(COPILOT_HISTORY_BADGE_RUNNING)
        elif unread:
            badges.append(COPILOT_HISTORY_BADGE_UNREAD)
        prefix = f"{' '.join(badges)} " if badges else ""
        title = (session.title or "").strip() or "Phiên mới"
        return f"{prefix}{title}"

    def _on_item_clicked(self, item: QListWidgetItem) -> None:
        session_id = item.data(_ROLE_DATA_SESSION_ID)
        if session_id:
            self.session_activated.emit(str(session_id))
        self.btn_delete.setEnabled(True)

    def _emit_delete_selected(self) -> None:
        session_id = self.selected_session_id()
        if session_id:
            self.delete_requested.emit(session_id)
