"""Fluent Command Palette — SearchLineEdit + ListWidget (Ctrl/Cmd+K)."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from PySide6.QtCore import Qt
from PySide6.QtGui import QKeySequence, QShortcut
from PySide6.QtWidgets import QListWidgetItem, QWidget
from qfluentwidgets import (
    CaptionLabel,
    ListWidget,
    MessageBoxBase,
    SearchLineEdit,
    SubtitleLabel,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import UI_PALETTE_MAX_HEIGHT, UI_PALETTE_MIN_WIDTH


@dataclass(frozen=True, slots=True)
class PaletteCommand:
    key: str
    title: str
    hint: str
    keywords: tuple[str, ...]
    callback: Callable[[], None]


class CommandPalette(MessageBoxBase):
    def __init__(
        self,
        commands: list[PaletteCommand],
        parent: QWidget,
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle("Command Palette")
        self._commands = list(commands)
        self._filtered: list[PaletteCommand] = list(commands)
        self._chosen: PaletteCommand | None = None

        self.widget.setMinimumWidth(UI_PALETTE_MIN_WIDTH)
        self.widget.setMaximumHeight(UI_PALETTE_MAX_HEIGHT)

        self.title_label = SubtitleLabel("Command Palette", self.widget)
        self.hint = CaptionLabel("Gõ để lọc · Enter để chạy · Esc để đóng", self.widget)

        self.search = SearchLineEdit(self.widget)
        self.search.setPlaceholderText("Tìm hành động (wizard, sync, run, export…)")
        self.search.setClearButtonEnabled(True)
        self.search.textChanged.connect(self._on_filter)

        self.list = ListWidget(self.widget)
        self.list.setMinimumHeight(220)
        self.list.itemActivated.connect(self._on_item_activated)
        self.list.itemDoubleClicked.connect(self._on_item_activated)

        self.viewLayout.addWidget(self.title_label)
        self.viewLayout.addWidget(self.hint)
        self.viewLayout.addWidget(self.search)
        self.viewLayout.addWidget(self.list, stretch=1)

        self.yesButton.setText("Chạy")
        self.cancelButton.setText("Đóng")
        self._rebuild_list(self._commands)

        QShortcut(QKeySequence(Qt.Key.Key_Return), self, self._activate_current)
        QShortcut(QKeySequence(Qt.Key.Key_Enter), self, self._activate_current)
        QShortcut(QKeySequence(Qt.Key.Key_Down), self, self._focus_list)
        self.search.setFocus()

    def chosen(self) -> PaletteCommand | None:
        return self._chosen

    def validate(self) -> bool:
        item = self.list.currentItem()
        if item is None and self.list.count() > 0:
            item = self.list.item(0)
        if item is None:
            return False
        data = item.data(Qt.ItemDataRole.UserRole)
        if not isinstance(data, PaletteCommand):
            return False
        self._chosen = data
        return True

    def _on_filter(self, text: str) -> None:
        kw = text.strip().lower()
        if not kw:
            self._filtered = list(self._commands)
        else:
            self._filtered = [
                cmd
                for cmd in self._commands
                if kw in cmd.title.lower()
                or kw in cmd.hint.lower()
                or kw in cmd.key.lower()
                or any(kw in token for token in cmd.keywords)
            ]
        self._rebuild_list(self._filtered)

    def _rebuild_list(self, commands: list[PaletteCommand]) -> None:
        self.list.clear()
        for cmd in commands:
            item = QListWidgetItem(f"{cmd.title}  —  {cmd.hint}")
            item.setData(Qt.ItemDataRole.UserRole, cmd)
            item.setIcon(FIF.COMMAND_PROMPT.icon())
            self.list.addItem(item)
        if self.list.count() > 0:
            self.list.setCurrentRow(0)

    def _on_item_activated(self, item: QListWidgetItem) -> None:
        data = item.data(Qt.ItemDataRole.UserRole)
        if isinstance(data, PaletteCommand):
            self._chosen = data
            self.accept()

    def _activate_current(self) -> None:
        if self.validate():
            self.accept()

    def _focus_list(self) -> None:
        self.list.setFocus()
        if self.list.count() > 0 and self.list.currentRow() < 0:
            self.list.setCurrentRow(0)
