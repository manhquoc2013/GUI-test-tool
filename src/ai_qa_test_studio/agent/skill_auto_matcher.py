"""Host-side SkillAutoMatcher — IntentType from enums; no import of intent_router."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from ai_qa_test_studio.constants import (
    SKILL_MATCH_MAX_PER_TURN,
    SKILL_MATCH_MIN_SCORE,
    SKILL_MATCH_WEIGHT_INTENT,
    SKILL_MATCH_WEIGHT_KEYWORD,
    SKILL_MATCH_WEIGHT_WORKSPACE,
)
from ai_qa_test_studio.core.enums import IntentType

_SKILL_PIN_RE = re.compile(r"@skill\s*[:=]?\s*([a-z0-9_-]+)", re.IGNORECASE)


@dataclass(frozen=True)
class SkillSpec:
    skill_id: str
    intents: frozenset[IntentType]
    keywords: frozenset[str] = field(default_factory=frozenset)
    workspace_hints: frozenset[str] = field(default_factory=frozenset)
    prompt_body: str = ""


@dataclass(frozen=True)
class SkillMatch:
    skill: SkillSpec
    score: float
    pinned: bool = False


class SkillAutoMatcher:
    def __init__(
        self,
        skills: list[SkillSpec] | None = None,
        *,
        min_score: float = SKILL_MATCH_MIN_SCORE,
        max_per_turn: int = SKILL_MATCH_MAX_PER_TURN,
    ) -> None:
        self.skills = list(skills or [])
        self.min_score = min_score
        self.max_per_turn = max_per_turn

    def match(
        self,
        prompt: str,
        intent: IntentType,
        *,
        workspace_signals: list[str] | None = None,
        pinned_skill_ids: list[str] | None = None,
    ) -> list[SkillMatch]:
        text = (prompt or "").lower()
        pins = set(pinned_skill_ids or [])
        for hit in _SKILL_PIN_RE.findall(prompt or ""):
            pins.add(hit.lower())
        workspace = " ".join(workspace_signals or []).lower()

        scored: list[SkillMatch] = []
        for skill in self.skills:
            pinned = skill.skill_id.lower() in pins
            intent_score = 1.0 if intent in skill.intents else 0.0
            kw_hits = sum(1 for k in skill.keywords if k.lower() in text)
            keyword_score = min(1.0, kw_hits / max(1, min(3, len(skill.keywords) or 1)))
            ws_hits = sum(1 for h in skill.workspace_hints if h.lower() in workspace)
            workspace_score = min(1.0, ws_hits / max(1, min(2, len(skill.workspace_hints) or 1)))
            score = (
                SKILL_MATCH_WEIGHT_INTENT * intent_score
                + SKILL_MATCH_WEIGHT_KEYWORD * keyword_score
                + SKILL_MATCH_WEIGHT_WORKSPACE * workspace_score
            )
            if pinned:
                score = max(score, 1.0)
            if pinned or score >= self.min_score:
                scored.append(SkillMatch(skill=skill, score=round(score, 4), pinned=pinned))

        scored.sort(key=lambda m: (m.pinned, m.score), reverse=True)
        # Keep pins first, then top scores — max N
        selected: list[SkillMatch] = []
        for item in scored:
            if len(selected) >= self.max_per_turn:
                break
            selected.append(item)
        return selected

    def overlay_prompt(self, prompt: str, matches: list[SkillMatch]) -> str:
        if not matches:
            return prompt
        blocks = [f"[skill:{m.skill.skill_id}]\n{m.skill.prompt_body}" for m in matches]
        return "\n\n".join(blocks) + "\n\n---\n" + (prompt or "")
