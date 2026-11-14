"""Shared title, action badge, and unbound alert widgets (v4.2)."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QFontMetrics
from PySide6.QtWidgets import QHBoxLayout, QLabel, QSizePolicy, QVBoxLayout, QWidget
from qfluentwidgets import (
    CardWidget,
    PrimaryPushButton,
    TransparentToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    UI_ACTION_BADGE_HEIGHT,
    UI_ACTION_BADGE_WIDTH,
    UI_TITLE_FONT_SIZE_PX,
    UI_TITLE_MIN_WIDTH,
    UI_UNBOUND_BANNER_BODY,
    UI_UNBOUND_BANNER_TITLE,
)
from ai_qa_test_studio.ui.theme import action_badge_colors, get_theme_tokens, text_primary_css
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme

_ASSERT_KEYS = frozenset({"assert", "assert_visible", "assert_text", "assert_url"})


class ElidedTitleLabel(QLabel):
    """Single-line title that elides with tooltip of the full text."""

    def __init__(self, text: str = "", parent: QWidget | None = None) -> None:
        super().__init__(text, parent)
        self._full_text = text
        self.setToolTip(text)
        self.setMinimumWidth(UI_TITLE_MIN_WIDTH)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _apply_theme_style(self) -> None:
        self.setStyleSheet(
            f"color: {text_primary_css()};"
            f"font-size: {UI_TITLE_FONT_SIZE_PX}px; font-weight: 600;"
            "background: transparent;"
        )

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
    """Colored action chip for Steps table cells (contrast-aware Dark/Light)."""

    def __init__(self, action: str, parent: QWidget | None = None) -> None:
        super().__init__(action, parent)
        key = action.lower()
        self._action_key = "assert" if key in _ASSERT_KEYS else key
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedSize(UI_ACTION_BADGE_WIDTH, UI_ACTION_BADGE_HEIGHT)
        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _apply_theme_style(self) -> None:
        bg, fg = action_badge_colors(self._action_key)
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

        # Plain QLabel — Fluent BodyLabel/CaptionLabel get opaque slabs when the
        # CardWidget setStyleSheet overrides app QSS for descendants.
        self._icon_lbl = QLabel("!", self)
        self._icon_lbl.setObjectName("UnboundIcon")
        self._icon_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._icon_lbl.setFixedSize(22, 22)
        row.addWidget(self._icon_lbl, 0, Qt.AlignmentFlag.AlignVCenter)

        col = QVBoxLayout()
        col.setSpacing(3)
        self._title = QLabel(self._title_text(), self)
        self._title.setObjectName("UnboundTitle")
        self._title.setWordWrap(True)
        self._desc = QLabel(UI_UNBOUND_BANNER_BODY, self)
        self._desc.setObjectName("UnboundDesc")
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
        t = get_theme_tokens()
        self.setStyleSheet(
            "#UnboundAlertBanner {"
            f"background-color: {t.warning_bg};"
            f"border: 1px solid {t.warning_border};"
            "border-radius: 10px;"
            "}"
            "#UnboundIcon {"
            f"background-color: {t.warning_fg};"
            "color: #FFFFFF;"
            "border: none; border-radius: 11px; font-weight: 700;"
            "}"
            "#UnboundTitle {"
            "background-color: transparent; border: none;"
            f"color: {t.warning_fg}; font-weight: 600; font-size: 13px;"
            "}"
            "#UnboundDesc {"
            "background-color: transparent; border: none;"
            f"color: {t.warning_desc}; font-size: 12px;"
            "}"
        )
