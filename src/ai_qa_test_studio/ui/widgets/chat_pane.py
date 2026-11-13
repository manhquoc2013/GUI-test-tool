"""QA Copilot — Case Chat + embedded AI Wizard (Compose) in one pane."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import QEvent, Qt, Signal
from PySide6.QtGui import QBrush, QColor, QGuiApplication, QKeyEvent
from PySide6.QtWidgets import QHBoxLayout, QListWidgetItem, QStackedWidget, QVBoxLayout, QWidget
from qfluentwidgets import (
    ComboBox,
    ListWidget,
    PillPushButton,
    PlainTextEdit,
    PrimaryPushButton,
    PrimaryToolButton,
    PushButton,
    SegmentedWidget,
    ToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COPILOT_BTN_SEND_SIZE,
    COPILOT_CHIP_HEIGHT,
    COPILOT_INPUT_HEIGHT,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    COPILOT_MODEL_AUTO,
    COPILOT_MODEL_COMBO_HEIGHT,
    ICON_COLOR_PRIMARY,
    ROLE_COMPOSER,
    ROLE_CREW,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel

_ROLE_LABEL = {
    "user": "Bạn",
    ROLE_PLANNER: "Planner",
    ROLE_COMPOSER: "Composer",
    ROLE_CRITIC: "Critic",
    ROLE_CREW: "Crew",
    ROLE_SYSTEM: "Hệ thống",
    "assistant": "AI",
}


class CopilotChatPane(QWidget):
    prompt_submitted = Signal(str, dict)
    stop_requested = Signal()
    apply_requested = Signal(dict)
    session_changed = Signal()
    info_message = Signal(str, str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("AI Copilot Chat")
        self._busy = False
        self._session = CopilotSession(mode=COPILOT_MODE_CASE)
        self._streaming_item: QListWidgetItem | None = None
        self._pending_apply: dict[str, Any] | None = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 6, 0, 0)
        layout.setSpacing(6)

        top = QHBoxLayout()
        top.setSpacing(6)
        self.model_combo = ComboBox(self)
        self.model_combo.setFixedHeight(COPILOT_MODEL_COMBO_HEIGHT)
        self.model_combo.setAccessibleName("Chọn model Copilot")
        self.set_model_catalog([(COPILOT_MODEL_AUTO, "Auto (theo tác vụ)")])
        top.addWidget(self.model_combo)

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
            top.addWidget(chip)
        top.addStretch(1)

        self.btn_new = ToolButton(FIF.ADD, self)
        self.btn_new.setFixedSize(COPILOT_BTN_SEND_SIZE - 8, COPILOT_BTN_SEND_SIZE - 8)
        self.btn_new.setToolTip("Phiên chat mới")
        self.btn_new.clicked.connect(self.new_session)
        self.btn_clear = ToolButton(FIF.DELETE, self)
        self.btn_clear.setFixedSize(COPILOT_BTN_SEND_SIZE - 8, COPILOT_BTN_SEND_SIZE - 8)
        self.btn_clear.setToolTip("Xóa tin trong phiên")
        self.btn_clear.clicked.connect(self.clear_messages)
        top.addWidget(self.btn_new)
        top.addWidget(self.btn_clear)
        layout.addLayout(top)

        self.mode_pivot = SegmentedWidget(self)
        self.mode_pivot.setAccessibleName("Chat hoặc AI Wizard trong Copilot")
        self.mode_pivot.addItem(routeKey="chat", text="Chat", icon=FIF.CHAT)
        self.mode_pivot.addItem(routeKey="wizard", text="AI Wizard", icon=FIF.EDUCATION)
        self.mode_pivot.setCurrentItem("chat")
        self.mode_pivot.currentItemChanged.connect(self._on_mode_pivot)
        layout.addWidget(self.mode_pivot)

        self.body_stack = QStackedWidget(self)

        # --- Chat body ---
        chat_body = QWidget(self.body_stack)
        chat_layout = QVBoxLayout(chat_body)
        chat_layout.setContentsMargins(0, 0, 0, 0)
        chat_layout.setSpacing(6)

        self.starters = QWidget(chat_body)
        starters_layout = QHBoxLayout(self.starters)
        starters_layout.setContentsMargins(0, 0, 0, 0)
        starters_layout.setSpacing(6)
        for label in (
            "Giải thích lỗi bước vừa chạy",
            "Sinh thêm 5 bộ Test Data biên (BVA)",
            "Tạo kịch bản từ SRS",
            "Chuyển kịch bản sang YAML",
        ):
            btn = PushButton(label, self.starters)
            btn.setFixedHeight(COPILOT_CHIP_HEIGHT + 4)
            btn.clicked.connect(lambda _=False, t=label: self._starter_clicked(t))
            starters_layout.addWidget(btn)
        starters_layout.addStretch(1)
        chat_layout.addWidget(self.starters)

        self.history = ListWidget(chat_body)
        self.history.setWordWrap(True)
        self.history.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.history.setAccessibleName("Lịch sử hội thoại Copilot")
        chat_layout.addWidget(self.history, stretch=1)

        action_row = QHBoxLayout()
        self.btn_copy = PushButton(FIF.COPY, "Copy", chat_body)
        self.btn_copy.setToolTip("Copy tin đang chọn")
        self.btn_copy.clicked.connect(self.copy_selected)
        self.btn_undo = PushButton(FIF.CANCEL, "Undo", chat_body)
        self.btn_undo.setToolTip("Hoàn tác tin user gần nhất → điền lại ô nhập")
        self.btn_undo.clicked.connect(self.undo_last_user)
        self.btn_apply = PrimaryPushButton(FIF.ACCEPT, "Apply", chat_body)
        self.btn_apply.setEnabled(False)
        self.btn_apply.setToolTip("Áp dụng artifact vào Editor / Test Data")
        self.btn_apply.clicked.connect(self._emit_apply)
        action_row.addWidget(self.btn_copy)
        action_row.addWidget(self.btn_undo)
        action_row.addStretch(1)
        action_row.addWidget(self.btn_apply)
        chat_layout.addLayout(action_row)

        input_row = QHBoxLayout()
        input_row.setSpacing(6)
        self.input_box = PlainTextEdit(chat_body)
        self.input_box.setPlaceholderText("Nhập yêu cầu (Enter gửi, Shift+Enter xuống dòng)...")
        self.input_box.setFixedHeight(COPILOT_INPUT_HEIGHT)
        self.input_box.setAccessibleName("Ô nhập Copilot")
        self.input_box.installEventFilter(self)
        self.btn_send = PrimaryToolButton(FIF.SEND, chat_body)
        self.btn_send.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_send.setToolTip("Gửi")
        self.btn_send.clicked.connect(self._emit_send)
        self.btn_stop = ToolButton(FIF.CANCEL, chat_body)
        self.btn_stop.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_stop.setToolTip("Dừng LLM / orchestrator")
        self.btn_stop.setVisible(False)
        self.btn_stop.clicked.connect(self.stop_requested.emit)
        input_row.addWidget(self.input_box, stretch=1)
        input_row.addWidget(self.btn_send, alignment=Qt.AlignmentFlag.AlignBottom)
        input_row.addWidget(self.btn_stop, alignment=Qt.AlignmentFlag.AlignBottom)
        chat_layout.addLayout(input_row)

        # --- Wizard body (AI Wizard is part of Copilot) ---
        self.wizard = ComposeWizardPanel(self.body_stack)
        self.wizard.drafts_ready.connect(self._on_wizard_drafts)
        self.wizard.cancelled.connect(self.focus_case_chat)

        self.body_stack.addWidget(chat_body)
        self.body_stack.addWidget(self.wizard)
        layout.addWidget(self.body_stack, stretch=1)

    @property
    def session(self) -> CopilotSession:
        return self._session

    def eventFilter(self, obj: object, event: object) -> bool:
        if obj is self.input_box and isinstance(event, QKeyEvent) and event.type() == QEvent.Type.KeyPress:
            if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter) and not (
                event.modifiers() & Qt.KeyboardModifier.ShiftModifier
            ):
                self._emit_send()
                return True
        return super().eventFilter(obj, event)  # type: ignore[arg-type]

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
        """Open AI Wizard (Compose) — the structured half of Copilot."""
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
        self.input_box.setPlaceholderText("Nhập yêu cầu (Enter gửi, Shift+Enter xuống dòng)...")
        self.input_box.setFocus()

    def new_session(self) -> None:
        mode = self._session.mode
        self._session = CopilotSession(mode=mode)
        self.history.clear()
        self._streaming_item = None
        self._pending_apply = None
        self.btn_apply.setEnabled(False)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def clear_messages(self) -> None:
        self._session.messages.clear()
        self.history.clear()
        self._streaming_item = None
        self._pending_apply = None
        self.btn_apply.setEnabled(False)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def load_session(self, session: CopilotSession) -> None:
        self._session = session
        self.history.clear()
        self._streaming_item = None
        for msg in session.messages:
            self._append_item(msg.role, msg.content, streaming=False)
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
        if self._streaming_item is not None:
            self._streaming_item = None
        msg = CopilotMessage(role=role, content=text)
        self._session.messages.append(msg)
        self._append_item(role, text, streaming=False)
        self.session_changed.emit()

    def append_agent_message(self, from_role: str, to_role: str, text: str) -> None:
        label_role = ROLE_CREW
        body = f"{from_role} → {to_role}: {text}"
        msg = CopilotMessage(role=label_role, content=body)
        self._session.messages.append(msg)
        self._append_item(label_role, body, streaming=False)
        self.session_changed.emit()

    def append_clarify_note(self, role: str, text: str) -> None:
        body = f"Clarify ({role}): {text}"
        msg = CopilotMessage(role=ROLE_SYSTEM, content=body)
        self._session.messages.append(msg)
        self._append_item(ROLE_SYSTEM, body, streaming=False)
        self.session_changed.emit()

    def append_user(self, text: str) -> None:
        msg = CopilotMessage(role="user", content=text)
        self._session.messages.append(msg)
        self._append_item("user", text, streaming=False)
        self.starters.setVisible(False)
        if self._session.title == "Phiên mới":
            self._session.title = text[:48] or "Phiên mới"
        self.session_changed.emit()

    def begin_role(self, role: str) -> None:
        msg = CopilotMessage(role=role, content="")
        self._session.messages.append(msg)
        self._streaming_item = self._append_item(role, "", streaming=True)
        self.session_changed.emit()

    def append_stream_chunk(self, chunk: str) -> None:
        if self._streaming_item is None:
            self.begin_role(ROLE_COMPOSER)
        assert self._streaming_item is not None
        role = self._streaming_item.data(Qt.ItemDataRole.UserRole) or ROLE_COMPOSER
        current = self._streaming_item.data(Qt.ItemDataRole.UserRole + 1) or ""
        current = str(current) + chunk
        self._streaming_item.setData(Qt.ItemDataRole.UserRole + 1, current)
        self._streaming_item.setText(self._format_bubble(str(role), current))
        if self._session.messages and self._session.messages[-1].role == role:
            self._session.messages[-1].content = current
        self.history.scrollToBottom()

    def end_role(self, role: str, text: str) -> None:
        if self._session.messages and self._session.messages[-1].role == role:
            self._session.messages[-1].content = text
        if self._streaming_item is not None:
            self._streaming_item.setText(self._format_bubble(role, text))
            self._streaming_item.setData(Qt.ItemDataRole.UserRole + 1, text)
        self._streaming_item = None
        self.session_changed.emit()

    def mark_stopped(self, role: str | None = None) -> None:
        suffix = "\n\n— Đã dừng —"
        if self._streaming_item is not None:
            body = str(self._streaming_item.data(Qt.ItemDataRole.UserRole + 1) or "") + suffix
            r = str(self._streaming_item.data(Qt.ItemDataRole.UserRole) or role or "assistant")
            self._streaming_item.setText(self._format_bubble(r, body))
            self._streaming_item = None
        if self._session.messages:
            self._session.messages[-1].content += suffix
            self._session.messages[-1].status = "stopped"
        self.set_busy(False)
        self.session_changed.emit()

    def set_apply_payload(self, payload: dict[str, Any] | None) -> None:
        self._pending_apply = payload
        self.btn_apply.setEnabled(bool(payload))

    def copy_selected(self) -> None:
        item = self.history.currentItem()
        if item is None and self.history.count():
            item = self.history.item(self.history.count() - 1)
        if item is None:
            return
        text = str(item.data(Qt.ItemDataRole.UserRole + 1) or item.text())
        QGuiApplication.clipboard().setText(text)
        self.info_message.emit("Đã copy", "Nội dung tin nhắn đã vào clipboard.")

    def undo_last_user(self) -> None:
        if self._busy:
            self.stop_requested.emit()
        text = self._session.undo_last_user_turn()
        if text is None:
            return
        self.history.clear()
        self._streaming_item = None
        for msg in self._session.messages:
            self._append_item(msg.role, msg.content, streaming=False)
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
        if "SRS" in text or "kịch bản" in text.lower():
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

    def _append_item(self, role: str, text: str, *, streaming: bool) -> QListWidgetItem:
        item = QListWidgetItem(self._format_bubble(role, text or ("…" if streaming else "")))
        item.setData(Qt.ItemDataRole.UserRole, role)
        item.setData(Qt.ItemDataRole.UserRole + 1, text)
        if role == "user":
            item.setForeground(Qt.GlobalColor.cyan)
        elif role == ROLE_PLANNER:
            item.setForeground(Qt.GlobalColor.yellow)
        elif role == ROLE_CRITIC:
            item.setForeground(Qt.GlobalColor.magenta)
        elif role in {ROLE_CREW, ROLE_SYSTEM}:
            item.setForeground(Qt.GlobalColor.lightGray)
        else:
            item.setForeground(QBrush(QColor(ICON_COLOR_PRIMARY)))
        self.history.addItem(item)
        self.history.scrollToBottom()
        return item

    def _format_bubble(self, role: str, text: str) -> str:
        label = _ROLE_LABEL.get(role, role)
        return f"[{label}] {text}"
