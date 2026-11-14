"""Render chat bubble markdown (especially pipe tables) to compact HTML."""

from __future__ import annotations

import html
import re

from PySide6.QtGui import QTextDocument

from ai_qa_test_studio.constants import CHAT_MD_TABLE_CSS

_MD_TABLE_LINE_RE = re.compile(r"^\s*\|.+\|\s*$", re.MULTILINE)
_MD_TABLE_SEP_RE = re.compile(r"^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$", re.MULTILINE)


def looks_like_markdown_table(text: str) -> bool:
    raw = text or ""
    return bool(_MD_TABLE_LINE_RE.search(raw) and _MD_TABLE_SEP_RE.search(raw))


def markdown_to_bubble_html(text: str) -> str:
    """Convert markdown (with tables) to HTML suitable for QLabel rich text."""
    raw = text or ""
    if not raw.strip():
        return ""
    if not looks_like_markdown_table(raw):
        return html.escape(raw).replace("\n", "<br/>")
    doc = QTextDocument()
    doc.setMarkdown(raw)
    body = doc.toHtml()
    # Keep Qt's table markup; wrap with compact border CSS for QLabel.
    return (
        f"<html><head><style>{CHAT_MD_TABLE_CSS}</style></head>"
        f"<body>{body}</body></html>"
    )
