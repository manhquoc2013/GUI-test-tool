# SPEC — Coordinator quality loop remediation

## Objective
Close `/s-review` Must/Should/Nice gaps so the coordinator-led crew matches agency-agents Dev↔QA: Critic FAIL → Composer revise → Critic again until PASS or debate budget exhausted; parse `clarify`; remove dead `_debate`; wire Settings `debateMaxRounds`; shrink dispatch surface; theme accent for coordinator.

## Assumptions (bound — no ask_question tool)
1. Keep Settings `debateMaxRounds`; map to max Critic rounds (incl. re-critique after revise). `0` = skip Critic.
2. Soft-parse: accept `complete` keyword; role-name-only dispatch only if exactly one specialist mentioned; else fallback.
3. Extract specialist dispatch out of `orchestrator.py` into `crew_dispatch.py` (orchestration stay on orch).
4. No git commits (repo user rule); verification via ruff/mypy/pytest only.

## Out of scope
- Vendoring ECC/Ruflo/agency; Dual-Loop run/heal; live LLM quality tuning.

## Commands
```bash
uv run ruff check src tests
uv run mypy src
uv run pytest tests/unit/test_orchestrator.py tests/unit/test_complexity_classifier.py -q
uv run pytest -q
```

## Success criteria
- After REVISE + revise, next offline hop is Critic (not complete).
- `parse_coord_decision` returns `clarify` when JSON says so.
- `_debate` gone; `debate_max_rounds` caps Critic hops.
- Coordinator accent in ThemeTokens dark/light.
- Gates green; unit tests cover revise→re-Critic + clarify parse.
