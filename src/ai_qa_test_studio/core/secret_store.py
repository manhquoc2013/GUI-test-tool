"""OS keychain-backed API key persistence (settings.json stores refs only)."""

from __future__ import annotations

import logging

import keyring

from ai_qa_test_studio.constants import KEYRING_REF_PREFIX, KEYRING_SERVICE

logger = logging.getLogger(__name__)


def is_keyring_ref(ref: str) -> bool:
    return bool(ref) and ref.startswith(KEYRING_REF_PREFIX)


def store_secret(slot: str, value: str) -> str:
    """Persist ``value`` under ``slot``; return ``keyring:<slot>`` ref."""
    keyring.set_password(KEYRING_SERVICE, slot, value)
    return f"{KEYRING_REF_PREFIX}{slot}"


def load_secret(ref: str) -> str:
    """Resolve a keyring ref to plaintext. Empty/missing/non-ref → \"\"."""
    if not is_keyring_ref(ref):
        return ""
    slot = ref[len(KEYRING_REF_PREFIX) :]
    if not slot:
        return ""
    try:
        return keyring.get_password(KEYRING_SERVICE, slot) or ""
    except Exception:
        logger.exception("Failed to load keyring secret for slot %s", slot)
        return ""


def resolve_api_key(ref: str) -> str:
    """Plaintext for LLM runtime: keyring refs are resolved; other values pass through."""
    stripped = (ref or "").strip()
    if not stripped:
        return ""
    if is_keyring_ref(stripped):
        return load_secret(stripped)
    return stripped


def seal_api_key_ref(slot: str, value: str) -> str:
    """If ``value`` is a raw secret, store it and return a keyring ref; else return as-is."""
    stripped = (value or "").strip()
    if not stripped or is_keyring_ref(stripped):
        return stripped
    return store_secret(slot, stripped)
