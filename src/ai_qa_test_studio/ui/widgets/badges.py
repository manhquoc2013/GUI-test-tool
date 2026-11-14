"""Shared title, action badge, and unbound alert widgets (v4.2)."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QFontMetrics
from PySide6.QtWidgets import QHBoxLayout, QLabel, QSizePolicy, QVBoxLayout, QWidget
from qfluentwidgets import (
    CaptionLabel,
    CardWidget,
    PrimaryPushButton,
    StrongBodyLabel,
    TransparentToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    THEME_ACTION_ASSERT_FG,
    THEME_ACTION_CLICK_FG,
    THEME_ACTION_FALLBACK_FG,
    THEME_ACTION_FILL_FG,
    THEME_ACTION_SELECT_FG,
    THEME_STATUS_UNBOUND,
    UI_ACTION_BADGE_HEIGHT,
    UI_ACTION_BADGE_WIDTH,
    UI_TITLE_FONT_SIZE_PX,
    UI_TITLE_MIN_WIDTH,
    UI_UNBOUND_BANNER_BODY,
    UI_UNBOUND_BANNER_TITLE,
)
from ai_qa_test_studio.ui.theme import (
    action_badge_bg_css,
    unbound_banner_bg_css,
    unbound_banner_border_css,
)
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme


class ElidedTitleLabel(QLabel):
    """Single-line title that elides with tooltip of the full text."""

    def __init__(self, text: str = "", parent: QWidget | None = None) -> None:
        super().__init__(text, parent)
        self._full_text = text
        self.setToolTip(text)
        self.setMinimumWidth(UI_TITLE_MIN_WIDTH)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.setStyleSheet(f"font-size: {UI_TITLE_FONT_SIZE_PX}px; font-weight: 600;")
        self.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)

    def setText(self, text: str) -> None:  # type: ignore[override]
        self._full_text = text
        self.setToolTip(text)
        self._update_elided()

    def full_text(self) -> str:
        return self._full_text

    def resizeEvent(self, event: object) -> None:  # type: ignore[override]
        super().resizeEvent(event)  # type: ignore[arg-type]
        self._update_elided()

    def _update_elided(self) -> None:
        fm = QFontMetrics(self.font())
        width = max(UI_TITLE_MIN_WIDTH, self.width())
        elided = fm.elidedText(self._full_text, Qt.TextElideMode.ElideRight, width)
        super().setText(elided)


class ActionBadge(QLabel):
    """Colored action chip for Steps table cells."""

    _STYLES: dict[str, str] = {
        "click": THEME_ACTION_CLICK_FG,
        "fill": THEME_ACTION_FILL_FG,
        "select": THEME_ACTION_SELECT_FG,
        "assert": THEME_ACTION_ASSERT_FG,
        "assert_visible": THEME_ACTION_ASSERT_FG,
        "assert_text": THEME_ACTION_ASSERT_FG,
        "assert_url": THEME_ACTION_ASSERT_FG,
    }

    def __init__(self, action: str, parent: QWidget | None = None) -> None:
        super().__init__(action, parent)
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedSize(UI_ACTION_BADGE_WIDTH, UI_ACTION_BADGE_HEIGHT)
        fg = self._STYLES.get(action.lower(), THEME_ACTION_FALLBACK_FG)
        bg = action_badge_bg_css(fg)
        self.setStyleSheet(
            f"background-color: {bg}; color: {fg}; border-radius: 6px;"
            "font-size: 12px; font-weight: 600;"
        )


class UnboundAlertBanner(CardWidget):
    """Auto-height UNBOUND warning with Sync CTA."""

    sync_clicked = Signal()
    dismissed = Signal()

    def __init__(self, count: int = 0, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("UnboundAlertBanner")
        self.setAccessibleName("Cảnh báo bước UNBOUND")
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self._count = count

        row = QHBoxLayout(self)
        row.setContentsMargins(16, 12, 16, 12)
        row.setSpacing(14)

        icon_lbl = QLabel("!", self)
        icon_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon_lbl.setFixedSize(22, 22)
        icon_lbl.setStyleSheet(
            f"background-color: {THEME_STATUS_UNBOUND}; color: #111827;"
            "border-radius: 11px; font-weight: 700;"
        )
        row.addWidget(icon_lbl, 0, Qt.AlignmentFlag.AlignVCenter)

        col = QVBoxLayout()
        col.setSpacing(3)
        self._title = StrongBodyLabel(self._title_text(), self)
        self._title.setStyleSheet(f"color: {THEME_STATUS_UNBOUND};")
        self._desc = CaptionLabel(UI_UNBOUND_BANNER_BODY, self)
        self._desc.setWordWrap(True)
        col.addWidget(self._title)
        col.addWidget(self._desc)
        row.addLayout(col, 1)

        self.sync_btn = PrimaryPushButton(FIF.SYNC, "Sync ngay", self)
        self.sync_btn.setFixedHeight(32)
        self.sync_btn.clicked.connect(self.sync_clicked.emit)
        close_btn = TransparentToolButton(FIF.CLOSE, self)
        close_btn.setFixedSize(30, 30)
        close_btn.clicked.connect(self._on_close)
        row.addWidget(self.sync_btn, 0, Qt.AlignmentFlag.AlignVCenter)
        row.addWidget(close_btn, 0, Qt.AlignmentFlag.AlignVCenter)

        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def set_count(self, count: int) -> None:
        self._count = count
        self._title.setText(self._title_text())

    def _title_text(self) -> str:
        if self._count > 0:
            return f"Có {self._count} bước chưa gắn selector (UNBOUND)"
        return UI_UNBOUND_BANNER_TITLE

    def _on_close(self) -> None:
        self.hide()
        self.dismissed.emit()

    def _apply_theme_style(self) -> None:
        self.setStyleSheet(
            "UnboundAlertBanner {"
            f"background-color: {unbound_banner_bg_css()};"
            f"border: 1px solid {unbound_banner_border_css()};"
            "border-radius: 10px;"
            "}"
        )
