"""ElementFingerprint scoring — persistent identity match (no DistilledNode ids)."""

from __future__ import annotations

from ai_qa_test_studio.constants import (
    ATTR_IN_SHADOW_DOM,
    FP_FRAME_MISMATCH_FACTOR,
    FP_WEIGHT_CSS,
    FP_WEIGHT_FRAME_PATH,
    FP_WEIGHT_ROLE_NAME,
    FP_WEIGHT_SHADOW_DOM,
    FP_WEIGHT_TEST_ID,
    FP_WEIGHT_TEXT,
    FP_WEIGHT_XPATH,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
)
from ai_qa_test_studio.core.models import ElementFingerprint, SharedElement


def _norm(value: str | None) -> str:
    return (value or "").strip().lower()


def normalize_frame_path(value: list[str] | str | None) -> list[str]:
    if value is None or value == "":
        return []
    if isinstance(value, str):
        return [part.strip() for part in value.split(",") if part.strip()]
    return [str(part).strip() for part in value if str(part).strip()]


def _partial_text_score(expected: str, candidate: str) -> float:
    if not expected or not candidate:
        return 0.0
    if expected == candidate:
        return 1.0
    if expected in candidate or candidate in expected:
        return 0.7
    exp_tokens = {t for t in expected.split() if len(t) > 2}
    cand_tokens = {t for t in candidate.split() if len(t) > 2}
    if not exp_tokens:
        return 0.0
    overlap = len(exp_tokens & cand_tokens) / len(exp_tokens)
    return overlap


def _shadow_flag(fp: ElementFingerprint) -> bool:
    raw = fp.attrs_subset.get(ATTR_IN_SHADOW_DOM, "")
    return _norm(raw) in {"1", "true", "yes"}


def score_match(expected: ElementFingerprint, candidate: ElementFingerprint) -> float:
    """Weighted similarity in [0, 1]. test_id ≫ role+name ≫ css ≫ xpath ≫ text (+ frame/shadow)."""
    score = 0.0
    exp_tid = _norm(expected.test_id)
    cand_tid = _norm(candidate.test_id)
    if exp_tid and cand_tid and exp_tid == cand_tid:
        score += FP_WEIGHT_TEST_ID

    role_ok = bool(_norm(expected.role) and _norm(expected.role) == _norm(candidate.role))
    name_score = _partial_text_score(
        _norm(expected.accessible_name),
        _norm(candidate.accessible_name),
    )
    if role_ok and name_score >= 0.7:
        score += FP_WEIGHT_ROLE_NAME
    elif role_ok or name_score >= 0.7:
        score += FP_WEIGHT_ROLE_NAME * 0.5

    if _norm(expected.css) and _norm(expected.css) == _norm(candidate.css):
        score += FP_WEIGHT_CSS
    if _norm(expected.xpath) and _norm(expected.xpath) == _norm(candidate.xpath):
        score += FP_WEIGHT_XPATH

    text_score = max(
        _partial_text_score(_norm(expected.text_sample), _norm(candidate.text_sample)),
        _partial_text_score(_norm(expected.accessible_name), _norm(candidate.text_sample)),
    )
    score += FP_WEIGHT_TEXT * text_score

    exp_frames = normalize_frame_path(expected.frame_path)
    cand_frames = normalize_frame_path(candidate.frame_path)
    if exp_frames:
        if exp_frames == cand_frames:
            score += FP_WEIGHT_FRAME_PATH
        else:
            score *= FP_FRAME_MISMATCH_FACTOR

    if _shadow_flag(expected):
        if _shadow_flag(candidate):
            score += FP_WEIGHT_SHADOW_DOM
        else:
            score *= FP_FRAME_MISMATCH_FACTOR

    return round(min(score, 1.0), 4)


def fingerprint_from_shared(element: SharedElement) -> ElementFingerprint:
    if element.fingerprint is not None:
        return element.fingerprint
    return ElementFingerprint(
        tag="",
        role=element.role,
        accessible_name=element.name,
        text_sample=element.name,
        test_id=element.test_id,
        css=element.css,
        xpath=element.xpath,
    )


def fingerprint_from_meta(meta: dict[str, object]) -> ElementFingerprint:
    name = str(meta.get("name") or meta.get("accessible_name") or "")
    frame_raw = meta.get("framePath") or meta.get("frame_path") or []
    attrs: dict[str, str] = {}
    placeholder = meta.get("placeholder")
    if placeholder:
        attrs["placeholder"] = str(placeholder)
    in_shadow = meta.get("inShadowDom") or meta.get(ATTR_IN_SHADOW_DOM)
    if in_shadow:
        attrs[ATTR_IN_SHADOW_DOM] = "true"
    return ElementFingerprint(
        tag=str(meta.get("tag") or ""),
        role=str(meta.get("role") or "") or None,
        accessible_name=name or None,
        text_sample=name or None,
        test_id=str(meta.get("testId") or meta.get("test_id") or "") or None,
        css=str(meta.get("css") or "") or None,
        xpath=str(meta.get("xpath") or "") or None,
        frame_path=normalize_frame_path(frame_raw if isinstance(frame_raw, (list, str)) else []),
        attrs_subset=attrs,
    )


def should_run_heal(confidence: float) -> bool:
    return confidence >= HEAL_RUN_THRESHOLD


def should_persist_heal(confidence: float) -> bool:
    return confidence >= HEAL_PERSIST_THRESHOLD


def apply_locator_to_element(
    element: SharedElement,
    *,
    css: str,
    confidence: float,
    test_id: str | None = None,
    name: str | None = None,
    role: str | None = None,
    fingerprint: ElementFingerprint | None = None,
) -> bool:
    """Apply in-memory locator patch. Returns True if YAML-safe (≥ persist threshold)."""
    element.css = css
    element.primary_playwright = css
    if test_id:
        element.test_id = test_id
    if name:
        element.name = name
    if role:
        element.role = role
    persist = should_persist_heal(confidence)
    if persist and fingerprint is not None:
        element.fingerprint = fingerprint
    return persist
