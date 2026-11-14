"""QA Copilot — Airy chat chrome: context row, thin pivot, 2×2 starters, composer."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import QEvent, Qt, Signal
from PySide6.QtGui import QGuiApplication, QKeyEvent
from PySide6.QtWidgets import (
    QGridLayout,
    QHBoxLayout,
    QScrollArea,
    QSizePolicy,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    ComboBox,
    FlowLayout,
    PillPushButton,
    PlainTextEdit,
    PrimaryPushButton,
    PrimaryToolButton,
    PushButton,
    SegmentedWidget,
    ToolButton,
    TransparentToolButton,
)
from qfluentwidgets import FluentIcon as FIF

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COPILOT_BTN_SEND_SIZE,
    COPILOT_CHIP_HEIGHT,
    COPILOT_INPUT_HEIGHT,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    COPILOT_MODEL_AUTO,
    COPILOT_MODEL_COMBO_HEIGHT,
    COPILOT_STARTER_PROMPTS,
    ROLE_COMPOSER,
    ROLE_CREW,
    ROLE_SYSTEM,
    THEME_BG_SURFACE,
    THEME_BORDER_SUBTLE,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel


class CopilotChatPane(QWidget):
    prompt_submitted = Signal(str, dict)
    stop_requested = Signal()
    apply_requested = Signal(dict)
    session_changed = Signal()
    info_message = Signal(str, str)
    settings_requested = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("AI Copilot Chat")
        self._busy = False
        self._session = CopilotSession(mode=COPILOT_MODE_CASE)
        self._streaming_row: ChatMessageRow | None = None
        self._pending_apply: dict[str, Any] | None = None
        self._rows: list[ChatMessageRow] = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        context_wrap = QWidget(self)
        context = FlowLayout(context_wrap, needAni=False)
        context.setContentsMargins(0, 0, 0, 0)
        context.setHorizontalSpacing(6)
        context.setVerticalSpacing(6)
        self.model_combo = ComboBox(self)
        self.model_combo.setFixedHeight(COPILOT_MODEL_COMBO_HEIGHT)
        self.model_combo.setMinimumWidth(96)
        self.model_combo.setMaximumWidth(160)
        self.model_combo.setAccessibleName("Chọn model Copilot")
        self.set_model_catalog([(COPILOT_MODEL_AUTO, "Auto (theo tác vụ)")])
        context.addWidget(self.model_combo)

        self.chip_case = PillPushButton("@Case", self)
        self.chip_case.setCheckable(True)
        self.chip_case.setChecked(True)
        self.chip_case.setFixedHeight(COPILOT_CHIP_HEIGHT)
        self.chip_dom = PillPushButton("@DOM", self)
        self.chip_dom.setCheckable(True)
        self.chip_dom.setChecked(False)
        self.chip_dom.setFixedHeight(COPILOT_CHIP_HEIGHT)
        self.chip_error = PillPushButton("@Error", self)
        self.chip_error.setCheckable(True)
        self.chip_error.setChecked(False)
        self.chip_error.setFixedHeight(COPILOT_CHIP_HEIGHT)
        for chip in (self.chip_case, self.chip_dom, self.chip_error):
            context.addWidget(chip)

        self.btn_settings = TransparentToolButton(FIF.SETTING, self)
        self.btn_settings.setFixedSize(28, 28)
        self.btn_settings.setToolTip("System Settings")
        self.btn_settings.clicked.connect(lambda: self.settings_requested.emit())
        self.btn_new = TransparentToolButton(FIF.ADD, self)
        self.btn_new.setFixedSize(28, 28)
        self.btn_new.setToolTip("Phiên chat mới")
        self.btn_new.clicked.connect(self.new_session)
        self.btn_clear = TransparentToolButton(FIF.DELETE, self)
        self.btn_clear.setFixedSize(28, 28)
        self.btn_clear.setToolTip("Xóa tin trong phiên")
        self.btn_clear.clicked.connect(self.clear_messages)
        context.addWidget(self.btn_settings)
        context.addWidget(self.btn_new)
        context.addWidget(self.btn_clear)
        layout.addWidget(context_wrap)

        self.mode_pivot = SegmentedWidget(self)
        self.mode_pivot.setAccessibleName("Chat hoặc AI Wizard trong Copilot")
        self.mode_pivot.addItem(routeKey="chat", text="Chat", icon=FIF.CHAT)
        self.mode_pivot.addItem(routeKey="wizard", text="AI Wizard", icon=FIF.EDUCATION)
        self.mode_pivot.setCurrentItem("chat")
        self.mode_pivot.currentItemChanged.connect(self._on_mode_pivot)
        layout.addWidget(self.mode_pivot)

        self.body_stack = QStackedWidget(self)

        chat_body = QWidget(self.body_stack)
        chat_layout = QVBoxLayout(chat_body)
        chat_layout.setContentsMargins(0, 0, 0, 0)
        chat_layout.setSpacing(8)

        self.starters = QWidget(chat_body)
        self.starters.setAccessibleName("Gợi ý bắt đầu Copilot")
        starters_grid = QGridLayout(self.starters)
        starters_grid.setContentsMargins(0, 0, 0, 0)
        starters_grid.setSpacing(8)
        for idx, (label, prompt) in enumerate(COPILOT_STARTER_PROMPTS):
            btn = PushButton(label, self.starters)
            btn.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
            btn.setMinimumHeight(40)
            btn.setToolTip(prompt)
            btn.clicked.connect(lambda _=False, t=prompt: self._starter_clicked(t))
            starters_grid.addWidget(btn, idx // 2, idx % 2)
        chat_layout.addWidget(self.starters)

        self.transcript_scroll = QScrollArea(chat_body)
        self.transcript_scroll.setWidgetResizable(True)
        self.transcript_scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.transcript_scroll.setStyleSheet(
            f"QScrollArea {{ background: {THEME_BG_SURFACE}; border: 1px solid {THEME_BORDER_SUBTLE};"
            f" border-radius: 8px; }}"
        )
        self.transcript_host = QWidget()
        self.transcript_host.setStyleSheet(f"background: {THEME_BG_SURFACE};")
        self.transcript_layout = QVBoxLayout(self.transcript_host)
        self.transcript_layout.setContentsMargins(6, 6, 6, 6)
        self.transcript_layout.setSpacing(4)
        self.transcript_layout.addStretch(1)
        self.transcript_scroll.setWidget(self.transcript_host)
        self.transcript_scroll.setAccessibleName("Lịch sử hội thoại Copilot")
        chat_layout.addWidget(self.transcript_scroll, stretch=1)

        composer = QHBoxLayout()
        composer.setSpacing(6)
        self.input_box = PlainTextEdit(chat_body)
        self.input_box.setPlaceholderText("Nhập yêu cầu…")
        self.input_box.setFixedHeight(COPILOT_INPUT_HEIGHT)
        self.input_box.setAccessibleName("Ô nhập Copilot")
        self.input_box.installEventFilter(self)
        self.btn_apply = PrimaryPushButton("Apply", chat_body)
        self.btn_apply.setEnabled(False)
        self.btn_apply.setToolTip("Áp dụng artifact vào Editor / Test Data")
        self.btn_apply.clicked.connect(self._emit_apply)
        self.btn_send = PrimaryToolButton(FIF.SEND, chat_body)
        self.btn_send.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_send.setToolTip("Gửi")
        self.btn_send.clicked.connect(self._emit_send)
        self.btn_stop = ToolButton(FIF.CANCEL, chat_body)
        self.btn_stop.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_stop.setToolTip("Dừng LLM / orchestrator")
        self.btn_stop.setVisible(False)
        self.btn_stop.clicked.connect(lambda: self.stop_requested.emit())
        composer.addWidget(self.input_box, stretch=1)
        composer.addWidget(self.btn_apply, alignment=Qt.AlignmentFlag.AlignBottom)
        composer.addWidget(self.btn_send, alignment=Qt.AlignmentFlag.AlignBottom)
        composer.addWidget(self.btn_stop, alignment=Qt.AlignmentFlag.AlignBottom)
        chat_layout.addLayout(composer)

        self.wizard = ComposeWizardPanel(self.body_stack)
        self.wizard.drafts_ready.connect(self._on_wizard_drafts)
        self.wizard.cancelled.connect(self.focus_case_chat)

        self.body_stack.addWidget(chat_body)
        self.body_stack.addWidget(self.wizard)
        layout.addWidget(self.body_stack, stretch=1)

        # Compat aliases (legacy chrome removed)
        self.history = self.transcript_scroll
        self.btn_copy = self.btn_settings
        self.btn_undo = self.btn_clear

    @property
    def session(self) -> CopilotSession:
        return self._session

    @property
    def is_busy(self) -> bool:
        return self._busy

    def eventFilter(self, obj: object, event: object) -> bool:
        if obj is self.input_box and isinstance(event, QKeyEvent) and event.type() == QEvent.Type.KeyPress:
            if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter) and not (
                event.modifiers() & Qt.KeyboardModifier.ShiftModifier
            ):
                self._emit_send()
                return True
        return super().eventFilter(obj, event)  # type: ignore[arg-type]

    def resizeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        super().resizeEvent(event)
        narrow = self.width() < 380
        for chip, full in (
            (self.chip_case, "@Case"),
            (self.chip_dom, "@DOM"),
            (self.chip_error, "@Error"),
        ):
            chip.setText("" if narrow else full)
            chip.setToolTip(full)
        self.model_combo.setMaximumWidth(110 if narrow else 160)

    def _on_mode_pivot(self, key: str) -> None:
        if key == "wizard":
            self.focus_compose()
        else:
            self.focus_case_chat()

    def set_busy(self, busy: bool) -> None:
        self._busy = busy
        self.btn_send.setVisible(not busy)
        self.btn_stop.setVisible(busy)
        self.input_box.setReadOnly(busy)

    def focus_compose(self) -> None:
        self._session.mode = COPILOT_MODE_COMPOSE
        self.chip_case.setChecked(False)
        self.mode_pivot.setCurrentItem("wizard")
        self.body_stack.setCurrentWidget(self.wizard)
        self.wizard.reset()

    def focus_case_chat(self) -> None:
        self._session.mode = COPILOT_MODE_CASE
        self.chip_case.setChecked(True)
        self.mode_pivot.setCurrentItem("chat")
        self.body_stack.setCurrentIndex(0)
        self.input_box.setPlaceholderText("Nhập yêu cầu…")
        self.input_box.setFocus()

    def new_session(self) -> None:
        mode = self._session.mode
        self._session = CopilotSession(mode=mode)
        self._clear_transcript()
        self._pending_apply = None
        self.btn_apply.setEnabled(False)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def clear_messages(self) -> None:
        self._session.messages.clear()
        self._clear_transcript()
        self._pending_apply = None
        self.btn_apply.setEnabled(False)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def load_session(self, session: CopilotSession) -> None:
        self._session = session
        self._clear_transcript()
        for msg in session.messages:
            self._append_row(msg.id, msg.role, msg.content)
        self.starters.setVisible(len(session.messages) == 0)
        self.focus_case_chat()
        self.session_changed.emit()

    def set_model_catalog(self, items: list[tuple[str, str]]) -> None:
        current = self.selected_model_ref()
        self.model_combo.blockSignals(True)
        self.model_combo.clear()
        for ref, label in items:
            self.model_combo.addItem(label, ref)
        self.model_combo.blockSignals(False)
        if not items:
            self.model_combo.addItem("Auto (theo tác vụ)", COPILOT_MODEL_AUTO)
        idx = max(0, self.model_combo.findData(current))
        self.model_combo.setCurrentIndex(idx)

    def selected_model_ref(self) -> str:
        data = self.model_combo.currentData()
        if isinstance(data, str) and data:
            return data
        return COPILOT_MODEL_AUTO

    def context_flags(self) -> dict[str, Any]:
        return {
            "model": self.selected_model_ref(),
            "mode": self._session.mode,
            "include_case": self.chip_case.isChecked(),
            "include_dom": self.chip_dom.isChecked(),
            "include_last_error": self.chip_error.isChecked(),
        }

    def append_status_summary(self, role: str, text: str) -> None:
        self._streaming_row = None
        msg = CopilotMessage(role=role, content=text)
        self._session.messages.append(msg)
        self._append_row(msg.id, role, text)
        self.session_changed.emit()

    def append_agent_message(self, from_role: str, to_role: str, text: str) -> None:
        body = f"{from_role} → {to_role}: {text}"
        msg = CopilotMessage(role=ROLE_CREW, content=body)
        self._session.messages.append(msg)
        self._append_row(msg.id, ROLE_CREW, body)
        self.session_changed.emit()

    def append_clarify_note(self, role: str, text: str) -> None:
        body = f"Clarify ({role}): {text}"
        msg = CopilotMessage(role=ROLE_SYSTEM, content=body)
        self._session.messages.append(msg)
        self._append_row(msg.id, ROLE_SYSTEM, body)
        self.session_changed.emit()

    def append_user(self, text: str) -> None:
        msg = CopilotMessage(role="user", content=text)
        self._session.messages.append(msg)
        self._append_row(msg.id, "user", text)
        self.starters.setVisible(False)
        if self._session.title == "Phiên mới":
            self._session.title = text[:48] or "Phiên mới"
        self.session_changed.emit()

    def begin_role(self, role: str) -> None:
        msg = CopilotMessage(role=role, content="")
        self._session.messages.append(msg)
        self._streaming_row = self._append_row(msg.id, role, "")
        self.session_changed.emit()

    def append_stream_chunk(self, chunk: str) -> None:
        if self._streaming_row is None:
            self.begin_role(ROLE_COMPOSER)
        assert self._streaming_row is not None
        current = self._streaming_row._text + chunk
        self._streaming_row.set_body(current)
        if self._session.messages:
            self._session.messages[-1].content = current
        self._scroll_transcript()

    def end_role(self, role: str, text: str) -> None:
        if self._session.messages and self._session.messages[-1].role == role:
            self._session.messages[-1].content = text
        if self._streaming_row is not None:
            self._streaming_row.set_body(text)
        self._streaming_row = None
        self.session_changed.emit()

    def mark_stopped(self, role: str | None = None) -> None:
        del role
        suffix = "\n\n— Đã dừng —"
        if self._streaming_row is not None:
            self._streaming_row.set_body(self._streaming_row._text + suffix)
            self._streaming_row = None
        if self._session.messages:
            self._session.messages[-1].content += suffix
            self._session.messages[-1].status = "stopped"
        self.set_busy(False)
        self.session_changed.emit()

    def set_apply_payload(self, payload: dict[str, Any] | None) -> None:
        self._pending_apply = payload
        self.btn_apply.setEnabled(bool(payload))

    def copy_selected(self) -> None:
        if not self._rows:
            return
        text = self._rows[-1]._text
        QGuiApplication.clipboard().setText(text)
        self.info_message.emit("Đã copy", "Nội dung tin nhắn đã vào clipboard.")

    def undo_last_user(self) -> None:
        self._undo_session(self._session.undo_last_user_turn())

    def _on_copy_row(self, text: str) -> None:
        QGuiApplication.clipboard().setText(text)
        self.info_message.emit("Đã copy", "Nội dung tin nhắn đã vào clipboard.")

    def _on_undo_row(self, message_id: str) -> None:
        if self._busy:
            self.stop_requested.emit()
        self._undo_session(self._session.undo_from_message_id(message_id))

    def _undo_session(self, text: str | None) -> None:
        if text is None:
            return
        self._rebuild_from_session()
        self.input_box.setPlainText(text)
        self.focus_case_chat()
        self._pending_apply = None
        self.btn_apply.setEnabled(False)
        self.starters.setVisible(len(self._session.messages) == 0)
        self.session_changed.emit()

    def _on_wizard_drafts(self, scenarios: list[TestScenario], start_url: str) -> None:
        typed = [s for s in scenarios if isinstance(s, TestScenario)]
        if not typed:
            self.info_message.emit("AI Wizard", "Không có scenario nào được chọn.")
            return
        payload = {
            "kind": APPLY_KIND_COMPOSE,
            "scenarios": [s.model_dump(mode="json") for s in typed],
            "start_url": start_url,
        }
        self.set_apply_payload(payload)
        self.apply_requested.emit(payload)
        self.append_user(f"[AI Wizard] Tạo draft {len(typed)} scenario")
        self.begin_role(ROLE_COMPOSER)
        summary = "\n".join(f"- {s.code}: {s.title} ({len(s.cases)} cases)" for s in typed)
        self.end_role(ROLE_COMPOSER, f"Đã Apply draft UNBOUND:\n{summary}")
        self.focus_case_chat()
        self.info_message.emit("AI Wizard", f"Đã Apply {len(typed)} scenario vào Explorer.")

    def _starter_clicked(self, text: str) -> None:
        if "SRS" in text or "kịch bản" in text.lower() or "Assertions" in text:
            self.focus_compose()
            return
        self.input_box.setPlainText(text)
        self._emit_send()

    def _emit_send(self) -> None:
        if self._busy:
            return
        text = self.input_box.toPlainText().strip()
        if not text:
            return
        self.input_box.clear()
        self.append_user(text)
        self.prompt_submitted.emit(text, self.context_flags())

    def _emit_apply(self) -> None:
        if self._pending_apply:
            self.apply_requested.emit(dict(self._pending_apply))

    def _clear_transcript(self) -> None:
        self._streaming_row = None
        while self._rows:
            row = self._rows.pop()
            self.transcript_layout.removeWidget(row)
            row.deleteLater()

    def _rebuild_from_session(self) -> None:
        self._clear_transcript()
        for msg in self._session.messages:
            self._append_row(msg.id, msg.role, msg.content)

    def _append_row(self, message_id: str, role: str, text: str) -> ChatMessageRow:
        row = ChatMessageRow(message_id, role, text, self.transcript_host)
        row.copy_clicked.connect(self._on_copy_row)
        row.undo_clicked.connect(self._on_undo_row)
        stretch = self.transcript_layout.count() - 1
        self.transcript_layout.insertWidget(max(0, stretch), row)
        self._rows.append(row)
        self._scroll_transcript()
        return row

    def _scroll_transcript(self) -> None:
        bar = self.transcript_scroll.verticalScrollBar()
        bar.setValue(bar.maximum())
