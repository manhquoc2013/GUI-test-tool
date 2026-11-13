"""Fluent-style multi-step progress header (community substitute for Pro StepProgressBar)."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QHBoxLayout, QLabel, QVBoxLayout, QWidget
from qfluentwidgets import BodyLabel, CaptionLabel, ProgressBar

from ai_qa_test_studio.constants import THEME_ACCENT, THEME_BORDER, THEME_MUTED


class StepProgressBar(QWidget):
    def __init__(self, steps: list[str], parent: QWidget | None = None) -> None:
        super().__init__(parent)
        if not steps:
            raise ValueError("steps must not be empty")
        self._steps = list(steps)
        self._index = 0

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(8)

        self._bar = ProgressBar(self)
        self._bar.setRange(0, max(len(self._steps) - 1, 1))
        self._bar.setValue(0)
        root.addWidget(self._bar)

        chips = QHBoxLayout()
        chips.setSpacing(8)
        self._labels: list[QLabel] = []
        for i, title in enumerate(self._steps):
            label = BodyLabel(f"{i + 1}. {title}", self)
            label.setAlignment(Qt.AlignmentFlag.AlignCenter)
            chips.addWidget(label, stretch=1)
            self._labels.append(label)
        root.addLayout(chips)

        self._caption = CaptionLabel("", self)
        self._caption.setAlignment(Qt.AlignmentFlag.AlignCenter)
        root.addWidget(self._caption)
        self.set_current(0)

    @property
    def current_index(self) -> int:
        return self._index

    @property
    def step_count(self) -> int:
        return len(self._steps)

    def set_current(self, index: int) -> None:
        self._index = max(0, min(index, len(self._steps) - 1))
        self._bar.setValue(self._index)
        for i, label in enumerate(self._labels):
            if i < self._index:
                label.setStyleSheet(f"color: {THEME_ACCENT}; font-weight: 600;")
            elif i == self._index:
                label.setStyleSheet(f"color: {THEME_ACCENT}; font-weight: 700;")
            else:
                label.setStyleSheet(f"color: {THEME_MUTED}; border-color: {THEME_BORDER};")
        self._caption.setText(
            f"Bước {self._index + 1}/{len(self._steps)} — {self._steps[self._index]}"
        )
