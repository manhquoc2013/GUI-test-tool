# Learnings — Coordinator quality loop

- Agency Dev↔QA requires **critique_stale** after every compose/revise; gating on `ROLE_CRITIC in dispatched` silently skips re-QA.
- Settings `debateMaxRounds` maps to max Critic hops (`0` = skip Critic); deleted unused `_debate` path.
- Extract specialist hops to `crew_dispatch.dispatch_specialist(host, …)` — free function + thin orch wrapper avoids dataclass/mixin mypy pain and import cycles.
- `parse_coord_decision` must handle `clarify` when the prompt advertises it; soft-parse only dispatches when exactly one specialist name appears.
