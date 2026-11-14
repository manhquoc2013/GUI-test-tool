from __future__ import annotations

from ai_qa_test_studio.constants import (
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
