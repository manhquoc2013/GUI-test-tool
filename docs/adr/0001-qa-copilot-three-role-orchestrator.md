# ADR 0001 — QA Copilot with default Planner / Composer / Critic

**Status**: Accepted (crew UI presentation refined by ADR 0002; shell by ADR 0003)  
**Date**: 2026-11-13  
**Spec**: `SPEC.md` (Phases 5 / 5.1; shell Phase 5.2)

## Context

AI Wizard (modal 3 bước) and a free-form chatbot would duplicate entry points. Users also want multi-agent quality (divide work, review) without unbounded agent-to-agent debate that blows token budgets and breaks Stop/Undo.

## Decision

1. **One QA Copilot** shell replaces Wizard as the primary AI entry (Compose + Case chat).
2. **Default orchestrator** for every user turn: **Planner → Composer → Critic**, with Critic limited to **one** review pass (optional single Composer revise, then stop).
3. Role handoffs are **structured artifacts**, not visible free-form multi-agent chat.
4. Dual-Loop / Healer / Ralph remain the **browser Run** path; Critic does not replace them.

## Consequences

- Predictable latency and cancel semantics; Undo can drop one user turn and all following role outputs.
- Token governance stays enforceable (sliding window + distilled DOM).
- Free-form swarm / multi-Critic loops are explicitly out of scope unless a future ADR supersedes this.
