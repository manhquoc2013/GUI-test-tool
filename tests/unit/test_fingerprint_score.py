from __future__ import annotations

from ai_qa_test_studio.constants import (
    ATTR_IN_SHADOW_DOM,
    FP_WEIGHT_FRAME_PATH,
    FP_WEIGHT_TEST_ID,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
)
from ai_qa_test_studio.core.fingerprint import (
    score_match,
    should_persist_heal,
    should_run_heal,
)
from ai_qa_test_studio.core.models import ElementFingerprint


def test_test_id_match_dominates() -> None:
    expected = ElementFingerprint(test_id="login-btn", css="#other")
    candidate = ElementFingerprint(test_id="login-btn", css="#login")
    score = score_match(expected, candidate)
    assert score >= FP_WEIGHT_TEST_ID
    assert should_run_heal(score) or score >= FP_WEIGHT_TEST_ID


def test_role_name_and_css_stack() -> None:
    expected = ElementFingerprint(
        role="button",
        accessible_name="Sign in",
        css="#submit",
        text_sample="Sign in",
        test_id="submit",
        xpath="/html/body/button[1]",
    )
    candidate = ElementFingerprint(
        role="button",
        accessible_name="Sign in",
        css="#submit",
        text_sample="Sign in",
        test_id="submit",
        xpath="/html/body/button[1]",
    )
    score = score_match(expected, candidate)
    assert score >= HEAL_PERSIST_THRESHOLD
    assert should_persist_heal(score)


def test_weak_text_only_below_run() -> None:
    expected = ElementFingerprint(text_sample="submit")
    candidate = ElementFingerprint(text_sample="submit form")
    score = score_match(expected, candidate)
    assert score < HEAL_RUN_THRESHOLD
    assert not should_run_heal(score)


def test_frame_path_match_bonus_and_mismatch_penalty() -> None:
    expected = ElementFingerprint(
        test_id="pay",
        frame_path=["iframe#checkout"],
    )
    same = ElementFingerprint(test_id="pay", frame_path=["iframe#checkout"])
    other = ElementFingerprint(test_id="pay", frame_path=["iframe#ads"])
    assert score_match(expected, same) >= FP_WEIGHT_TEST_ID + FP_WEIGHT_FRAME_PATH
    assert score_match(expected, other) < score_match(expected, same)


def test_shadow_dom_flag_bonus() -> None:
    expected = ElementFingerprint(
        test_id="inner",
        attrs_subset={ATTR_IN_SHADOW_DOM: "true"},
    )
    shadow = ElementFingerprint(
        test_id="inner",
        attrs_subset={ATTR_IN_SHADOW_DOM: "true"},
    )
    light = ElementFingerprint(test_id="inner")
    assert score_match(expected, shadow) > score_match(expected, light)


def test_frame_path_coerces_legacy_csv_string() -> None:
    fp = ElementFingerprint.model_validate(
        {"tag": "button", "frame_path": "iframe#a, iframe#b"}
    )
    assert fp.frame_path == ["iframe#a", "iframe#b"]
