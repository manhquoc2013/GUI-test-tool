"""QSS design tokens (dark/light) — no QtWebEngine."""

from __future__ import annotations

DARK_QSS = """
QWidget { background: #12141a; color: #e8eaed; font-size: 13px; }
QMainWindow { background: #0e1015; }
QTreeWidget, QPlainTextEdit, QTextEdit, QListWidget, QTableWidget {
  background: #1a1d26; border: 1px solid #2a2f3a; border-radius: 6px;
}
QPushButton {
  min-height: 44px; min-width: 44px; padding: 8px 14px;
  background: #2b3240; border: 1px solid #3a4254; border-radius: 6px;
}
QPushButton:hover { background: #3a4254; }
QPushButton#primary { background: #3d7eff; color: #fff; }
QLabel#title { font-size: 18px; font-weight: 700; color: #f2f4f8; }
QStatusBar { background: #0e1015; color: #9aa3b2; }
QLineEdit, QComboBox {
  min-height: 36px; padding: 6px 10px; background: #1a1d26;
  border: 1px solid #2a2f3a; border-radius: 6px; color: #e8eaed;
}
"""

LIGHT_QSS = """
QWidget { background: #f5f6f8; color: #1a1d26; font-size: 13px; }
QMainWindow { background: #eef0f4; }
QTreeWidget, QPlainTextEdit, QTextEdit, QListWidget, QTableWidget {
  background: #ffffff; border: 1px solid #d5dae3; border-radius: 6px;
}
QPushButton {
  min-height: 44px; min-width: 44px; padding: 8px 14px;
  background: #e8ebf2; border: 1px solid #c9d0dc; border-radius: 6px;
}
QPushButton:hover { background: #dce2ec; }
QPushButton#primary { background: #2f6fed; color: #fff; }
QLabel#title { font-size: 18px; font-weight: 700; color: #12141a; }
QStatusBar { background: #eef0f4; color: #5b6575; }
QLineEdit, QComboBox {
  min-height: 36px; padding: 6px 10px; background: #ffffff;
  border: 1px solid #d5dae3; border-radius: 6px;
}
"""


def apply_theme(app, theme: str = "dark") -> None:  # type: ignore[no-untyped-def]
    app.setStyleSheet(DARK_QSS if theme == "dark" else LIGHT_QSS)
