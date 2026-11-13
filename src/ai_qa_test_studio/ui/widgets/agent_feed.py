"""Agent execution feed — step cards instead of raw developer logs."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import QLabel, QListWidget, QListWidgetItem, QVBoxLayout, QWidget

from ai_qa_test_studio.constants import ICON_COLOR_DANGER, ICON_COLOR_SUCCESS, ICON_COLOR_WARN


class AgentFeed(QWidget):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Agent Execution Feed")
        self._lines: list[str] = []
        self._title = QLabel("Luồng thực thi Agent")
        self._title.setObjectName("muted")
        self.list = QListWidget()
        self.list.setAccessibleName("Danh sách bước chạy")
        self.list.itemClicked.connect(self._toggle_detail)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(4)
        layout.addWidget(self._title)
        layout.addWidget(self.list)

    def set_title_visible(self, visible: bool) -> None:
        self._title.setVisible(visible)

    def clear(self) -> None:
        self._lines.clear()
        self.list.clear()

    def append_message(self, message: str) -> None:
        text = message.strip()
        if not text:
            return
        # Drop developer-only integration notes
        lowered = text.lower()
        if "agent api" in lowered or "integration tests" in lowered:
            return
        self._lines.append(text)
        summary, detail = self._split_card(text)
        item = QListWidgetItem(summary)
        item.setData(Qt.ItemDataRole.UserRole, detail)
        item.setToolTip(detail or summary)
        color = ICON_COLOR_WARN
        if summary.startswith("[✓]") or "→ PASS" in text or "BOUND" in text and "UNBOUND=0" in text:
            color = ICON_COLOR_SUCCESS
        if "fail" in lowered or summary.startswith("[✗]"):
            color = ICON_COLOR_DANGER
        item.setForeground(QBrush(QColor(color)))
        self.list.addItem(item)
        self.list.scrollToBottom()

    def to_plain_text(self) -> str:
        return "\n".join(self._lines)

    def append_step(
        self,
        step_no: int,
        action: str,
        status: str,
        duration_ms: float,
        message: str = "",
    ) -> None:
        mark = "[✓]" if status in {"PASS", "HEALED", "BOUND"} else "[✗]" if status == "FAIL" else "[…]"
        summary = f"{mark} Bước {step_no}: {action} ({duration_ms / 1000:.1f}s)"
        detail = message or status
        self.append_message(f"{summary}\n{detail}")

    def _split_card(self, text: str) -> tuple[str, str]:
        if "\n" in text:
            head, *rest = text.splitlines()
            return head, "\n".join(rest).strip()
        # Promote "  #N action → STATUS" lines into cards
        stripped = text.lstrip()
        if stripped.startswith("#") and "→" in stripped:
            try:
                left, right = stripped.split("→", 1)
                num = left.split()[0].lstrip("#")
                action = " ".join(left.split()[1:])
                status_part = right.strip().split()[0]
                ms = 0.0
                if "(" in right and "ms" in right:
                    inner = right[right.find("(") + 1 : right.find("ms")].strip()
                    ms = float(inner)
                mark = "[✓]" if status_part in {"PASS", "HEALED"} else "[✗]" if status_part == "FAIL" else "[…]"
                return f"{mark} Bước {num}: {action} ({ms / 1000:.1f}s)", right.strip()
            except (ValueError, IndexError):
                pass
        return text, ""

    def _toggle_detail(self, item: QListWidgetItem) -> None:
        detail = item.data(Qt.ItemDataRole.UserRole) or ""
        summary = item.text()
        if detail and detail not in summary:
            item.setText(f"{summary}\n  {detail}")
            item.setData(Qt.ItemDataRole.UserRole, "")
        elif "\n" in summary:
            head = summary.splitlines()[0]
            item.setText(head)
            item.setData(Qt.ItemDataRole.UserRole, "\n".join(summary.splitlines()[1:]).strip())
