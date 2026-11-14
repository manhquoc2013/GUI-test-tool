from __future__ import annotations

from ai_qa_test_studio.constants import SECRET_MASK_PLACEHOLDER
from ai_qa_test_studio.core.secret_masker import SecretMasker


def test_mask_text_preserves_key_redacts_value() -> None:
    masker = SecretMasker()
    raw = "password=super-secret-pass token: abc123"
    masked = masker.mask_text(raw)
    assert "super-secret-pass" not in masked
    assert "abc123" not in masked
    assert SECRET_MASK_PLACEHOLDER in masked
    assert "password=" in masked


def test_mask_mapping_does_not_mutate_original() -> None:
    masker = SecretMasker()
    args = {"password": "PlainPass123", "username": "qa"}
    outbound = masker.mask_mapping(args)
    assert args["password"] == "PlainPass123"
    assert outbound["password"] == SECRET_MASK_PLACEHOLDER
    assert outbound["username"] == "qa"


def test_fill_args_path_keeps_raw_for_playwright() -> None:
    """Invariant: raw tool args for fill must stay usable after outbound mask copy."""
    masker = SecretMasker()
    raw_args = {"text": "password=RealLoginSecret", "selector": "#pwd"}
    for_log = masker.mask_mapping(raw_args)
    assert raw_args["text"] == "password=RealLoginSecret"
    assert SECRET_MASK_PLACEHOLDER in for_log["text"]
