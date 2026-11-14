"""QA Copilot — Airy chat chrome: context row, thin pivot, 2×2 starters, composer."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PySide6.QtCore import QEvent, Qt, Signal
from PySide6.QtGui import QGuiApplication, QKeyEvent
from PySide6.QtWidgets import (
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QScrollArea,
    QSizePolicy,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    BodyLabel,
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
    CHAT_TRANSCRIPT_SPACING,
    COPILOT_APPLY_TOOLTIP,
    COPILOT_ATTACH_BTN_LABEL,
    COPILOT_ATTACH_CHIP_HEIGHT,
    COPILOT_ATTACH_MAX_FILES,
    COPILOT_ATTACH_NAME_MAX_CHARS,
    COPILOT_ATTACH_PREVIEW_CHARS,
    COPILOT_BTN_APPLY,
    COPILOT_BTN_DOWNLOAD_EXCEL,
    COPILOT_BTN_SEND_SIZE,
    COPILOT_BUSY_PLACEHOLDER,
    COPILOT_CHIP_CASE,
    COPILOT_CHIP_DOM,
    COPILOT_CHIP_ERROR,
    COPILOT_CHIP_HEIGHT,
    COPILOT_COMPOSER_GAP,
    COPILOT_COMPOSER_PADDING,
    COPILOT_CONTEXT_FLOW_SPACING,
    COPILOT_DOWNLOAD_DIALOG_TITLE,
    COPILOT_DOWNLOAD_DONE_TITLE,
    COPILOT_DOWNLOAD_EMPTY,
    COPILOT_DOWNLOAD_FAIL_TITLE,
    COPILOT_DOWNLOAD_TOOLTIP,
    COPILOT_IDLE_PLACEHOLDER,
    COPILOT_INPUT_HEIGHT,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    COPILOT_MODEL_AUTO,
    COPILOT_MODEL_COMBO_HEIGHT,
    COPILOT_MODEL_COMBO_MAX_WIDTH,
    COPILOT_MODEL_COMBO_MIN_WIDTH,
    COPILOT_MODEL_HINT_AUTO,
    COPILOT_MODEL_LABEL_AUTO,
    COPILOT_STARTER_PROMPTS,
    COPILOT_STATUS_CONNECTING,
    COPILOT_STATUS_MIN_HEIGHT,
    ROLE_COMPOSER,
    ROLE_CREW,
    ROLE_SYSTEM,
    THEME_CARD_RADIUS,
    VN_TC_FILE_FILTER,
    VN_TC_FILENAME_DEFAULT,
    WIZARD_DOC_FILE_FILTER,
    WIZARD_DOC_MARKDOWN_SUFFIXES,
    WIZARD_DOC_MAX_FILE_BYTES,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage, CopilotSession
from ai_qa_test_studio.core.doc_corpus import IngestResult, ingest_path
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.core.token_budget import ContextCompressor
from ai_qa_test_studio.reporting.vn_test_doc_exporter import CopilotArtifactExcelExporter
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel

_ATTACH_STATUS_PENDING = "pending"
_ATTACH_STATUS_READY = "ready"
_ATTACH_STATUS_ERROR = "error"


@dataclass
class ChatAttachment:
    path: Path
    name: str
    markdown: str
    doc_id: str = ""
    index_status: str = _ATTACH_STATUS_PENDING
    index_detail: str = "đang index…"
    total_chunks: int = 0


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
        self._attachments: list[ChatAttachment] = []
        self._attach_compressor = ContextCompressor()
        self._project_root: Path | None = None
        self._ingest_tasks: dict[str, asyncio.Task[IngestResult]] = {}

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        context_row = QWidget(self)
        context_row.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        row = QHBoxLayout(context_row)
        row.setContentsMargins(0, 0, 0, 0)
        row.setSpacing(COPILOT_CONTEXT_FLOW_SPACING)

        pill_box = QWidget(context_row)
        pill_box.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self._context_flow = FlowLayout(pill_box, needAni=False)
        self._context_flow.setContentsMargins(0, 0, 0, 0)
        self._context_flow.setHorizontalSpacing(COPILOT_CONTEXT_FLOW_SPACING)
        self._context_flow.setVerticalSpacing(COPILOT_CONTEXT_FLOW_SPACING)

        self.model_combo = ComboBox(pill_box)
        self.model_combo.setFixedHeight(COPILOT_MODEL_COMBO_HEIGHT)
        self.model_combo.setMinimumWidth(COPILOT_MODEL_COMBO_MIN_WIDTH)
        self.model_combo.setMaximumWidth(COPILOT_MODEL_COMBO_MAX_WIDTH)
        self.model_combo.setAccessibleName("Chọn model Copilot")
        self.model_combo.setToolTip(COPILOT_MODEL_HINT_AUTO)
        self.set_model_catalog([(COPILOT_MODEL_AUTO, COPILOT_MODEL_LABEL_AUTO)])
        self._context_flow.addWidget(self.model_combo)

        self.chip_case = PillPushButton(COPILOT_CHIP_CASE, pill_box)
        self.chip_case.setCheckable(True)
        self.chip_case.setChecked(True)
        self.chip_case.setFixedHeight(COPILOT_CHIP_HEIGHT)
        self.chip_case.setToolTip(COPILOT_CHIP_CASE)
        self.chip_dom = PillPushButton(COPILOT_CHIP_DOM, pill_box)
        self.chip_dom.setCheckable(True)
        self.chip_dom.setChecked(False)
        self.chip_dom.setFixedHeight(COPILOT_CHIP_HEIGHT)
        self.chip_dom.setToolTip(COPILOT_CHIP_DOM)
        self.chip_error = PillPushButton(COPILOT_CHIP_ERROR, pill_box)
        self.chip_error.setCheckable(True)
        self.chip_error.setChecked(False)
        self.chip_error.setFixedHeight(COPILOT_CHIP_HEIGHT)
        self.chip_error.setToolTip(COPILOT_CHIP_ERROR)
        for chip in (self.chip_case, self.chip_dom, self.chip_error):
            self._context_flow.addWidget(chip)

        tools_wrap = QWidget(context_row)
        tools = QHBoxLayout(tools_wrap)
        tools.setContentsMargins(0, 0, 0, 0)
        tools.setSpacing(2)
        self.btn_settings = TransparentToolButton(FIF.SETTING, tools_wrap)
        self.btn_settings.setFixedSize(28, 28)
        self.btn_settings.setToolTip("System Settings")
        self.btn_settings.clicked.connect(lambda: self.settings_requested.emit())
        self.btn_new = TransparentToolButton(FIF.ADD, tools_wrap)
        self.btn_new.setFixedSize(28, 28)
        self.btn_new.setToolTip("Phiên chat mới")
        self.btn_new.clicked.connect(self.new_session)
        self.btn_clear = TransparentToolButton(FIF.DELETE, tools_wrap)
        self.btn_clear.setFixedSize(28, 28)
        self.btn_clear.setToolTip("Xóa tin trong phiên")
        self.btn_clear.clicked.connect(self.clear_messages)
        tools.addWidget(self.btn_settings)
        tools.addWidget(self.btn_new)
        tools.addWidget(self.btn_clear)
        row.addWidget(pill_box, stretch=1)
        row.addWidget(tools_wrap, stretch=0, alignment=Qt.AlignmentFlag.AlignTop)
        self._pill_box = pill_box
        layout.addWidget(context_row)

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
        self.transcript_host = QWidget()
        self.transcript_layout = QVBoxLayout(self.transcript_host)
        self.transcript_layout.setContentsMargins(8, 8, 8, 8)
        self.transcript_layout.setSpacing(CHAT_TRANSCRIPT_SPACING)
        self.transcript_layout.addStretch(1)
        self.transcript_scroll.setWidget(self.transcript_host)
        self.transcript_scroll.setAccessibleName("Lịch sử hội thoại Copilot")
        chat_layout.addWidget(self.transcript_scroll, stretch=1)

        composer_wrap = QFrame(chat_body)
        composer_wrap.setObjectName("CopilotComposer")
        composer_wrap.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum)
        composer_col = QVBoxLayout(composer_wrap)
        composer_col.setContentsMargins(
            COPILOT_COMPOSER_PADDING,
            COPILOT_COMPOSER_PADDING,
            COPILOT_COMPOSER_PADDING,
            COPILOT_COMPOSER_PADDING,
        )
        composer_col.setSpacing(COPILOT_COMPOSER_GAP)
        self._composer_wrap = composer_wrap

        self.status_label = BodyLabel("", composer_wrap)
        self.status_label.setWordWrap(True)
        self.status_label.setMinimumHeight(COPILOT_STATUS_MIN_HEIGHT)
        self.status_label.setVisible(False)
        self.status_label.setAccessibleName("Trạng thái Copilot")
        composer_col.addWidget(self.status_label)

        self.attach_chips = QWidget(composer_wrap)
        self.attach_chips.setObjectName("CopilotAttachList")
        self.attach_chips.setVisible(False)
        self.attach_chips.setSizePolicy(
            QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum
        )
        self._attach_list = QVBoxLayout(self.attach_chips)
        self._attach_list.setContentsMargins(0, 0, 0, 0)
        self._attach_list.setSpacing(4)
        composer_col.addWidget(self.attach_chips)

        input_row = QHBoxLayout()
        input_row.setSpacing(COPILOT_COMPOSER_GAP)
        self.btn_attach = PushButton(COPILOT_ATTACH_BTN_LABEL, composer_wrap)
        self.btn_attach.setIcon(FIF.DOCUMENT.icon())
        self.btn_attach.setFixedHeight(COPILOT_BTN_SEND_SIZE)
        self.btn_attach.setToolTip(
            "Đính kèm tài liệu lớn (md/pdf/docx/xlsx). Index full vào corpus; "
            "prompt chỉ nhận Top-K đoạn liên quan."
        )
        self.btn_attach.setAccessibleName("Đính kèm file Copilot")
        self.btn_attach.clicked.connect(self._pick_attachments)
        self.input_box = PlainTextEdit(composer_wrap)
        self.input_box.setObjectName("CopilotInput")
        self.input_box.setPlaceholderText(COPILOT_IDLE_PLACEHOLDER)
        self.input_box.setFixedHeight(COPILOT_INPUT_HEIGHT)
        self.input_box.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.input_box.setAccessibleName("Ô nhập Copilot")
        self.input_box.installEventFilter(self)
        self.btn_send = PrimaryToolButton(FIF.SEND, composer_wrap)
        self.btn_send.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_send.setToolTip("Gửi")
        self.btn_send.clicked.connect(self._emit_send)
        self.btn_stop = ToolButton(FIF.CANCEL, composer_wrap)
        self.btn_stop.setFixedSize(COPILOT_BTN_SEND_SIZE, COPILOT_BTN_SEND_SIZE)
        self.btn_stop.setToolTip("Dừng LLM / orchestrator")
        self.btn_stop.setVisible(False)
        self.btn_stop.clicked.connect(lambda: self.stop_requested.emit())
        input_row.addWidget(self.btn_attach, 0, Qt.AlignmentFlag.AlignBottom)
        input_row.addWidget(self.input_box, 1)
        input_row.addWidget(self.btn_send, 0, Qt.AlignmentFlag.AlignBottom)
        input_row.addWidget(self.btn_stop, 0, Qt.AlignmentFlag.AlignBottom)
        composer_col.addLayout(input_row)

        artifact_row = QHBoxLayout()
        artifact_row.setContentsMargins(0, 0, 0, 0)
        artifact_row.setSpacing(COPILOT_COMPOSER_GAP)
        self.btn_download = PushButton(COPILOT_BTN_DOWNLOAD_EXCEL, composer_wrap)
        self.btn_download.setEnabled(False)
        self.btn_download.setVisible(False)
        self.btn_download.setToolTip(COPILOT_DOWNLOAD_TOOLTIP)
        self.btn_download.setAccessibleName(COPILOT_BTN_DOWNLOAD_EXCEL)
        self.btn_download.clicked.connect(self._emit_download)
        self.btn_apply = PrimaryPushButton(COPILOT_BTN_APPLY, composer_wrap)
        self.btn_apply.setEnabled(False)
        self.btn_apply.setVisible(False)
        self.btn_apply.setToolTip(COPILOT_APPLY_TOOLTIP)
        self.btn_apply.clicked.connect(self._emit_apply)
        artifact_row.addWidget(self.btn_download)
        artifact_row.addWidget(self.btn_apply, stretch=1)
        composer_col.addLayout(artifact_row)
        chat_layout.addWidget(composer_wrap)

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
        self._theme_slot = bind_widget_theme(self, self._style_chat_chrome)
        self._style_chat_chrome()

    def _style_chat_chrome(self) -> None:
        t = get_theme_tokens()
        self.transcript_scroll.setStyleSheet(
            f"QScrollArea {{ background: {t.bg_card}; border: 1px solid {t.border_card};"
            f" border-radius: {THEME_CARD_RADIUS}px; }}"
        )
        self.transcript_host.setStyleSheet(f"background: {t.bg_card};")
        composer = getattr(self, "_composer_wrap", None)
        if composer is not None:
            composer.setStyleSheet(
                "#CopilotComposer {"
                f"background-color: {t.bg_card_subtle};"
                f"border: 1px solid {t.border_card};"
                f"border-radius: {THEME_CARD_RADIUS}px;"
                "}"
            )
        if getattr(self, "input_box", None) is not None:
            self.input_box.setStyleSheet(
                "#CopilotInput, PlainTextEdit {"
                f"background-color: {t.bg_canvas};"
                f"color: {t.text_primary};"
                f"border: 1px solid {t.border_card};"
                f"border-radius: {THEME_CARD_RADIUS}px;"
                "padding: 8px;"
                "}"
            )
        if getattr(self, "status_label", None) is not None:
            self.status_label.setStyleSheet(
                f"color: {t.warning_fg}; background-color: {t.warning_bg};"
                f"border: 1px solid {t.warning_border}; border-radius: 6px; padding: 6px 8px;"
            )

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
        # Keep chip labels visible; FlowLayout wraps. Invalidate so heightForWidth updates.
        self._context_flow.invalidate()
        self._pill_box.updateGeometry()

    def _on_mode_pivot(self, key: str) -> None:
        if key == "wizard":
            self.focus_compose()
        else:
            self.focus_case_chat()

    def set_busy(self, busy: bool) -> None:
        self._busy = busy
        self.btn_send.setVisible(not busy)
        self.btn_stop.setVisible(busy)
        self.btn_attach.setEnabled(not busy)
        self.input_box.setReadOnly(busy)
        if busy:
            self.input_box.setPlaceholderText(COPILOT_BUSY_PLACEHOLDER)
            self.set_status(COPILOT_STATUS_CONNECTING)
        else:
            self.input_box.setPlaceholderText(COPILOT_IDLE_PLACEHOLDER)
            self.set_status("")

    def set_status(self, text: str) -> None:
        message = (text or "").strip()
        self.status_label.setText(message)
        self.status_label.setVisible(bool(message))

    def set_apply_payload(self, payload: dict[str, Any] | None) -> None:
        self._pending_apply = payload
        ready = bool(payload)
        self.btn_apply.setEnabled(ready)
        self.btn_apply.setVisible(ready)
        self.btn_download.setEnabled(ready)
        self.btn_download.setVisible(ready)

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
        if not self._busy:
            self.input_box.setPlaceholderText(COPILOT_IDLE_PLACEHOLDER)
        self.input_box.setFocus()

    def new_session(self) -> None:
        mode = self._session.mode
        self._session = CopilotSession(mode=mode)
        self._clear_transcript()
        self._clear_attachments()
        self.set_apply_payload(None)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def clear_messages(self) -> None:
        self._session.messages.clear()
        self._clear_transcript()
        self._clear_attachments()
        self.set_apply_payload(None)
        self.starters.setVisible(True)
        self.session_changed.emit()

    def load_session(self, session: CopilotSession) -> None:
        self._session = session
        self._clear_transcript()
        for msg in session.messages:
            self._append_row(msg.id, msg.role, msg.bubble_text())
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
            self.model_combo.addItem(COPILOT_MODEL_LABEL_AUTO, COPILOT_MODEL_AUTO)
        idx = max(0, self.model_combo.findData(current))
        self.model_combo.setCurrentIndex(idx)
        self.model_combo.setToolTip(COPILOT_MODEL_HINT_AUTO)

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

    def append_status_summary(self, role: str, text: str, *, summary: str = "") -> None:
        self._streaming_row = None
        display = summary or text
        msg = CopilotMessage(role=role, content=text, summary=summary or text)
        self._session.messages.append(msg)
        self._append_row(msg.id, role, display)
        self.session_changed.emit()

    def append_agent_message(
        self, from_role: str, to_role: str, text: str, *, summary: str = ""
    ) -> None:
        body = f"{from_role} → {to_role}: {text}"
        display = (
            f"{from_role} → {to_role}: {summary}" if summary else body
        )
        msg = CopilotMessage(role=ROLE_CREW, content=body, summary=display if summary else "")
        self._session.messages.append(msg)
        self._append_row(msg.id, ROLE_CREW, display)
        self.session_changed.emit()

    def append_clarify_note(self, role: str, text: str) -> None:
        body = f"Clarify ({role}): {text}"
        msg = CopilotMessage(role=ROLE_SYSTEM, content=body, summary=body)
        self._session.messages.append(msg)
        self._append_row(msg.id, ROLE_SYSTEM, body)
        self.session_changed.emit()

    def append_user(self, text: str) -> None:
        msg = CopilotMessage(role="user", content=text, summary=text)
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

    def preview_role(self, role: str, summary: str) -> None:
        """Update open bubble display only — session content stays for role_end."""
        del role
        if self._streaming_row is not None:
            self._streaming_row.set_body(summary or "…")
            self._scroll_transcript()

    def append_stream_chunk(self, chunk: str) -> None:
        if self._streaming_row is None:
            self.begin_role(ROLE_COMPOSER)
        assert self._streaming_row is not None
        current = self._streaming_row._text + chunk
        self._streaming_row.set_body(current)
        if self._session.messages:
            self._session.messages[-1].content = current
        self._scroll_transcript()

    def end_role(self, role: str, text: str, *, summary: str = "") -> None:
        """Persist full `text` for agents; show `summary` (or text) in the bubble."""
        display = summary or text
        if self._session.messages and self._session.messages[-1].role == role:
            self._session.messages[-1].content = text
            self._session.messages[-1].summary = display
        if self._streaming_row is not None:
            self._streaming_row.set_body(display or "…")
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

    def copy_selected(self) -> None:
        if not self._session.messages:
            return
        text = self._session.messages[-1].content or self._session.messages[-1].bubble_text()
        QGuiApplication.clipboard().setText(text)
        self.info_message.emit("Đã copy", "Nội dung tin nhắn đã vào clipboard.")

    def undo_last_user(self) -> None:
        self._undo_session(self._session.undo_last_user_turn())

    def _on_copy_row(self, message_id: str) -> None:
        text = next(
            (m.content for m in self._session.messages if m.id == message_id and m.content),
            "",
        )
        if not text:
            text = next(
                (m.bubble_text() for m in self._session.messages if m.id == message_id),
                "",
            )
        QGuiApplication.clipboard().setText(text)
        self.info_message.emit("Đã copy", "Nội dung đầy đủ đã vào clipboard.")

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
        self.set_apply_payload(None)
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
        if not text and not self._attachments:
            return
        prompt = text or "Phân tích các tài liệu đã đính kèm."
        flags = self.context_flags()
        flags["attachments"] = [
            {
                "name": item.name,
                "path": str(item.path),
                "markdown": item.markdown,
                "doc_id": item.doc_id,
            }
            for item in self._attachments
        ]
        flags["attach_paths"] = [str(item.path) for item in self._attachments]
        display = prompt
        if self._attachments:
            names = ", ".join(item.name for item in self._attachments)
            display = f"{prompt}\n\n📎 {names}"
        self.input_box.clear()
        self.append_user(display)
        self.prompt_submitted.emit(prompt, flags)
        self._clear_attachments()

    def _emit_apply(self) -> None:
        if self._pending_apply:
            self.apply_requested.emit(dict(self._pending_apply))

    def _emit_download(self) -> None:
        if not self._pending_apply:
            self.info_message.emit(COPILOT_DOWNLOAD_FAIL_TITLE, COPILOT_DOWNLOAD_EMPTY)
            return
        path_str, _ = QFileDialog.getSaveFileName(
            self,
            COPILOT_DOWNLOAD_DIALOG_TITLE,
            VN_TC_FILENAME_DEFAULT,
            VN_TC_FILE_FILTER,
        )
        if not path_str:
            return
        target = Path(path_str).expanduser()
        if target.suffix.lower() != ".xlsx":
            target = target.with_suffix(".xlsx")
        try:
            CopilotArtifactExcelExporter().write(dict(self._pending_apply), target)
        except Exception as exc:
            self.info_message.emit(COPILOT_DOWNLOAD_FAIL_TITLE, str(exc))
            return
        self.info_message.emit(COPILOT_DOWNLOAD_DONE_TITLE, str(target))

    def set_project_root(self, root: Path | str | None) -> None:
        self._project_root = Path(root).resolve() if root else None
        if hasattr(self, "wizard") and hasattr(self.wizard, "set_project_root"):
            self.wizard.set_project_root(self._project_root)

    def _pick_attachments(self) -> None:
        if self._busy:
            return
        remaining = COPILOT_ATTACH_MAX_FILES - len(self._attachments)
        if remaining <= 0:
            self.info_message.emit(
                "Đính kèm",
                f"Tối đa {COPILOT_ATTACH_MAX_FILES} file mỗi lượt chat.",
            )
            return
        paths, _ = QFileDialog.getOpenFileNames(
            self, "Đính kèm tài liệu để phân tích", "", WIZARD_DOC_FILE_FILTER
        )
        if not paths:
            return
        errors: list[str] = []
        for raw in paths[:remaining]:
            target = Path(raw).expanduser().resolve()
            if any(item.path == target for item in self._attachments):
                errors.append(f"{target.name}: đã đính kèm")
                continue
            try:
                size = target.stat().st_size
            except OSError as exc:
                errors.append(f"{target.name}: {exc}")
                continue
            if size > WIZARD_DOC_MAX_FILE_BYTES:
                errors.append(f"{target.name}: vượt {WIZARD_DOC_MAX_FILE_BYTES} bytes")
                continue
            preview = self._preview_markdown(target)
            if not preview.strip():
                errors.append(f"{target.name}: trống / không đọc được")
                continue
            attachment = ChatAttachment(
                path=target,
                name=target.name,
                markdown=preview,
                index_status=_ATTACH_STATUS_PENDING,
                index_detail="đang index…",
            )
            self._attachments.append(attachment)
            self._start_ingest(attachment)
        self._rebuild_attach_chips()
        if errors:
            self.info_message.emit("Đính kèm", "\n".join(errors[:4]))

    def _preview_markdown(self, target: Path) -> str:
        try:
            if target.suffix.lower() in WIZARD_DOC_MARKDOWN_SUFFIXES:
                text = target.read_text(encoding="utf-8", errors="replace")
            else:
                text = self._attach_compressor.document_to_markdown(target)
        except Exception as exc:  # noqa: BLE001
            return f"(preview unavailable: {exc})"
        clipped = text.strip()
        if len(clipped) > COPILOT_ATTACH_PREVIEW_CHARS:
            clipped = clipped[: COPILOT_ATTACH_PREVIEW_CHARS - 1] + "…"
        return clipped

    def _start_ingest(self, attachment: ChatAttachment) -> None:
        key = str(attachment.path)
        root = self._project_root

        async def _job() -> IngestResult:
            return await asyncio.to_thread(ingest_path, root, attachment.path)

        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            attachment.index_status = _ATTACH_STATUS_ERROR
            attachment.index_detail = "no event loop"
            return

        task = loop.create_task(_job())
        self._ingest_tasks[key] = task

        def _done(done: asyncio.Task[IngestResult], path: Path = attachment.path) -> None:
            self._on_ingest_done(path, done)

        task.add_done_callback(_done)

    def _on_ingest_done(self, path: Path, task: asyncio.Task[IngestResult]) -> None:
        for item in self._attachments:
            if item.path != path:
                continue
            try:
                result = task.result()
            except Exception as exc:  # noqa: BLE001
                item.index_status = _ATTACH_STATUS_ERROR
                item.index_detail = str(exc)[:80]
                break
            item.doc_id = result.doc_id
            item.total_chunks = result.total_chunks
            item.index_status = _ATTACH_STATUS_READY
            item.index_detail = f"{result.total_chunks} chunks"
            break
        self._rebuild_attach_chips()

    async def await_attachment_ingests(self, paths: list[str]) -> list[str]:
        """Wait for pending corpus ingest; return doc_ids in path order."""
        doc_ids: list[str] = []
        for raw in paths:
            task = self._ingest_tasks.get(raw)
            if task is None:
                continue
            try:
                result = await task
            except Exception:  # noqa: BLE001
                continue
            if result.doc_id:
                doc_ids.append(result.doc_id)
            self._ingest_tasks.pop(raw, None)
        return doc_ids

    def _rebuild_attach_chips(self) -> None:
        while self._attach_list.count():
            item = self._attach_list.takeAt(0)
            if item is None:
                break
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        tokens = get_theme_tokens()
        for attachment in self._attachments:
            row = QWidget(self.attach_chips)
            row.setFixedHeight(COPILOT_ATTACH_CHIP_HEIGHT)
            row.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
            row_lay = QHBoxLayout(row)
            row_lay.setContentsMargins(0, 0, 0, 0)
            row_lay.setSpacing(4)
            label = BodyLabel(self._chip_label(attachment), row)
            label.setToolTip(f"{attachment.path}\n{attachment.index_detail}")
            label.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
            label.setStyleSheet(f"color: {tokens.text_secondary};")
            remove = TransparentToolButton(FIF.CLOSE, row)
            remove.setFixedSize(COPILOT_ATTACH_CHIP_HEIGHT, COPILOT_ATTACH_CHIP_HEIGHT)
            remove.setToolTip(f"Gỡ {attachment.name}")
            remove.clicked.connect(
                lambda _=False, path=attachment.path: self._remove_attachment(path)
            )
            row_lay.addWidget(label, 1)
            row_lay.addWidget(remove, 0)
            self._attach_list.addWidget(row)
        has_files = bool(self._attachments)
        self.attach_chips.setVisible(has_files)
        if has_files:
            height = (
                len(self._attachments) * COPILOT_ATTACH_CHIP_HEIGHT
                + max(0, len(self._attachments) - 1) * self._attach_list.spacing()
            )
            self.attach_chips.setMinimumHeight(height)
            self.attach_chips.setMaximumHeight(height)
        else:
            self.attach_chips.setMinimumHeight(0)
            self.attach_chips.setMaximumHeight(0)
        self.attach_chips.updateGeometry()
        self._composer_wrap.updateGeometry()

    @staticmethod
    def _chip_label(attachment: ChatAttachment) -> str:
        short = CopilotChatPane._short_attach_name(attachment.name)
        if attachment.index_status == _ATTACH_STATUS_PENDING:
            return f"{short} · index…"
        if attachment.index_status == _ATTACH_STATUS_ERROR:
            return f"{short} · lỗi"
        return f"{short} · {attachment.total_chunks} chunks"

    @staticmethod
    def _short_attach_name(name: str) -> str:
        text = name.strip() or "file"
        if len(text) <= COPILOT_ATTACH_NAME_MAX_CHARS:
            return f"📎 {text}"
        keep = COPILOT_ATTACH_NAME_MAX_CHARS - 1
        head = max(8, keep // 2)
        tail = max(8, keep - head)
        return f"📎 {text[:head]}…{text[-tail:]}"

    def _remove_attachment(self, path: Path) -> None:
        key = str(path)
        task = self._ingest_tasks.pop(key, None)
        if task is not None and not task.done():
            task.cancel()
        self._attachments = [item for item in self._attachments if item.path != path]
        self._rebuild_attach_chips()

    def _clear_attachments(self) -> None:
        self._attachments.clear()
        self._rebuild_attach_chips()

    def _clear_transcript(self) -> None:
        self._streaming_row = None
        while self._rows:
            row = self._rows.pop()
            self.transcript_layout.removeWidget(row)
            row.deleteLater()

    def _rebuild_from_session(self) -> None:
        self._clear_transcript()
        for msg in self._session.messages:
            self._append_row(msg.id, msg.role, msg.bubble_text())

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
