"""Four host-side builtin SkillSpecs (IntentType from core.enums — no router import)."""

from __future__ import annotations

from ai_qa_test_studio.agent.skill_auto_matcher import SkillSpec
from ai_qa_test_studio.core.enums import IntentType

BUILTIN_SKILLS: tuple[SkillSpec, ...] = (
    SkillSpec(
        skill_id="scenario_designer",
        intents=frozenset({IntentType.TEST_SCENARIO, IntentType.RESEARCH}),
        keywords=frozenset(
            {
                "scenario",
                "kịch bản",
                "happy",
                "negative",
                "boundary",
                "permission",
                "state",
            }
        ),
        workspace_hints=frozenset({"scenario", "srs", "requirement"}),
        prompt_body=(
            "STAGE isolation — TEST_SCENARIO only: design Happy/Negative/Boundary/"
            "State/Permission coverage as a compact Markdown table of high-level rows "
            "(columns: code | title | category | requirement_ref | expected_outcome). "
            "Do NOT nest cases or detailed steps. Prefer 12–18 highest-value rows. "
            "Prefer ISO coverage language. When a source URL is provided with page "
            "structure, ground rows in that structure; if only a browser-off note is "
            "present, state assumptions briefly."
        ),
    ),
    SkillSpec(
        skill_id="iso29119_case_author",
        intents=frozenset({IntentType.TEST_CASE_GEN}),
        keywords=frozenset(
            {"test case", "tc", "bva", "iso", "29119", "step", "precondition"}
        ),
        workspace_hints=frozenset({"case", "steps", "test_data"}),
        prompt_body=(
            "STAGE isolation — TEST_CASE_GEN only: author ISO 29119-aligned TestCase "
            "and steps for the specified TS codes (max 5–8 cases per turn). "
            "Use UNBOUND steps and ${var} data. Do not invent CSS/XPath selectors; "
            "leave binding for EXEC_AND_BIND. Do not redesign the high-level coverage list. "
            "LANGUAGE LOCK: if the user writes Vietnamese, write ALL of title, "
            "preconditions, step.intent_hint, step.target, and expected_result in "
            "Vietnamese (keep technical tokens like model names, UTC, OCR). "
            "PRIORITY: classify P1 - High (core path), P2 - Medium (boundary/legacy/"
            "notes), P3 - Low (cosmetic) — never force every case to P1. "
            "SCOPE: website URL ⇒ browser UI/content cases; backend/API ⇒ "
            "test_type=API. Each UI case needs ≥2–3 steps: navigate → interact/"
            "locate → assert. "
            "Actions: navigate|click|fill|select|hover|scroll|wait|assert|api_request "
            "(Navigate→navigate, Scroll→scroll, Read/Inspect/Compare/Check→assert, "
            "Send request→api_request; click only for real mouse presses). "
            "UNBOUND steps must set target to a semantic UI label "
            "(e.g. 'Bảng giá PRICING') and put every input/threshold into "
            "input_data (e.g. model=deepseek-flash; expected_price=$0.003). "
            "preconditions is a string or string[] — never character-split."
        ),
    ),
    SkillSpec(
        skill_id="playwright_python_coder",
        intents=frozenset({IntentType.TEST_SCRIPT_GEN}),
        keywords=frozenset({"python", "pytest", "playwright", "script", "codegen"}),
        workspace_hints=frozenset({"python", ".py", "pytest"}),
        prompt_body=(
            "Emit Playwright Python (pytest-compatible). Prefer get_by_role / test_id. "
            "When fully bound, mirror SharedElement locators exactly."
        ),
    ),
    SkillSpec(
        skill_id="playwright_csharp_coder",
        intents=frozenset({IntentType.TEST_SCRIPT_GEN}),
        keywords=frozenset({"csharp", "c#", "nunit", "xunit", "playwright"}),
        workspace_hints=frozenset({"csharp", ".cs", "nunit"}),
        prompt_body=(
            "Emit Playwright C# (NUnit/xUnit style). Prefer role/test_id locators. "
            "When fully bound, mirror SharedElement locators exactly."
        ),
    ),
)


def builtin_skill_catalog() -> list[SkillSpec]:
    return list(BUILTIN_SKILLS)
