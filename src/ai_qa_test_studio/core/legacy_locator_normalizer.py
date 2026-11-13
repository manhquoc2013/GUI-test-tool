"""Normalize Java/Selenium locator prefixes and ${var} → {{var}} for Tier-1 bind."""

from __future__ import annotations

import re

from ai_qa_test_studio.constants import VAR_PATTERN_LEGACY

_BY_ID = re.compile(r'^By\.id\(\s*["\']([^"\']+)["\']\s*\)$', re.I)
_BY_CSS = re.compile(r'^By\.cssSelector\(\s*["\']([^"\']+)["\']\s*\)$', re.I)
_BY_XPATH = re.compile(r'^By\.xpath\(\s*["\']([^"\']+)["\']\s*\)$', re.I)
_BY_NAME = re.compile(r'^By\.name\(\s*["\']([^"\']+)["\']\s*\)$', re.I)
_BY_LINK = re.compile(r'^By\.linkText\(\s*["\']([^"\']+)["\']\s*\)$', re.I)
_PREFIX = re.compile(
    r"^(id|css|xpath|name|linkText|partialLinkText|className|tagName)\s*=\s*(.+)$",
    re.I | re.S,
)


def normalize_variables(text: str | None) -> str:
    if not text:
        return ""
    return re.sub(VAR_PATTERN_LEGACY, r"{{\1}}", text)


def normalize_locator(raw: str | None) -> str:
    if raw is None:
        return ""
    value = raw.strip()
    if not value:
        return ""

    for pattern, fmt in (
        (_BY_ID, "#{0}"),
        (_BY_CSS, "{0}"),
        (_BY_XPATH, "xpath={0}"),
        (_BY_NAME, "[name=\"{0}\"]"),
        (_BY_LINK, "text={0}"),
    ):
        match = pattern.match(value)
        if match:
            return fmt.format(match.group(1))

    prefix = _PREFIX.match(value)
    if prefix:
        kind = prefix.group(1).lower()
        body = prefix.group(2).strip()
        if kind == "id":
            return f"#{body.lstrip('#')}"
        if kind == "css":
            return body
        if kind == "xpath":
            return body if body.startswith("xpath=") else f"xpath={body}"
        if kind == "name":
            return f'[name="{body}"]'
        if kind in {"linktext", "partiallinktext"}:
            return f"text={body}"
        if kind == "classname":
            return f".{body.lstrip('.')}"
        if kind == "tagname":
            return body

    return normalize_variables(value)


def normalize_action(java_action: str | None) -> str:
    from ai_qa_test_studio.constants import ACTION_MAP_JAVA_TO_V4

    if not java_action:
        return "click"
    return ACTION_MAP_JAVA_TO_V4.get(java_action.strip().upper(), "click")
