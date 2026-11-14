# SPEC — s-review remediation #2

## Objective
Close the re-review blockers: restore bloated tracked `data/copilot/sessions.json` to `origin/main`, and add a regression test so removing dock `stop_channel` on narrow sidebar snap fails CI.

## Success
- `data/copilot/sessions.json` matches `origin/main` (no live Copilot dump in the branch tip).
- Test proves narrow mutual-exclusion snap cancels in-flight dock width anim (`on_done` never runs / registry cleared); deleting `stop_channel(...)` in `toggle_sidebar` makes that test fail.
- Gates: ruff / mypy / pytest green.

## Out of scope
- History rewrite of `40ee9ccbe`; push to remote.
