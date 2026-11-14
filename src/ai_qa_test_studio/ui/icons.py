"""Small drawn icons for chrome that FluentIcon does not cover."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPixmap

from ai_qa_test_studio.constants import (
    COPILOT_BTN_SEND_SIZE,
    COPILOT_STOP_ICON_INSET_RATIO,
    COPILOT_STOP_ICON_RADIUS_RATIO,
)


def make_stop_icon(
    *,
    color: str | QColor,
    size: int = COPILOT_BTN_SEND_SIZE,
) -> QIcon:
    """Filled rounded square — media-stop glyph (not undo/cancel)."""
    side = max(12, int(size))
    pixmap = QPixmap(side, side)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor(color) if isinstance(color, str) else color)
    inset = max(2, int(side * COPILOT_STOP_ICON_INSET_RATIO))
    radius = max(1, int(side * COPILOT_STOP_ICON_RADIUS_RATIO))
    painter.drawRoundedRect(inset, inset, side - 2 * inset, side - 2 * inset, radius, radius)
    painter.end()
    return QIcon(pixmap)
