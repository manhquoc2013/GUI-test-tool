# Knowledge Distilled — `docs/knowledges/`

> Survey date: 2026-11-15  
> Method: `codegraph files` (structure) → `codegraph explore` / MCP `codegraph_explore` (architecture + symbols) → `codegraph context` (flow hints).  
> Scope: **reference clones only** — do not import into runtime; prefer patterns for AI QA Test Studio.

**Index note:** The workspace CodeGraph index covers ~90k files under `docs/knowledges/`. `codegraph context` often cross-hits symbols across clones (name collisions). Prefer **path-scoped `codegraph explore`** when tracing a specific project.

---

## 1. Catalog — 17 projects / modules

| # | Directory | Indexed files (approx.) | Stack | Role (one line) |
|---|-----------|-------------------------|-------|-----------------|
| 1 | `skyvern-main` | ~5.1k | Python + TS | LLM/CV browser automation (task + workflow) |
| 2 | `midscene-main` | ~2.1k | TypeScript monorepo | AI vision E2E agent (web/mobile/desktop) |
| 3 | `dify-main` | ~12.8k | Python API + Next web | LLM app / workflow / agent platform |
| 4 | `langgraph-main` | ~0.5k | Python libs | Stateful agent graph orchestration (Pregel) |
| 5 | `hermes-agent-main` | ~18.7k | Python + TUI/web | Self-improving multi-channel agent |
| 6 | `ruflo-main` | ~5.0k | TS/Rust plugins | Claude Code / Codex meta-harness (swarm) |
| 7 | `ECC-main` | ~1.3k | TS skills + Rust `ecc2` + Tk dashboard | Agent/skills/hooks packaging for IDEs |
| 8 | `Fabric-main` | ~0.4k | Go CLI + Svelte web | Prompt-pattern CLI / sequential workflows |
| 9 | `headroom-main` | ~2.2k | Python + Rust crates | Context compression + agent memory |
| 10 | `ai-memory` | ~0.6k | Rust crates | Durable memory store / recall / MCP |
| 11 | `automa-main` | ~0.4k | Browser extension (JS/Vue) | Block-based browser workflow engine |
| 12 | `open-webui-main` | ~1.3k | FastAPI + SvelteKit | Chat UI + model proxy backend |
| 13 | `orca` | ~36.6k | Electron + native | Multi-agent worktree orchestrator desktop |
| 14 | `testsigma-dev` | ~3.0k | Java (server/agent/UI) | Classic test automation platform |
| 15 | `markitdown-main` | ~0.1k | Python packages | Document → Markdown for LLM ingest |
| 16 | `loop-engineering-main` | ~0.1k | Docs + starters | Methodology: design the agent loop |
| 17 | `agency-agents-main` | ~4 code files* | Markdown personas | Ready-made agent role catalog |

\*Most of Agency is markdown under division folders; CodeGraph only indexes `scripts/`.

### Top-level trees (`codegraph files --max-depth 4`)

```
skyvern-main/     skyvern/, skyvern-frontend/, skyvern-ts/, alembic/, evaluation/
midscene-main/    packages/{core,web-integration,android,ios,computer,...}, apps/, examples/
dify-main/        api/, web/, dify-agent/, dify-agent-runtime/, packages/, docker/
langgraph-main/   libs/, docs/, examples/
hermes-agent-main/ agent/, gateway/, hermes_cli/, tools/, skills/, web/, ui-tui/
ruflo-main/       ruflo/, v3/, plugins/, plugin/, bin/
ECC-main/         src/, skills/, hooks/, ecc2/, agents/, ecc_dashboard.py
Fabric-main/      cmd/, internal/, web/, data/
headroom-main/    headroom/, crates/, plugins/, sdk/
ai-memory/        crates/, companions/, docs/
automa-main/      src/{workflowEngine,background,content,popup}/
open-webui-main/  backend/open_webui/, src/
orca/             src/{main,renderer,shared}/, native/, mobile/, cloud/
testsigma-dev/    server/, agent/, automator/, ui/, agent-launcher/
markitdown-main/  packages/markitdown{,-mcp,-ocr,-sample-plugin}/
loop-engineering-main/ docs/, patterns/, starters/, tools/, gate.yaml
agency-agents-main/ engineering/, design/, .../, scripts/
```

---

## 2. Architecture per project

### 2.1 Skyvern — browser agent platform

- **Layers:** API (`forge/sdk/routes`) → services (`task_v1_service`) → `ForgeAgent` (`forge/agent.py`) → webeye browser/CDP → artifacts/reporting.
- **Dual SDK:** Python `skyvern.library.skyvern.AsyncSkyvern` + TS `skyvern-ts/.../Skyvern`.
- **Workflow:** block graph in `forge/sdk/workflow/models/block.py` (e.g. `FileDownloadBlock`).

### 2.2 Midscene — AI vision E2E

- **Core:** `packages/core` (`Agent`, `Insight`, `TaskExecutor`, `ReportSession`).
- **Adapters:** `web-integration` (Playwright), `android`/`ios`, `computer-*`, `webdriver`.
- **Reporting:** dump → `ReportSession` / `ReportSourceRecorder` → visualizer / CLI report session.

### 2.3 Dify — LLM app platform

- **API:** Flask/FastAPI-style services under `api/`; models (`Workflow`, `Agent`); generate path via `AppGenerateService`.
- **Runtime:** `WorkflowAppRunner` / mode-specific generators (completion, agent_chat, workflow).
- **UI:** `web/` (Next); separate `dify-agent` + `dify-agent-runtime`.

### 2.4 LangGraph — graph runtime library

- **Authoring:** `StateGraph` (`graph/state.py`) → `compile()` → `CompiledStateGraph` / `Pregel`.
- **Execution:** Pregel channels, checkpoints, stream/invoke/batch.
- **CLI:** `libs/cli` for deploy/local runner helpers.

### 2.5 Hermes Agent — multi-channel agent

- **Entry:** `cli.py`, `hermes_cli/`, gateway platforms.
- **Core agent:** `AIAgent` in `run_agent.py` (mixin facade) → `TurnFacadeMixin.run_conversation` → `agent/conversation_loop.py`.
- **Supporting:** tools/, skills/, memory, compression, ACP adapter, TUI.

### 2.6 Ruflo — agent meta-harness

- **CLI V3:** `v3/@claude-flow/cli` (`ruflo` binary, lazy commands, hive-mind).
- **Orchestration:** swarm/GOAP planner (`plugins/ruflo-console/hooks/goap.ts`), `WorkflowEngine` under `v3/src/task-execution`.
- **Memory:** tiered store/recall (`v3/@claude-flow/memory`).

### 2.7 ECC — IDE agent kit

- **Packaging:** agents/, skills/, hooks/, workflows/, integrations for Cursor/Claude/etc.
- **Runtime CLI:** Rust `ecc2/src/main.rs` (sessions, harness eval, coordination).
- **UI:** Tk `ecc_dashboard.py` loads agents/skills/commands/rules.

### 2.8 Fabric — pattern CLI

- **Entry:** `internal/cli/cli.go` `Cli()` → chat or workflow.
- **Workflow:** YAML/JSON steps in `internal/cli/workflow.go` (`LoadWorkflow` → `runWorkflow`).
- **Assets:** patterns under `data/`; optional `web/` UI.

### 2.9 Headroom — context / memory

- **Compress API:** `headroom/compress.py`.
- **Memory facade:** `headroom/memory/easy.py` `Memory` (local SQLite or qdrant-neo4j).
- **Rust:** `crates/` (parity harness, etc.).

### 2.10 ai-memory — durable recall

- **CLI:** `crates/ai-memory-cli` (`Cli` + subcommands).
- **Serve/recall:** `crates/ai-memory-web` (`Recall` embedder + rerank).
- **Companions:** macOS menu app under `companions/`.

### 2.11 Automa — extension workflow engine

- **Engine:** `src/workflowEngine/WorkflowEngine.js` + `WorkflowWorker.js` + `WorkflowManager.js`.
- **Blocks:** handlers under `blocksHandler/`; content-script `executeBlock` in `content/index.js`.
- **Logging:** `addLogHistory` / states for UI reporting.

### 2.12 Open WebUI — chat frontend + backend

- **Backend entry:** `backend/open_webui/main.py` (FastAPI app, chat/proxy routes, middleware).
- **Models:** `models/chats.py`, tools, skills, retrieval.
- **Frontend:** SvelteKit `src/`.

### 2.13 Orca — desktop agent orchestrator

- **Process:** Electron `src/main` (runtime, persistence `Store`, agent sessions) + `src/renderer` UI.
- **Native hooks:** `native/windows-agent-hook`, etc.
- **Scale:** largest clone; many domains (terminal, browser pane, worktrees, mobile).

### 2.14 Testsigma — enterprise test platform

- **Server:** Spring `server/` — plans, `TestPlanResult`, APIs, JUnit reports.
- **Agent entry:** `agent/.../TestsigmaAgent.java` → Spring Boot + `automator`.
- **UI:** separate `ui/` SPA.

### 2.15 MarkItDown — doc conversion

- **Core:** `packages/markitdown/.../_markitdown.py` `MarkItDown.convert*`.
- **Plugins:** OCR, sample RTF, MCP server — `DocumentConverter` registry (runtime dispatch).

### 2.16 Loop Engineering — methodology

- **Not a runtime engine:** docs, `gate.yaml`, patterns, starters (e.g. changelog-drafter).
- **Idea:** constrain/score the agent loop rather than one-shot prompts.

### 2.17 Agency Agents — persona catalog

- **Divisions:** engineering, design, marketing, security, … (markdown agent specs).
- **Code:** install/lint/convert scripts only.

---

## 3. Main run flows (entry → orchestration → engine → reporting)

### 3.1 Skyvern (browser task)

```
HTTP POST /run/tasks/          skyvern/forge/sdk/routes/agent_protocol.py :: run_task
        ↓
task_v1_service.run_task       skyvern/services/task_v1_service.py
        ↓
ForgeAgent.execute_step        skyvern/forge/agent.py  (orchestration wrapper)
        ↓
ForgeAgent.agent_step          scrape → LLM plan → actions → results
        ↓
BrowserState / webeye CDP      skyvern/webeye/...
        ↓
Artifacts + Step/Task status   DetailedAgentStepOutput, DB updates, webhooks
```

**SDK shortcut:** `AsyncSkyvern.run_task` / TS `Skyvern.runTask` → wait for completion.

### 3.2 Midscene (AI act / query)

```
Agent.aiAct / aiQuery          packages/core/src/agent/agent.ts
        ↓
withReportOperation            report binding
        ↓
executeAiAct / Insight.aiQuery locate/plan/extract via TaskExecutor
        ↓
Platform adapter               web-integration / android / computer / ...
        ↓
ReportSession.finish           packages/core/src/dump/report-session.ts
        ↓
Visualizer / CLI report session
```

### 3.3 Dify (app generate / workflow)

```
AppGenerateService.generate    api/services/app_generate_service.py
        ↓
_dispatch_generate by AppMode  completion | agent_chat | workflow | ...
        ↓
*AppGenerator + WorkflowAppRunner   api/core/app/apps/workflow/app_runner.py
        ↓
Graph engine + node execution  (layers, queue_manager, streaming SSE)
        ↓
AppGenerateResponseConverter / stream events
```

### 3.4 LangGraph (library)

```
StateGraph.add_node/edge       libs/langgraph/langgraph/graph/state.py
        ↓
StateGraph.compile(...)        → CompiledStateGraph / Pregel
        ↓
invoke / stream / astream      libs/langgraph/langgraph/pregel/main.py
        ↓
Checkpointer + channels        (optional interrupt_before/after)
```

### 3.5 Hermes

```
cli.py / gateway platform
        ↓
AIAgent (run_agent.py mixins)
        ↓
TurnFacadeMixin.run_conversation → conversation_loop.run_conversation
        ↓
Tool calls / skills / memory / compression
        ↓
Activity snapshot + session persistence + stream delivery
```

### 3.6 Ruflo

```
CLI run()                      v3/@claude-flow/cli/src/index.ts
        ↓
Command (hive-mind / swarm / daemon…)
        ↓
GOAP plan() or WorkflowEngine  plugins/.../goap.ts ; v3/.../WorkflowEngine.ts
        ↓
Coordinator.executeTask + tiered memory store/recall
        ↓
Console/plugin Report types
```

### 3.7 Fabric

```
Cli(version)                   internal/cli/cli.go
        ↓
handleWorkflowProcessing | handleChatProcessing
        ↓
LoadWorkflow → runWorkflow     internal/cli/workflow.go  (sequential patterns)
        ↓
stdout / clipboard / file output
```

### 3.8 Automa

```
WorkflowManager / popup trigger
        ↓
WorkflowEngine.init            src/workflowEngine/WorkflowEngine.js
        ↓
WorkflowWorker.executeBlock    block handlers + content executeBlock
        ↓
addLogHistory + states         UI history / destroy status
```

### 3.9 Testsigma

```
TestsigmaAgent.main            agent/.../TestsigmaAgent.java
        ↓
Server schedules plan          server controllers/services
        ↓
Automator executes on device/browser
        ↓
TestPlanResult / TestDeviceResult / JUnit report APIs
```

### 3.10 MarkItDown

```
MarkItDown.convert(source)     packages/markitdown/.../_markitdown.py
        ↓
convert_local | convert_uri | convert_stream
        ↓
DocumentConverter.convert*     (plugin registry, OCR optional)
        ↓
DocumentConverterResult.markdown
```

### 3.11 Open WebUI / Orca / Headroom / ai-memory (summary)

| Project | Entry | Orchestration | Engine | Reporting / output |
|---------|-------|---------------|--------|--------------------|
| Open WebUI | `backend/open_webui/main.py` | routers + chat ownership | model proxy / tools / RAG | chat models, SSE streams |
| Orca | Electron main + renderer | runtime agent sessions / worktrees | host agents (Claude/Codex/…) | UI panes, status, persistence `Store` |
| Headroom | `compress()` / CLI | config + hooks | token/context transforms | `CompressResult`; `Memory.save/search` |
| ai-memory | `ai-memory` CLI / serve | ingest pipelines | embed + FTS + rerank `Recall` | ranked recall / MCP tools |
| ECC | `ecc2` CLI / dashboard | session StateStore | harness eval / coordination | eval reports; dashboard listings |
| Loop Eng. | `gate.yaml` / docs | human process | N/A (methodology) | loop scores / run logs |
| Agency | install scripts | N/A | N/A | installed agent markdown |

---

## 4. Quick-reference — core files & classes

### Browser / UI automation (highest relevance to GUI-test-tool)

| Path | Symbol / note |
|------|----------------|
| `docs/knowledges/skyvern-main/skyvern/forge/agent.py` | `ForgeAgent.execute_step`, `agent_step` |
| `docs/knowledges/skyvern-main/skyvern/forge/sdk/routes/agent_protocol.py` | `run_task`, `run_workflow` |
| `docs/knowledges/skyvern-main/skyvern/services/task_v1_service.py` | `run_task` |
| `docs/knowledges/skyvern-main/skyvern/library/skyvern.py` | `AsyncSkyvern` |
| `docs/knowledges/skyvern-main/skyvern-ts/client/src/library/Skyvern.ts` | TS client |
| `docs/knowledges/midscene-main/packages/core/src/agent/agent.ts` | `Agent.aiAct`, `aiQuery`, `aiAction` |
| `docs/knowledges/midscene-main/packages/core/src/agent/insight.ts` | `Insight` queries |
| `docs/knowledges/midscene-main/packages/core/src/dump/report-session.ts` | `ReportSession` |
| `docs/knowledges/midscene-main/packages/web-integration/src/playwright/ai-fixture.ts` | Playwright fixture |
| `docs/knowledges/automa-main/src/workflowEngine/WorkflowEngine.js` | `WorkflowEngine` |
| `docs/knowledges/automa-main/src/workflowEngine/WorkflowWorker.js` | `executeBlock` |
| `docs/knowledges/testsigma-dev/agent/.../TestsigmaAgent.java` | Agent process entry |
| `docs/knowledges/testsigma-dev/server/.../model/TestPlanResult.java` | Result aggregate |

### Agent orchestration / graphs

| Path | Symbol / note |
|------|----------------|
| `docs/knowledges/langgraph-main/libs/langgraph/langgraph/graph/state.py` | `StateGraph.compile` |
| `docs/knowledges/langgraph-main/libs/langgraph/langgraph/pregel/main.py` | `Pregel` |
| `docs/knowledges/dify-main/api/services/app_generate_service.py` | `AppGenerateService` |
| `docs/knowledges/dify-main/api/core/app/apps/workflow/app_runner.py` | `WorkflowAppRunner` |
| `docs/knowledges/dify-main/api/models/workflow.py` | `Workflow` |
| `docs/knowledges/dify-main/api/models/agent.py` | `Agent` ORM |
| `docs/knowledges/hermes-agent-main/run_agent.py` | `AIAgent` |
| `docs/knowledges/hermes-agent-main/agent/conversation_loop.py` | `run_conversation` |
| `docs/knowledges/hermes-agent-main/agent/turn_facade.py` | `TurnFacadeMixin` |
| `docs/knowledges/hermes-agent-main/cli.py` | CLI entry |
| `docs/knowledges/ruflo-main/v3/@claude-flow/cli/src/index.ts` | Ruflo CLI |
| `docs/knowledges/ruflo-main/plugins/ruflo-console/hooks/goap.ts` | `plan` (A*) |
| `docs/knowledges/ruflo-main/v3/src/task-execution/application/WorkflowEngine.ts` | swarm workflow engine |
| `docs/knowledges/Fabric-main/internal/cli/cli.go` | `Cli` |
| `docs/knowledges/Fabric-main/internal/cli/workflow.go` | `Workflow`, `runWorkflow` |
| `docs/knowledges/ECC-main/ecc2/src/main.rs` | `main` / harness eval |
| `docs/knowledges/ECC-main/ecc_dashboard.py` | `ECCDashboard` |

### Memory / context / ingest

| Path | Symbol / note |
|------|----------------|
| `docs/knowledges/headroom-main/headroom/compress.py` | `compress` |
| `docs/knowledges/headroom-main/headroom/memory/easy.py` | `Memory` |
| `docs/knowledges/ai-memory/crates/ai-memory-cli/src/cli.rs` | `Cli` |
| `docs/knowledges/ai-memory/crates/ai-memory-web/src/recall.rs` | `Recall` |
| `docs/knowledges/ruflo-main/v3/@claude-flow/memory/src/tiered-memory.ts` | `store` / `recall` |
| `docs/knowledges/markitdown-main/packages/markitdown/src/markitdown/_markitdown.py` | `MarkItDown` |

### Desktop / chat shells

| Path | Symbol / note |
|------|----------------|
| `docs/knowledges/open-webui-main/backend/open_webui/main.py` | FastAPI `app` |
| `docs/knowledges/open-webui-main/backend/open_webui/models/chats.py` | `Chat` |
| `docs/knowledges/orca/src/main/persistence/loading-store/store.ts` | main `Store` |
| `docs/knowledges/orca/src/main/runtime/` | agent/session runtime |

### Methodology / personas

| Path | Note |
|------|------|
| `docs/knowledges/loop-engineering-main/LOOP.md`, `gate.yaml` | Loop design contract |
| `docs/knowledges/agency-agents-main/README.md`, division folders | Agent persona library |
| `docs/knowledges/agency-agents-main/scripts/` | install/lint tooling |

---

## 5. Cross-cutting themes (for AI QA Test Studio)

| Theme | Primary references | Studio takeaway |
|-------|-------------------|-----------------|
| AI locate → act → assert | Midscene `Agent`/`Insight` | Align with Midscene MCP + heuristic fallback |
| Step loop + self-heal artifacts | Skyvern `execute_step`/`agent_step` | Fingerprint + retry resembles Skyvern scrape/plan/act |
| Graph / dual-loop orchestration | LangGraph Pregel; Dify `WorkflowAppRunner`; Ruflo GOAP | Planner/Composer/Critic crew patterns |
| Block workflow engines | Automa `WorkflowEngine`; Skyvern blocks; Fabric steps | YAML project tree + bound steps |
| Reporting dumps | Midscene `ReportSession`; Testsigma `TestPlanResult` | `.studio/` runtime + HTML/Excel reports |
| Context budget | Headroom compress; Hermes conversation_loop | Snapshot compression before LLM history |
| Doc ingest | MarkItDown converters | Doc corpus / coverage ingest |
| Agent packaging | ECC skills/hooks; Agency personas; Loop Eng. | `skills/` progressive SKILL.md catalog |
| Never vendor into runtime | All of `docs/knowledges/` | Per AGENTS.md: reference only |

---

## 6. How to re-query this corpus

```bash
# Structure of one clone
codegraph files --filter docs/knowledges/midscene-main --max-depth 4 --no-metadata

# Architecture / symbols (preferred)
codegraph explore "docs/knowledges/skyvern-main ForgeAgent execute_step agent_step" --max-files 12

# Task-oriented context (watch for cross-clone name collisions)
codegraph context -n 25 --no-code "StateGraph Pregel compile docs/knowledges/langgraph-main"
```

MCP equivalent: `codegraph_explore` with the same path-scoped queries.

---

## 7. Source evidence summary

- **Structure:** `codegraph files` over all 17 directories (depth 3–4).
- **Architecture & flows:** multiple `codegraph_explore` passes (Skyvern, Midscene, Dify, LangGraph, Hermes, Ruflo, Fabric, Automa, MarkItDown, Testsigma, Headroom, ai-memory, ECC, Orca).
- **Context passes:** `codegraph context` for each project (noisy across monorepo index; used as secondary hints).
- **README blurbs:** confirmed product intent for naming/role only.
