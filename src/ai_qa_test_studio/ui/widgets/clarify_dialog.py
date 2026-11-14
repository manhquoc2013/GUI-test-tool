"""Non-blocking clarify popup — suggestions + free text + default/skip/timeout."""

from __future__ import annotations

from PySide6.QtCore import QTimer, Signal
from PySide6.QtWidgets import QButtonGroup, QWidget
from qfluentwidgets import CaptionLabel, LineEdit, MessageBoxBase, RadioButton, SubtitleLabel

from ai_qa_test_studio.constants import (
    CLARIFY_DIALOG_CUSTOM_PLACEHOLDER,
    CLARIFY_DIALOG_HEADING,
    CLARIFY_DIALOG_OK,
    CLARIFY_DIALOG_TITLE,
    CLARIFY_DIALOG_USE_DEFAULT,
    CLARIFY_FALLBACK_DEFAULT,
    CLARIFY_OPT_COMPOSE_FULL,
    CLARIFY_OPT_COMPOSE_HAPPY,
    CLARIFY_OPT_EXPLAIN_ONLY,
    CLARIFY_SOURCE_DEFAULT_SKIP,
    CLARIFY_SOURCE_DEFAULT_TIMEOUT,
    CLARIFY_SOURCE_STOPPED,
    CLARIFY_SOURCE_USER,
    CLARIFY_SUGGESTION_MIN_CHARS,
    COPILOT_CLARIFY_TIMEOUT_MS,
    UI_PALETTE_MIN_WIDTH,
)
from ai_qa_test_studio.ui.motion import animate_dialog_show

_FALLBACK_SUGGESTIONS: tuple[str, ...] = (
    CLARIFY_OPT_COMPOSE_HAPPY,
    CLARIFY_OPT_COMPOSE_FULL,
    CLARIFY_OPT_EXPLAIN_ONLY,
)


def sanitize_clarify_suggestions(
    suggestions: list[str] | tuple[str, ...] | None,
    *,
    fallback: tuple[str, ...] | list[str] = _FALLBACK_SUGGESTIONS,
) -> list[str]:
    """Drop blank/invisible labels so radios always show concrete choices."""
    cleaned: list[str] = []
    seen: set[str] = set()
    for raw in suggestions or ():
        if raw is None:
            continue
        label = " ".join(str(raw).split())
        if len(label) < CLARIFY_SUGGESTION_MIN_CHARS:
            continue
        key = label.casefold()
        if key in seen:
            continue
        seen.add(key)
        cleaned.append(label)
    if cleaned:
        return cleaned
    return [str(s) for s in fallback if str(s).strip()]


class ClarifyDialog(MessageBoxBase):
    answered = Signal(str, str)  # answer, source

    def __init__(
        self,
        question: str,
        suggestions: list[str],
        default_answer: str,
        parent: QWidget,
        timeout_ms: int = COPILOT_CLARIFY_TIMEOUT_MS,
        *,
        role: str = "Agents",
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle(CLARIFY_DIALOG_TITLE)
        options = sanitize_clarify_suggestions(suggestions)
        default = " ".join(str(default_answer or "").split())
        if default not in options:
            default = options[0] if options else CLARIFY_FALLBACK_DEFAULT
        self._default = default
        self._resolved = False
        self.widget.setMinimumWidth(UI_PALETTE_MIN_WIDTH)

        heading = CLARIFY_DIALOG_HEADING.format(
            role=(role or "Agents").strip().title() or "Agents"
        )
        self.viewLayout.addWidget(SubtitleLabel(heading, self.widget))
        self.viewLayout.addWidget(CaptionLabel(question, self.widget))

        self._group = QButtonGroup(self.widget)
        self._radios: list[RadioButton] = []
        for idx, suggestion in enumerate(options):
            radio = RadioButton(suggestion, self.widget)
            if suggestion == self._default or (idx == 0 and not self._default):
                radio.setChecked(True)
            self._group.addButton(radio, idx)
            self._radios.append(radio)
            self.viewLayout.addWidget(radio)

        self.custom = LineEdit(self.widget)
        self.custom.setPlaceholderText(CLARIFY_DIALOG_CUSTOM_PLACEHOLDER)
        self.viewLayout.addWidget(self.custom)

        self.yesButton.setText(CLARIFY_DIALOG_OK)
        self.cancelButton.setText(CLARIFY_DIALOG_USE_DEFAULT)
        self.yesButton.clicked.connect(self._on_ok)
        self.cancelButton.clicked.connect(self._on_skip)

        self._timer = QTimer(self)
        self._timer.setSingleShot(True)
        self._timer.timeout.connect(self._on_timeout)
        if timeout_ms > 0:
            self._timer.start(timeout_ms)
        animate_dialog_show(self)

    def _selected(self) -> str:
        custom = str(self.custom.text()).strip()
        if custom:
            return custom
        for radio in self._radios:
            if radio.isChecked():
                return str(radio.text())
        return str(self._default)

    def _emit(self, answer: str, source: str) -> None:
        if self._resolved:
            return
        self._resolved = True
        self._timer.stop()
        self.answered.emit(answer, source)
        self.accept()

    def _on_ok(self) -> None:
        self._emit(self._selected(), CLARIFY_SOURCE_USER)

    def _on_skip(self) -> None:
        self._emit(self._selected() if self._radios else self._default, CLARIFY_SOURCE_DEFAULT_SKIP)

    def _on_timeout(self) -> None:
        self._emit(self._default, CLARIFY_SOURCE_DEFAULT_TIMEOUT)

    def abort_stopped(self) -> None:
        """Close dialog when Copilot Stop wins the clarify race."""
        self._emit(self._default, CLARIFY_SOURCE_STOPPED)
