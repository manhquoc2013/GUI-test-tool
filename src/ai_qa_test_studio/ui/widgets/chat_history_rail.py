"""Compact Copilot session-history popover (content-sized, not full-height)."""

from __future__ import annotations

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QColor, QMouseEvent, QPalette
from PySide6.QtWidgets import (
    QGraphicsDropShadowEffect,
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
    COPILOT_HISTORY_EMPTY_HEIGHT,
    COPILOT_HISTORY_HEADER_HEIGHT,
    COPILOT_HISTORY_POPOVER_MAX_HEIGHT,
    COPILOT_HISTORY_POPOVER_MIN_HEIGHT,
    COPILOT_HISTORY_POPOVER_WIDTH,
    COPILOT_HISTORY_ROW_DELETE_SIZE,
    COPILOT_HISTORY_ROW_GAP,
    COPILOT_HISTORY_ROW_HEIGHT,
    COPILOT_HISTORY_SHADOW_ALPHA,
    COPILOT_HISTORY_SHADOW_BLUR,
    COPILOT_HISTORY_SHADOW_OFFSET_Y,
    COPILOT_HISTORY_TITLE,
    COPILOT_HISTORY_TITLE_MAX_CHARS,
    COPILOT_HISTORY_VISIBLE_ROWS,
    THEME_CARD_RADIUS,
)
from ai_qa_test_studio.core.chat_models import CopilotSession
from ai_qa_test_studio.ui.qt_compat import graphics_effects_supported
from ai_qa_test_studio.ui.smooth_scroll import apply_pixel_scroll
from ai_qa_test_studio.ui.theme import elevation_channel, get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme

_ROLE_DATA_SESSION_ID = Qt.ItemDataRole.UserRole


class _HistorySessionRow(QWidget):
    """One history line: title + per-row delete."""

    delete_clicked = Signal(str)
    activated = Signal(str)

    def __init__(self, session_id: str, label: str, tooltip: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.session_id = session_id
        self.setObjectName("CopilotHistoryRow")
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self.setFixedHeight(COPILOT_HISTORY_ROW_HEIGHT)

        row = QHBoxLayout(self)
        row.setContentsMargins(8, 0, 4, 0)
        row.setSpacing(4)

        self.title = BodyLabel(label, self)
        self.title.setObjectName("CopilotHistoryRowTitle")
        self.title.setToolTip(tooltip)
        self.title.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.title.setTextInteractionFlags(Qt.TextInteractionFlag.NoTextInteraction)

        self.btn_delete = TransparentToolButton(FIF.DELETE, self)
        self.btn_delete.setFixedSize(COPILOT_HISTORY_ROW_DELETE_SIZE, COPILOT_HISTORY_ROW_DELETE_SIZE)
        self.btn_delete.setToolTip(COPILOT_HISTORY_DELETE_TIP)
        self.btn_delete.setAccessibleName(COPILOT_HISTORY_DELETE_TIP)
        self.btn_delete.clicked.connect(lambda: self.delete_clicked.emit(self.session_id))

        row.addWidget(self.title, stretch=1)
        row.addWidget(self.btn_delete, stretch=0, alignment=Qt.AlignmentFlag.AlignVCenter)
        self.apply_theme()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        if event.button() == Qt.MouseButton.LeftButton:
            # Ignore clicks on the delete control — it handles its own signal.
            child = self.childAt(event.position().toPoint())
            if child is self.btn_delete or (
                child is not None and self.btn_delete.isAncestorOf(child)
            ):
                super().mouseReleaseEvent(event)
                return
            self.activated.emit(self.session_id)
        super().mouseReleaseEvent(event)

    def apply_theme(self, *, selected: bool = False) -> None:
        t = get_theme_tokens()
        bg = t.bg_card_subtle if selected else t.bg_card
        border = t.border_card if selected else "transparent"
        self.setStyleSheet(
            f"#CopilotHistoryRow {{ background-color: {bg};"
            f" border: 1px solid {border}; border-radius: 8px; }}"
        )
        self.title.setStyleSheet(
            f"#CopilotHistoryRowTitle {{ color: {t.text_primary}; background: transparent; }}"
        )


class ChatHistoryRail(QWidget):
    """Popover list of nonempty Copilot sessions; per-row trash deletes that session."""

    session_activated = Signal(str)
    delete_requested = Signal(str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("CopilotHistoryRail")
        self.setAccessibleName(COPILOT_HISTORY_TITLE)
        # Stylesheet background must paint on macOS overlays (else transcript bleeds through).
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self.setFixedWidth(COPILOT_HISTORY_POPOVER_WIDTH)
        self.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Maximum)

        root = QVBoxLayout(self)
        root.setContentsMargins(10, 8, 10, 10)
        root.setSpacing(6)

        header = QWidget(self)
        header.setFixedHeight(COPILOT_HISTORY_HEADER_HEIGHT)
        header_row = QHBoxLayout(header)
        header_row.setContentsMargins(2, 0, 0, 0)
        header_row.setSpacing(2)
        self.title_label = StrongBodyLabel(COPILOT_HISTORY_TITLE, header)
        header_row.addWidget(self.title_label, stretch=1)
        root.addWidget(header)

        self.empty_label = BodyLabel(COPILOT_HISTORY_EMPTY, self)
        self.empty_label.setWordWrap(True)
        self.empty_label.setAlignment(Qt.AlignmentFlag.AlignTop | Qt.AlignmentFlag.AlignLeft)
        self.empty_label.setMinimumHeight(COPILOT_HISTORY_EMPTY_HEIGHT)
        root.addWidget(self.empty_label)

        self.list = QListWidget(self)
        self.list.setObjectName("CopilotHistoryList")
        self.list.setAccessibleName(COPILOT_HISTORY_TITLE)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.list.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.list.setSpacing(COPILOT_HISTORY_ROW_GAP)
        self.list.setUniformItemSizes(True)
        self.list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        apply_pixel_scroll(self.list)
        self.list.currentItemChanged.connect(self._on_current_item_changed)
        root.addWidget(self.list, stretch=0)

        self._shadow: QGraphicsDropShadowEffect | None = None
        if graphics_effects_supported():
            shadow = QGraphicsDropShadowEffect(self)
            shadow.setBlurRadius(COPILOT_HISTORY_SHADOW_BLUR)
            shadow.setOffset(0, COPILOT_HISTORY_SHADOW_OFFSET_Y)
            self.setGraphicsEffect(shadow)
            self._shadow = shadow

        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)
        self._apply_theme_style()
        self.set_sessions([], active_id=None)

    def preferred_popover_height(self, *, max_height: int) -> int:
        """Content-sized height capped by ``max_height`` (not full host height)."""
        layout = self.layout()
        if layout is not None:
            margins = layout.contentsMargins()
            pad_v = margins.top() + margins.bottom()
            spacing = layout.spacing()
        else:
            pad_v = 18
            spacing = 6
        header = COPILOT_HISTORY_HEADER_HEIGHT + spacing
        count = self.list.count()
        if count <= 0:
            body = COPILOT_HISTORY_EMPTY_HEIGHT
        else:
            visible = min(count, COPILOT_HISTORY_VISIBLE_ROWS)
            body = (
                visible * COPILOT_HISTORY_ROW_HEIGHT
                + max(0, visible - 1) * COPILOT_HISTORY_ROW_GAP
            )
        raw = pad_v + header + body
        capped = min(max(raw, COPILOT_HISTORY_POPOVER_MIN_HEIGHT), max_height)
        return min(capped, COPILOT_HISTORY_POPOVER_MAX_HEIGHT)

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        palette = self.palette()
        palette.setColor(QPalette.ColorRole.Window, QColor(t.bg_card))
        palette.setColor(QPalette.ColorRole.Base, QColor(t.bg_card))
        self.setPalette(palette)
        self.setStyleSheet(
            f"#CopilotHistoryRail {{ background-color: {t.bg_card};"
            f" border: 1px solid {t.border_card};"
            f" border-radius: {THEME_CARD_RADIUS}px; }}"
        )
        self.setAutoFillBackground(True)
        if self._shadow is not None:
            channel = elevation_channel()
            self._shadow.setColor(
                QColor(channel, channel, channel, COPILOT_HISTORY_SHADOW_ALPHA)
            )
        self.list.setStyleSheet(
            f"QListWidget {{ background-color: {t.bg_card}; border: none; outline: none; }}"
            f"QListWidget::item {{ margin: 0; padding: 0; border: none; background: transparent; }}"
            f"QListWidget::item:selected {{ background: transparent; }}"
            f"QListWidget::item:hover {{ background: transparent; }}"
        )
        self.empty_label.setStyleSheet(
            f"color: {t.text_muted}; background-color: {t.bg_card}; padding: 8px 4px;"
        )
        self.title_label.setStyleSheet(
            f"color: {t.text_primary}; background-color: {t.bg_card}; padding-left: 2px;"
            " font-weight: 600;"
        )
        current = self.list.currentItem()
        for index in range(self.list.count()):
            item = self.list.item(index)
            row = self.list.itemWidget(item) if item is not None else None
            if isinstance(row, _HistorySessionRow):
                row.apply_theme(selected=item is current)

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
        active_item: QListWidgetItem | None = None
        for session in sessions:
            label = self._row_label(session, running=session.id in running, unread=session.id in unread)
            tip = (session.title or "").strip() or "Phiên mới"
            item = QListWidgetItem()
            item.setData(_ROLE_DATA_SESSION_ID, session.id)
            item.setSizeHint(QSize(COPILOT_HISTORY_POPOVER_WIDTH - 24, COPILOT_HISTORY_ROW_HEIGHT))
            self.list.addItem(item)
            row = _HistorySessionRow(session.id, label, tip, self.list)
            row.delete_clicked.connect(self.delete_requested.emit)
            row.activated.connect(self._activate_session)
            self.list.setItemWidget(item, row)
            if active_id and session.id == active_id:
                active_item = item
        if active_item is not None:
            self.list.setCurrentItem(active_item)
        self.list.blockSignals(False)
        self._sync_row_selection_styles()
        has_rows = self.list.count() > 0
        self.list.setVisible(has_rows)
        self.empty_label.setVisible(not has_rows)
        if has_rows:
            visible = min(self.list.count(), COPILOT_HISTORY_VISIBLE_ROWS)
            self.list.setFixedHeight(
                visible * COPILOT_HISTORY_ROW_HEIGHT
                + max(0, visible - 1) * COPILOT_HISTORY_ROW_GAP
            )
        else:
            self.list.setFixedHeight(0)

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
        # Collapse whitespace / newlines from pasted prompts so rows stay one line.
        title = " ".join(title.split())
        if len(title) > COPILOT_HISTORY_TITLE_MAX_CHARS:
            title = title[: COPILOT_HISTORY_TITLE_MAX_CHARS - 1].rstrip() + "…"
        return f"{prefix}{title}"

    def _activate_session(self, session_id: str) -> None:
        for index in range(self.list.count()):
            item = self.list.item(index)
            if item is not None and item.data(_ROLE_DATA_SESSION_ID) == session_id:
                self.list.setCurrentItem(item)
                break
        self.session_activated.emit(session_id)

    def _on_current_item_changed(
        self, current: QListWidgetItem | None, _previous: QListWidgetItem | None
    ) -> None:
        del current
        self._sync_row_selection_styles()

    def _sync_row_selection_styles(self) -> None:
        current = self.list.currentItem()
        for index in range(self.list.count()):
            item = self.list.item(index)
            if item is None:
                continue
            row = self.list.itemWidget(item)
            if isinstance(row, _HistorySessionRow):
                row.apply_theme(selected=item is current)
