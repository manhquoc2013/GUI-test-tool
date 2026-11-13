"""CDP screencast canvas viewport with Launch / Inspect chrome — no QtWebEngine."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QImage, QPixmap
from PySide6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QPushButton,
    QSizePolicy,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from ai_qa_test_studio.browser.visual_inspector import ViewportMapping, widget_to_css_point
from ai_qa_test_studio.constants import UI_VIEWPORT_HEIGHT, UI_VIEWPORT_WIDTH
from ai_qa_test_studio.ui.icons import inspect_icon, launch_icon


class ScreencastViewport(QWidget):
    clicked_css = Signal(float, float)
    launch_requested = Signal()
    inspect_toggled = Signal(bool)

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self.setAccessibleName("Khung trình duyệt nhúng")
        self._pixmap = QPixmap()
        self._mapping = ViewportMapping(0, 0, 1, 1, 1, 1)
        self._inspecting = False
        self._has_frame = False

        self.btn_inspect = QPushButton("Inspect Element")
        self.btn_inspect.setIcon(inspect_icon())
        self.btn_inspect.setCheckable(True)
        self.btn_inspect.setAccessibleName("Bật chế độ Inspect Element")
        self.btn_inspect.toggled.connect(self._on_inspect_toggled)

        self.btn_launch = QPushButton("Launch Browser")
        self.btn_launch.setIcon(launch_icon())
        self.btn_launch.setObjectName("primary")
        self.btn_launch.setAccessibleName("Mở trình duyệt")
        self.btn_launch.clicked.connect(self.launch_requested.emit)

        self.resolution = QLabel(f"{UI_VIEWPORT_WIDTH}×{UI_VIEWPORT_HEIGHT}")
        self.resolution.setObjectName("muted")

        chrome = QHBoxLayout()
        chrome.setSpacing(8)
        chrome.addWidget(self.btn_inspect)
        chrome.addWidget(self.btn_launch)
        chrome.addStretch(1)
        chrome.addWidget(self.resolution)

        self.label = QLabel()
        self.label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.label.setMinimumHeight(240)
        self.label.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.label.setScaledContents(False)
        self.label.setAccessibleName("Screencast")
        self.label.mousePressEvent = self._on_click  # type: ignore[method-assign]

        self.empty = QWidget()
        empty_layout = QVBoxLayout(self.empty)
        empty_layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        empty_hint = QLabel("Chưa mở trình duyệt")
        empty_hint.setObjectName("muted")
        empty_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.empty_cta = QPushButton("Mở trình duyệt để Inspect / Chạy thử")
        self.empty_cta.setObjectName("primary")
        self.empty_cta.setIcon(launch_icon())
        self.empty_cta.setAccessibleName("Mở trình duyệt để Inspect hoặc chạy thử")
        self.empty_cta.clicked.connect(self.launch_requested.emit)
        empty_layout.addWidget(empty_hint)
        empty_layout.addWidget(self.empty_cta)

        self.stack = QStackedWidget()
        self.stack.addWidget(self.empty)
        self.stack.addWidget(self.label)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)
        layout.addLayout(chrome)
        layout.addWidget(self.stack, stretch=1)
        self.set_placeholder("")

    @property
    def inspect_mode(self) -> bool:
        return self._inspecting

    def _on_inspect_toggled(self, checked: bool) -> None:
        self._inspecting = checked
        self.inspect_toggled.emit(checked)
        if checked:
            self.btn_inspect.setText("Inspect ON")
        else:
            self.btn_inspect.setText("Inspect Element")

    def set_placeholder(self, text: str) -> None:
        self._pixmap = QPixmap()
        self._has_frame = False
        self.label.setPixmap(QPixmap())
        self.label.setText(text)
        self.stack.setCurrentWidget(self.empty)

    def set_frame_png(self, png_bytes: bytes, viewport_w: int, viewport_h: int) -> None:
        image = QImage.fromData(png_bytes)
        if image.isNull():
            self.set_placeholder("Không đọc được khung hình")
            return
        self._pixmap = QPixmap.fromImage(image)
        self._has_frame = True
        self._update_mapping(viewport_w, viewport_h)
        self.resolution.setText(f"{viewport_w}×{viewport_h}")
        self.label.setText("")
        self.label.setPixmap(
            self._pixmap.scaled(
                self.label.size(),
                Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            )
        )
        self.stack.setCurrentWidget(self.label)

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
        if not self._has_frame or self._pixmap.isNull():
            return
        if not self._inspecting:
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
