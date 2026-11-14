"""Outbound-only secret redaction for logs, UI, LLM history, and evidence."""

from __future__ import annotations

import copy
import re
from typing import Any

from ai_qa_test_studio.constants import (
    SECRET_MASK_KEY_HINTS,
    SECRET_MASK_PLACEHOLDER,
)


def _build_patterns(placeholder: str) -> tuple[tuple[re.Pattern[str], str], ...]:
    key_alt = "|".join(re.escape(h) for h in SECRET_MASK_KEY_HINTS)
    return (
        (
            re.compile(rf"(?i)(\b(?:{key_alt})\b\s*[:=]\s*)([^\s,;\"']+)"),
            rf"\1{placeholder}",
        ),
        (
            re.compile(r"(?i)(bearer\s+)([A-Za-z0-9\-._~+/]+=*)"),
            rf"\1{placeholder}",
        ),
        (
            re.compile(r"(?i)(authorization\s*[:=]\s*)([^\s,;\"']+)"),
            rf"\1{placeholder}",
        ),
    )


class SecretMasker:
    """Mask secrets in outbound text/mappings. Never mutate Playwright tool args in place."""

    def __init__(
        self,
        placeholder: str = SECRET_MASK_PLACEHOLDER,
    ) -> None:
        self._placeholder = placeholder
        self._subs = _build_patterns(placeholder)

    def mask_text(self, text: str) -> str:
        if not text:
            return text
        out = text
        for pattern, repl in self._subs:
            out = pattern.sub(repl, out)
        return out

    def mask_mapping(self, data: dict[str, Any]) -> dict[str, Any]:
        """Return a deep-copied mapping with string values masked (outbound)."""
        cloned = copy.deepcopy(data)
        masked = self._mask_value(cloned)
        if not isinstance(masked, dict):
            return {}
        return masked

    def mask_snapshot(self, snapshot: str) -> str:
        return self.mask_text(snapshot)

    def _mask_value(self, value: Any) -> Any:
        if isinstance(value, dict):
            masked: dict[str, Any] = {}
            for key, item in value.items():
                key_l = str(key).lower()
                if any(hint in key_l for hint in SECRET_MASK_KEY_HINTS):
                    masked[str(key)] = self._placeholder if item not in (None, "") else item
                else:
                    masked[str(key)] = self._mask_value(item)
            return masked
        if isinstance(value, list):
            return [self._mask_value(item) for item in value]
        if isinstance(value, str):
            return self.mask_text(value)
        return value


_DEFAULT_MASKER = SecretMasker()


def get_secret_masker() -> SecretMasker:
    return _DEFAULT_MASKER
