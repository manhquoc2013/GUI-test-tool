# Spec: QA Copilot Crew — Hybrid Debate + Clarify + Settings (Phase 5.1)

**Status**: ACTIVE  
**Supersedes**: Phase 5 Copilot 3-role (handoff/debate clauses)  
**Parent product**: AI QA Test Studio v4.0  
**ADR**: `docs/adr/0001-qa-copilot-three-role-orchestrator.md`, `docs/adr/0002-agent-crew-hybrid-debate-clarify.md`

## Objective

Nâng Copilot thành **crew** Planner→Composer→Critic với:

- Tóm tắt ngắn trên UI (≤160 ký tự); artifact dài nội bộ / Apply  
- Hội thoại agent↔agent có giới hạn + anti-loop  
- Popup clarify (gợi ý + tự nhập) mọi role; skip/timeout → mặc định  
- MCP nhúng zero-install; Settings hub đa API/đa model; router theo task×complexity  

**Success**: Mọi cognitive step qua `UnifiedLLMGateway`; bundled MCP sẵn khi app start; Settings CRUD connections/models; gates xanh.

## Decisions locked

| # | Decision |
|---|---|
| UI | Hybrid summaries; không dump full plan/critique vào thread |
| Debate | Max 3 rounds; loop fingerprint limit 2 → halt |
| Clarify | Any role; Future+dialog; default on skip/timeout |
| LLM | All cognitive via `stream_chat`; offline = gateway mock |
| MCP | In-process `qa-compress` / `qa-crew-memory` / `qa-project-context` |
| Settings | System Settings hub — full system + multi AI API + multi models |
| Router | Auto `(task, complexity)→tier→model`; manual pin overrides |
| Critic | Still one Critic pass; one Composer revise max (ADR 0001) |

## Pipeline

```text
User → classify complexity → Planner (LLM)
     → optional clarify → Composer → Critic
     → bounded debate (≤3) → optional revise → complete
```

Events: `status_summary`, `agent_message`, `clarify_request`, `clarify_resolved`, `loop_halted`, `apply_ready`, `stopped`, `pipeline_complete`.

## Architecture

```text
agent/
  orchestrator.py, model_router.py, complexity_classifier.py
  mcp_servers/ (bundled), bundled_mcp.py
  llm_gateway.py
core/models.py (AiConnection, AiModelEntry, AiRoutingSettings, …)
ui/widgets/
  system_settings_dialog.py, clarify_dialog.py, chat_pane.py
```

## Testing / Gates

`uv run ruff check src tests` · `uv run mypy src` · `uv run pytest`

## Boundaries

**In**: Crew UX, clarify, bundled MCP, Settings, router.  
**Out**: Unlimited swarm; replace Dual-Loop; require npx for crew MCP; auto-apply.
