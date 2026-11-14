"""Evidence viewer — step sync, lazy screenshot load, heal selector diff."""

from __future__ import annotations

import asyncio
from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QImage, QPixmap
from PySide6.QtWidgets import (
    QDialog,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QSizePolicy,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    BodyLabel,
    CaptionLabel,
    CardWidget,
    PrimaryPushButton,
    PushButton,
    StrongBodyLabel,
    SubtitleLabel,
)

from ai_qa_test_studio.constants import (
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
    UI_EVIDENCE_ACCEPT_HEAL,
    UI_EVIDENCE_EMPTY,
    UI_EVIDENCE_FIT,
    UI_EVIDENCE_FULL,
    UI_EVIDENCE_HEAL_PERSISTED,
    UI_EVIDENCE_HEAL_TEMP,
    UI_EVIDENCE_LOADING,
    UI_EVIDENCE_MISSING_SHOT,
    UI_EVIDENCE_NO_SHOT,
    UI_EVIDENCE_SELECTOR_HEALED,
    UI_EVIDENCE_SELECTOR_PREV,
    UI_EVIDENCE_ZOOM,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import RunReport, RunStepResult, StepAuditEntry
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.ui.motion import fade_in
from ai_qa_test_studio.ui.smooth_scroll import apply_pixel_scroll, create_scroll_area
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme


class EvidencePanel(QWidget):
    """Results / evidence tab: step list + detail + lazy screenshot + heal diff."""

    accept_heal_requested = Signal(object)  # StepAuditEntry
    image_loaded = Signal(object)  # QPixmap | None
    step_selected = Signal(int)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("EvidencePanel")
        self.setAccessibleName("Panel bằng chứng và kết quả chạy")
        self._report: RunReport | None = None
        self._steps: list[RunStepResult] = []
        self._load_gen = 0
        self._pixmap: QPixmap | None = None
        self._fit_mode = True

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(8)

        self.title = SubtitleLabel("Kết quả & Evidence", self)
        root.addWidget(self.title)

        body = QHBoxLayout()
        body.setSpacing(10)

        self.step_list = QListWidget(self)
        self.step_list.setObjectName("EvidenceStepList")
        self.step_list.setMinimumWidth(180)
        self.step_list.setMaximumWidth(260)
        apply_pixel_scroll(self.step_list)
        self.step_list.currentRowChanged.connect(self._on_row_changed)
        body.addWidget(self.step_list, stretch=0)

        detail_scroll = create_scroll_area(self)
        detail_scroll.setWidgetResizable(True)
        detail_scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        detail_host = QWidget(detail_scroll)
        detail_host.setObjectName("EvidenceDetailHost")
        self._detail_layout = QVBoxLayout(detail_host)
        self._detail_layout.setContentsMargins(4, 4, 4, 4)
        self._detail_layout.setSpacing(8)

        self.meta_card = CardWidget(detail_host)
        meta_l = QVBoxLayout(self.meta_card)
        meta_l.setContentsMargins(12, 10, 12, 10)
        meta_l.setSpacing(4)
        self.meta_status = StrongBodyLabel("—", self.meta_card)
        self.meta_duration = CaptionLabel("", self.meta_card)
        self.meta_message = BodyLabel(UI_EVIDENCE_EMPTY, self.meta_card)
        self.meta_message.setWordWrap(True)
        self.meta_console = CaptionLabel("", self.meta_card)
        self.meta_console.setWordWrap(True)
        self.meta_dom = CaptionLabel("", self.meta_card)
        self.meta_dom.setWordWrap(True)
        meta_l.addWidget(self.meta_status)
        meta_l.addWidget(self.meta_duration)
        meta_l.addWidget(self.meta_message)
        meta_l.addWidget(self.meta_console)
        meta_l.addWidget(self.meta_dom)
        self._detail_layout.addWidget(self.meta_card)

        self.heal_card = CardWidget(detail_host)
        self.heal_card.setObjectName("EvidenceHealCard")
        heal_l = QVBoxLayout(self.heal_card)
        heal_l.setContentsMargins(12, 10, 12, 10)
        heal_l.setSpacing(6)
        self.heal_title = StrongBodyLabel("Selector Diff", self.heal_card)
        self.heal_badge = CaptionLabel("", self.heal_card)
        self.heal_note = BodyLabel("", self.heal_card)
        self.heal_note.setWordWrap(True)
        self.heal_prev = CaptionLabel("", self.heal_card)
        self.heal_prev.setWordWrap(True)
        self.heal_new = CaptionLabel("", self.heal_card)
        self.heal_new.setWordWrap(True)
        self.btn_accept_heal = PrimaryPushButton(UI_EVIDENCE_ACCEPT_HEAL, self.heal_card)
        self.btn_accept_heal.clicked.connect(self._emit_accept_heal)
        heal_l.addWidget(self.heal_title)
        heal_l.addWidget(self.heal_badge)
        heal_l.addWidget(self.heal_note)
        heal_l.addWidget(self.heal_prev)
        heal_l.addWidget(self.heal_new)
        heal_l.addWidget(self.btn_accept_heal)
        self.heal_card.hide()
        self._detail_layout.addWidget(self.heal_card)

        shot_bar = QHBoxLayout()
        self.btn_fit = PushButton(UI_EVIDENCE_FIT, detail_host)
        self.btn_full = PushButton(UI_EVIDENCE_FULL, detail_host)
        self.btn_zoom = PushButton(UI_EVIDENCE_ZOOM, detail_host)
        self.btn_fit.clicked.connect(lambda: self._set_fit(True))
        self.btn_full.clicked.connect(lambda: self._set_fit(False))
        self.btn_zoom.clicked.connect(self._open_zoom)
        shot_bar.addWidget(self.btn_fit)
        shot_bar.addWidget(self.btn_full)
        shot_bar.addWidget(self.btn_zoom)
        shot_bar.addStretch(1)
        self._detail_layout.addLayout(shot_bar)

        self.shot_label = QLabel(UI_EVIDENCE_EMPTY, detail_host)
        self.shot_label.setObjectName("EvidenceShot")
        self.shot_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.shot_label.setMinimumHeight(180)
        self.shot_label.setSizePolicy(
            QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding
        )
        self.shot_label.setScaledContents(False)
        self._detail_layout.addWidget(self.shot_label, stretch=1)
        self._detail_layout.addStretch(0)

        detail_scroll.setWidget(detail_host)
        body.addWidget(detail_scroll, stretch=1)
        root.addLayout(body, stretch=1)

        self._pending_audit: StepAuditEntry | None = None
        self.image_loaded.connect(self._apply_pixmap)
        self._apply_theme()
        self._theme_slot = bind_widget_theme(self, self._apply_theme)

    def _apply_theme(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(
            f"#EvidencePanel {{ background-color: {t.bg_card}; }}"
            f"#EvidenceShot {{ background-color: {t.bg_canvas}; border: 1px solid {t.border_card}; "
            f"border-radius: 8px; color: {t.text_secondary}; }}"
        )

    def clear(self) -> None:
        self._report = None
        self._steps = []
        self._pixmap = None
        self._load_gen += 1
        self.step_list.clear()
        self.meta_status.setText("—")
        self.meta_duration.setText("")
        self.meta_message.setText(UI_EVIDENCE_EMPTY)
        self.meta_console.setText("")
        self.meta_dom.setText("")
        self.heal_card.hide()
        self.shot_label.setPixmap(QPixmap())
        self.shot_label.setText(UI_EVIDENCE_EMPTY)

    def set_report(self, report: RunReport | None) -> None:
        self.clear()
        self._report = report
        if report is None:
            return
        for case in report.cases:
            for step in case.steps:
                self._steps.append(step)
                status = (
                    step.step_status.value
                    if step.step_status is not None
                    else str(step.status)
                )
                item = QListWidgetItem(
                    f"#{step.step_no} {step.action} · {status}"
                )
                item.setData(Qt.ItemDataRole.UserRole, len(self._steps) - 1)
                self.step_list.addItem(item)
        if self._steps:
            self.step_list.setCurrentRow(0)
            fade_in(self.meta_card)

    def select_step_no(self, step_no: int) -> None:
        for row, step in enumerate(self._steps):
            if step.step_no == step_no:
                self.step_list.setCurrentRow(row)
                return

    def _on_row_changed(self, row: int) -> None:
        if row < 0 or row >= len(self._steps):
            return
        step = self._steps[row]
        self.step_selected.emit(step.step_no)
        self._render_step(step)
        fade_in(self.meta_card)

    def _render_step(self, step: RunStepResult) -> None:
        masker = get_secret_masker()
        status = (
            step.step_status.value
            if step.step_status is not None
            else str(step.status)
        )
        self.meta_status.setText(f"{status} · {step.action}")
        self.meta_duration.setText(f"duration_ms: {step.duration_ms}")
        self.meta_message.setText(masker.mask_text(step.message or "—"))
        console = "\n".join(step.console_logs) if step.console_logs else ""
        self.meta_console.setText(
            masker.mask_text(f"Console/Network:\n{console}") if console else ""
        )
        self.meta_dom.setText("")
        self._render_heal(step)
        self._start_lazy_shot(step.screenshot_path)

    def _render_heal(self, step: RunStepResult) -> None:
        audit = step.audit
        if audit is None and self._report is not None:
            for entry in self._report.healing_audit:
                if entry.step_no == step.step_no:
                    audit = entry
                    break
        healed = (
            step.step_status == StepStatus.HEALED
            or step.status == "HEALED"
            or audit is not None
        )
        if not healed or audit is None:
            self.heal_card.hide()
            self._pending_audit = None
            return
        self._pending_audit = audit
        conf = float(audit.confidence_score or 0.0)
        self.heal_badge.setText(f"confidence: {conf:.2f}")
        prev = audit.previous_selector or audit.old_locator or "—"
        healed_sel = audit.healed_selector or audit.new_locator or "—"
        self.heal_prev.setText(f"{UI_EVIDENCE_SELECTOR_PREV}: {prev}")
        self.heal_new.setText(f"{UI_EVIDENCE_SELECTOR_HEALED}: {healed_sel}")
        if conf >= HEAL_PERSIST_THRESHOLD or audit.persisted:
            self.heal_note.setText(UI_EVIDENCE_HEAL_PERSISTED)
            self.btn_accept_heal.hide()
        elif conf >= HEAL_RUN_THRESHOLD:
            self.heal_note.setText(UI_EVIDENCE_HEAL_TEMP)
            self.btn_accept_heal.show()
        else:
            self.heal_note.setText(UI_EVIDENCE_HEAL_TEMP)
            self.btn_accept_heal.hide()
        self.heal_card.show()

    def _emit_accept_heal(self) -> None:
        if self._pending_audit is not None:
            self.accept_heal_requested.emit(self._pending_audit)

    def _start_lazy_shot(self, path: str | None) -> None:
        self._load_gen += 1
        gen = self._load_gen
        self._pixmap = None
        if not path:
            self.shot_label.setPixmap(QPixmap())
            self.shot_label.setText(UI_EVIDENCE_NO_SHOT)
            return
        file_path = Path(path)
        if not file_path.is_file():
            self.shot_label.setPixmap(QPixmap())
            self.shot_label.setText(UI_EVIDENCE_MISSING_SHOT)
            return
        self.shot_label.setText(UI_EVIDENCE_LOADING)
        supervisor = get_async_task_supervisor()
        supervisor.register(
            self._load_shot_async(file_path, gen),
            key=f"evidence:shot:{gen}",
            replace=True,
        )

    async def _load_shot_async(self, path: Path, gen: int) -> None:
        def _decode() -> QPixmap | None:
            image = QImage(str(path))
            if image.isNull():
                return None
            return QPixmap.fromImage(image)

        pixmap = await asyncio.to_thread(_decode)
        if gen != self._load_gen:
            return
        supervisor = get_async_task_supervisor()
        supervisor.emit_if_alive(self, self.image_loaded, pixmap)

    def _apply_pixmap(self, pixmap: object) -> None:
        if pixmap is None or not isinstance(pixmap, QPixmap) or pixmap.isNull():
            self.shot_label.setPixmap(QPixmap())
            self.shot_label.setText(UI_EVIDENCE_MISSING_SHOT)
            self._pixmap = None
            return
        self._pixmap = pixmap
        self._paint_shot()

    def _set_fit(self, fit: bool) -> None:
        self._fit_mode = fit
        self._paint_shot()

    def _paint_shot(self) -> None:
        if self._pixmap is None or self._pixmap.isNull():
            return
        self.shot_label.setText("")
        if self._fit_mode:
            scaled = self._pixmap.scaled(
                self.shot_label.size(),
                Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            )
            self.shot_label.setPixmap(scaled)
        else:
            self.shot_label.setPixmap(self._pixmap)

    def _open_zoom(self) -> None:
        if self._pixmap is None or self._pixmap.isNull():
            return
        dialog = QDialog(self)
        dialog.setWindowTitle(UI_EVIDENCE_ZOOM)
        layout = QVBoxLayout(dialog)
        label = QLabel(dialog)
        label.setPixmap(self._pixmap)
        label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        scroll = create_scroll_area(dialog)
        scroll.setWidget(label)
        layout.addWidget(scroll)
        dialog.resize(min(960, self._pixmap.width() + 40), min(720, self._pixmap.height() + 40))
        dialog.exec()

    def resizeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        super().resizeEvent(event)
        if self._fit_mode and self._pixmap is not None:
            self._paint_shot()
