"""CDP screencast canvas viewport — no QtWebEngine."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QImage, QPixmap
from PySide6.QtWidgets import QLabel, QSizePolicy, QVBoxLayout, QWidget

from ai_qa_test_studio.browser.visual_inspector import ViewportMapping, widget_to_css_point


class ScreencastViewport(QWidget):
    clicked_css = Signal(float, float)

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self._pixmap = QPixmap()
        self._mapping = ViewportMapping(0, 0, 1, 1, 1, 1)
        self.label = QLabel("Chưa có khung hình — bấm Run / Sync URL để mở trình duyệt")
        self.label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.label.setMinimumHeight(240)
        self.label.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.label.setScaledContents(False)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.label)
        self.label.mousePressEvent = self._on_click  # type: ignore[method-assign]

    def set_placeholder(self, text: str) -> None:
        self._pixmap = QPixmap()
        self.label.setPixmap(QPixmap())
        self.label.setText(text)

    def set_frame_png(self, png_bytes: bytes, viewport_w: int, viewport_h: int) -> None:
        image = QImage.fromData(png_bytes)
        if image.isNull():
            self.set_placeholder("Không đọc được khung hình")
            return
        self._pixmap = QPixmap.fromImage(image)
        self._update_mapping(viewport_w, viewport_h)
        self.label.setText("")
        self.label.setPixmap(
            self._pixmap.scaled(
                self.label.size(),
                Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            )
        )

    def resizeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        super().resizeEvent(event)
        if not self._pixmap.isNull():
            self.label.setPixmap(
                self._pixmap.scaled(
                    self.label.size(),
                    Qt.AspectRatioMode.KeepAspectRatio,
                    Qt.TransformationMode.SmoothTransformation,
                )
            )
            self._update_mapping(
                int(self._mapping.viewport_width),
                int(self._mapping.viewport_height),
            )

    def _update_mapping(self, viewport_w: int, viewport_h: int) -> None:
        area = self.label.size()
        if self._pixmap.isNull() or viewport_w <= 0 or viewport_h <= 0:
            return
        scale = min(
            area.width() / max(self._pixmap.width(), 1),
            area.height() / max(self._pixmap.height(), 1),
        )
        rendered_w = self._pixmap.width() * scale
        rendered_h = self._pixmap.height() * scale
        offset_x = (area.width() - rendered_w) / 2
        offset_y = (area.height() - rendered_h) / 2
        self._mapping = ViewportMapping(
            offset_x=offset_x,
            offset_y=offset_y,
            rendered_width=max(rendered_w, 1.0),
            rendered_height=max(rendered_h, 1.0),
            viewport_width=float(viewport_w),
            viewport_height=float(viewport_h),
        )

    def _on_click(self, event) -> None:  # type: ignore[no-untyped-def]
        if self._pixmap.isNull():
            return
        try:
            css_x, css_y = widget_to_css_point(
                event.position().x(),
                event.position().y(),
                self._mapping,
            )
        except ValueError:
            return
        self.clicked_css.emit(css_x, css_y)
