# Master Refactoring Roadmap — Hợp nhất Agents + Playwright MCP

> Role: Principal AI & Browser Automation Systems Architect  
> Date: 2026-11-15 (rev: **v4.2** — case_binding chỉ đếm bindable UI steps; loại Navigate/Wait/AssertUrl)  
> **Implementation status (2026-11-15):** Sprint 1–4 landed in `src/` — contracts/mask/supervisor; SnapshotDistiller+dual heal; Hybrid Intent (no cycle)+Compactor pin+APPLY schema; Evidence+RealityChecker+SWARM_AUDIT. `MCP_ACTIONS_ENABLED` remains `False` (DualLoop MCP-first path ready behind flag).  
> Survey: CodeGraph `codegraph_explore` on `src/ai_qa_test_studio/**` (bỏ `docs/knowledges/` khi audit runtime). Pattern từ knowledges chỉ tham chiếu — **không vendor**.  
> Companion plan: `.cursor/plans/agents_orchestration_design_0b9cdc75.plan.md`

**MCP tool note:** Namespace hiện chỉ expose `codegraph_explore`. Không có tool MCP riêng `codegraph_context` / `codegraph_callers` — dùng explore với tên symbol để lấy blast radius / callers.

---

## Executive verdict

Studio đã có xương sống đúng (UNBOUND↔BOUND, DomDistiller short-id, Healer 2 tầng, ExternalMcpBridge, DualLoop cancel, ContextCompressor/ChatRunRegistry, Copilot crew) nhưng execution path chính vẫn in-process `page`, thiếu contracts/middleware ISO, và agents topology cần Evidence runtime.

Lộ trình **4 Sprint** + design-review invariants + **Hybrid Intent Orchestrator (v4)** — app vẫn chạy sau mỗi Sprint. Feature flag: `MCP_ACTIONS_ENABLED=False` đến hết Sprint 3.

---

## Design-review invariants (khóa trước Sprint 1)

| # | Invariant | Áp dụng |
|---|-----------|---------|
| 1 | **SecretMasker outbound-only** | Raw `args` luôn vào Playwright/`call_tool`. Mask chỉ: logs, UI signals, `StepResult`/`RunReport`, LLM history, evidence disk. |
| 2 | **`ElementFingerprint` ≠ `DistilledNode`** | Fingerprint bền → YAML/SharedElement. DistilledNode tạm (short_id, parent, sibling, bbox) chỉ trong snapshot RAM. |
| 3 | **Dual heal threshold** | `HEAL_RUN_THRESHOLD=0.65` → StepStatus.Healed + chạy tiếp. `HEAL_PERSIST_THRESHOLD=0.85` → mới auto-patch SharedElement/YAML. Dưới 0.85: audit warning, tester duyệt. |
| 4 | **Supervisor key + shiboken + threadsafe + lazy loop** | `__init__`: `self._loop: AbstractEventLoop \| None = None` — **không** gọi `get_running_loop()` lúc construct (tránh `RuntimeError` khi instance module-level trước qasync). `bind_loop()` gán loop. `register(...)`: nếu `_loop is None` → tự `bind_loop()`. `replace=True` hủy phiên cũ cùng key. `emit_if_alive`: worker → `call_soon_threadsafe` → `shiboken6.isValid` → emit. |
| 5 | **ContextCompactor pin** | Luôn giữ: `[System + TestCase gốc] + [snapshot đầy đủ mới nhất] + [2 tool call/result cuối]`. Stub `browser_snapshot` chỉ từ **t-2** trở về trước. |
| 6 | **Fingerprint/DistilledNode trong `models.py`** | Định nghĩa class trong `core/models.py` (SharedElement cần `ElementFingerprint`). `core/contracts.py` **chỉ re-export**. `models.py` **không bao giờ** `import contracts`. |
| 8 | **Intent 2-tier** | Tầng 1 Rule/UI/slash (&lt;2ms, 0 token) → Tầng 2 Micro-Router Thinking OFF chỉ khi ambiguous. |
| 9 | **DRAFT vs APPLY** | DRAFT mặc định. APPLY S3: `validate_case_schema` (Pydantic). APPLY S4: RealityChecker swarm. |
| 10 | **SkillAutoMatcher** | Max 2; Score 0.45/0.30/0.25; turn overlay; `@skill` pin. |
| 11 | **ThinkingPolicy + escalate** | MEDIUM→HIGH khi RealityChecker fail / heal Tier1 &lt; 0.65 / DOM_CHANGED. |
| 12 | **No circular import Intent** | `IntentType`/`DeliveryMode`/`ThinkingLevel` trong `core/enums.py`. Router **không** gọi matcher; orchestrator `route` rồi mới `match`. |
| 13 | **`case_binding_summary`** | Mẫu số = `total_bindable_steps` (enabled + cần target UI). **Loại** Navigate/goto, Wait, AssertUrl khỏi mẫu số. Deterministic chỉ khi `is_fully_bound` trên tập bindable; partial → LLM + fingerprints. |
| 14 | **Micro-Router fallback** | Timeout 3–4s; confidence &lt; 0.6 hoặc lỗi → `CHAT_FAST` + Thinking LOW. |

---

## Phần A — Interface Contracts (hợp nhất)

### A.1 Hub chống circular import

**File mới:** `src/ai_qa_test_studio/core/contracts.py`

| Symbol | Định nghĩa | Impl / nguồn |
|--------|------------|--------------|
| `StepStatus` | StrEnum: Queued, Running, Passed, Failed, Healed, Blocked, Skipped | `core/enums.py` |
| `ElementFingerprint` | **Bền** — không short_id (xem A.2) | **`core/models.py`** (class body) |
| `DistilledNode` | **Tạm** — short_id + nested fingerprint | **`core/models.py`** (class body) |
| `CaseStatus` / `BindingStatus` / `TestCase` / `TestStep` / `SharedElement` / `StepResult` / … | Class/Literal | `core/models.py` |
| Re-exports trên | Re-export only | `core/contracts.py` |
| `SecretMaskerProtocol` | Outbound mask | `core/secret_masker.py` |
| `MCPToolInterceptorProtocol` | before/after hooks | SelfHealing + SmartExecutor |
| `AsyncTaskSupervisorProtocol` | `__init__` `_loop=None`; `bind_loop`; `register` auto-bind nếu None; `emit_if_alive` (threadsafe) | `core/async_task_supervisor.py` |
| Scoring helpers | `score_match` | `core/fingerprint.py` (imports Fingerprint từ models/contracts) |

**Quy tắc import (khóa):**

```text
constants  →  (no agent/browser)
enums      →  (stdlib)
models     →  constants (+ enums nếu cần)
             ★ định nghĩa ElementFingerprint, DistilledNode
             ★ KHÔNG import contracts
contracts  →  enums + models   (re-export + Protocols only)
fingerprint / secret_masker / async_task_supervisor  →  contracts (hoặc models)
browser/*  →  contracts
agent/*    →  contracts, browser, mcp_*
reporting/* →  contracts
ui/*       →  agent, reporting, supervisor
```

### A.2 Schema khóa

```text
ElementFingerprint:   # persistent — YAML / SharedElement / Git-friendly
  tag, role, accessible_name, text_sample,
  test_id, css, xpath, frame_path,
  attrs_subset (type, name, placeholder, aria-*)

DistilledNode:        # ephemeral — one snapshot only
  short_id, parent_short_id, sibling_index,
  bbox_norm?,
  fingerprint: ElementFingerprint

StepResult (RunStepResult evolved):
  step_no, action, status (legacy CaseStatus compat),
  step_status: StepStatus,
  duration_ms, message,
  previous_selector?, confidence_score?,
  fingerprint?: ElementFingerprint,   # never DistilledNode.short_id
  screenshot_path?, screenshot_b64?,
  audit?: StepAuditEntry
```

### A.3 Middleware order (4 trụ) — đã chỉnh mask

1. **call_tool nhận raw args** — SecretMasker **không** mutate inbound.  
2. **Mask outbound** trước log / UI / StepResult / LLM history / evidence write.  
3. **Distill trước** heal & LLM — DistilledNode tree; heal so khớp qua `fingerprint`.  
4. **Heal trước** retry class — STALE_REF → interceptor; persist YAML chỉ nếu ≥ `HEAL_PERSIST_THRESHOLD`.  
5. **Supervisor owns cancel** — key+replace; DualLoop + MCP + exporters + Copilot.

### A.4 CodeGraph evidence (src only)

| Finding | Evidence |
|---------|----------|
| DualLoop dùng `page` trực tiếp | `dual_loop_agent.run_case` → `execute_step(self.page)` / `heal_step(self.page)` |
| Callers DualLoop | `ui/session_controller.py` |
| MCP passthrough | `ExternalMcpSession.call_tool` — chưa mask outbound / taxonomy |
| Snapshot bloat | `DomDistiller.extract` append full aria rồi truncate |
| Status thiếu step ISO | `RunStepResult.status: CaseStatus` — chưa StepStatus |
| Thiếu symbols | Không SecretMasker / ElementFingerprint / AsyncTaskSupervisor / DistilledNode |

---

## Phần B — Hybrid Intent Orchestrator (chatbot + ISO pipeline)

Ba lớp (Sprint 3 wire; S4 hoàn thiện APPLY/swarm):

1. **HybridIntentRouter 2 tầng** + khóa **DRAFT/APPLY**
2. **ThinkingPolicy** động + auto-escalate
3. **SkillAutoMatcher** host-side (max 2 skills / turn)

### B.1 IntentType matrix

| Intent | Thinking | Path | MCP | Default delivery |
|--------|----------|------|-----|------------------|
| `CHAT_FAST` | OFF/LOW | Fast-path LLM + Compactor; skip Playwright | none | direct |
| `RESEARCH` | HIGH | Doc/DOM read-only (SnapshotDistiller) | observe/doc RO | report |
| `TEST_SCENARIO` | HIGH | Happy/Negative/Boundary/State/Permission | project_context RO | **DRAFT** |
| `TEST_CASE_GEN` | MED→HIGH | ISO 29119 UNBOUND steps; RealityChecker khi APPLY | — | **DRAFT** |
| `TEST_SCRIPT_GEN` | LOW/MED; OFF chỉ khi **is_fully_bound** | Fully bound → deterministic exporter; partial → LLM + fingerprints | ScriptExporter / LLM | **DRAFT** |
| `EXEC_AND_BIND` | MED (→HIGH khi heal) | DualLoop + SmartExecutor (0.65/0.85) | Playwright/Midscene | execute |
| `SWARM_AUDIT` | HIGH | Evidence/Traceability/RealityChecker trên RunReport | Evidence RO | audit |

**Tầng 1:** slash `/chat|/research|/scenario|/tc|/script|/run|/audit`, UI context, keywords (&lt;2ms, 0 token).  
**Tầng 2:** Micro-Router Thinking OFF → JSON `{intent, delivery_mode, confidence}`; **timeout 3–4s**; confidence &lt; 0.6 / lỗi / timeout → fallback **`CHAT_FAST` + Thinking LOW**.  
**APPLY:** regex hoặc nút Apply → S3 `validate_case_schema` → S4 RealityChecker.  
**Import graph Intent:** `enums` ← `intent_router` / `skill_auto_matcher` / `builtin_skills`; orchestrator gọi `route` rồi `match` (router không import matcher).  
**`case_binding_summary(case)`:** đếm `total_bindable_steps` = enabled steps cần target UI (click/fill/select/…); **không** đếm Navigate/goto, Wait, AssertUrl. `is_fully_bound` / `is_partial` / `is_unbound` trên tập bindable; deterministic script **chỉ** khi fully bound trên tập đó.

### B.2 ThinkingPolicy

| Level | Budget | Dùng cho |
|-------|--------|----------|
| OFF | 0 | CHAT_FAST đơn giản, Micro-Router, BOUND→script |
| LOW | 512–1024 | CHAT_FAST + log; reformat TC |
| MEDIUM | 2048–4096 | TEST_CASE_GEN, SCRIPT tự do, EXEC thường |
| HIGH | 8192+ | SCENARIO, RESEARCH, SWARM_AUDIT; escalate |

`to_provider_kwargs`: DeepSeek/OpenAI `reasoning_effort`; Anthropic/Gemini `thinking_budget`.  
Escalate MEDIUM→HIGH: RealityChecker fail / heal Tier1 &lt; 0.65 / DOM_CHANGED.

### B.3 SkillAutoMatcher + builtin_skills

`Score = 0.45·Intent + 0.30·Keyword + 0.25·Workspace`; `min_score=0.45`; max 2; `@skill` pin.  
Inject `prompt_body` vào turn overlay only.

| Skill | Intents |
|-------|---------|
| `scenario_designer` | TEST_SCENARIO, RESEARCH |
| `iso29119_case_author` | TEST_CASE_GEN (UNBOUND, `${var}`) |
| `playwright_python_coder` | TEST_SCRIPT_GEN |
| `playwright_csharp_coder` | TEST_SCRIPT_GEN |

Files: ★ `agent/intent_router.py`, ★ `agent/skill_auto_matcher.py`, ★ `agent/builtin_skills.py`, ★ helper `case_binding_summary`, ★ `validate_case_schema` (S3), ● `orchestrator.py`.

### B.4 run_turn

```text
supervisor.register(key=copilot:{session_id}, replace=True)
→ route_fast | micro_router(timeout→CHAT_FAST)
→ case_binding_summary (nếu SCRIPT)
→ matcher.match(prompt, decision.intent, ...)   # sau route, không trong router
→ ContextCompactor(pin) → inject ≤2 skills
→ LLM / DualLoop / deterministic Exporter
→ mask outbound; DRAFT | APPLY→validate_case_schema(S3)|RealityChecker(S4) → tree_ops
```

### B.5 Map Sprint

| Capability | Sprint |
|------------|--------|
| contracts + DistilledNode + Intent enums + mask + supervisor | S1 |
| SnapshotDistiller + dual heal | S2 |
| SmartExecutor + Compactor + Intent/Thinking/Skills + schema APPLY + MCP warm | S3 |
| Evidence + UI + SWARM_AUDIT + RealityChecker APPLY + MCP-first | S4 |

---

## Phần C — Target architecture

```text
PySide6 UI  ←Signals→  AsyncTaskSupervisor (lazy loop, key+replace, shiboken)
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
   HybridIntentRouter    DualLoopAgent   ReportExporter
   + ThinkingPolicy      (run / heal)    (to_thread)
   + SkillAutoMatcher
              │               │
              ▼               ▼
              ┌──────── MCP Host Middleware ────────┐
              │  SecretMasker (OUTBOUND ONLY)       │
              │  SnapshotDistiller → DistilledNode  │
              │  SelfHealing (0.65 run / 0.85 persist)│
              │  ContextCompactor (pin latest+2)    │
              │  SmartMCPToolExecutor (raw args in) │
              └───────────────┬─────────────────────┘
                              │ JSON-RPC (warm at boot)
                              ▼
                    Playwright / Midscene MCP  ←CDP→ single Chromium
                              │
                              ▼
              ISO StepResult + EvidenceCollector → .studio/runs/
              DRAFT preview  /  APPLY → RealityChecker → YAML
```

**Migration:** S2–S3 giữ BrowserEngine CDP; `MCP_ACTIONS_ENABLED` đến hết S3; S4 DualLoop default = MCP.

---

## Phần D — 4 Sprint

### Sprint 1 — ISO 29119 Models + SecretMasker + AsyncTaskSupervisor

**Goal:** Contracts & lifecycle; UX không đổi.

| Order | Create / Modify | Work |
|------:|-----------------|------|
| 1 | ★ `core/enums.py` | `StepStatus` + **`IntentType` / `DeliveryMode` / `ThinkingLevel`** (lá cho router/matcher) |
| 2 | ● `core/models.py` | **Định nghĩa** `ElementFingerprint` + `DistilledNode`; SharedElement.fingerprint?; evolve StepResult |
| 3 | ★ `core/contracts.py` | Re-export models/enums + Protocols — **không** định nghĩa Fingerprint/DistilledNode |
| 4 | ● `constants.py` | Mask patterns; heal threshold stubs; supervisor defaults |
| 5 | ★ `core/secret_masker.py` | Outbound-only; test: fill password args không đổi |
| 6 | ★ `core/async_task_supervisor.py` | `__init__`: `_loop=None` (không get_running_loop lúc import); `bind_loop()`; `register` tự bind nếu None + `key`/`replace=True`; `emit_if_alive`: worker → `call_soon_threadsafe` → shiboken → emit |
| 7 | ● `core/chat_run_registry.py` | Key `copilot:{session_id}`, replace=True |
| 8 | ● `agent/mcp_client.py` | **Raw args** → `call_tool`; mask **bản sao** khi log / serialize LLM |
| 9–12 | ★ unit tests | secret_masker; supervisor (replace + **threadsafe emit**); step_status; contracts_imports (**models ↛ contracts**) |

**Verify:**

```bash
uv run ruff check src/ai_qa_test_studio/core src/ai_qa_test_studio/agent/mcp_client.py
uv run mypy src/ai_qa_test_studio/core
uv run pytest tests/unit/test_secret_masker.py \
  tests/unit/test_async_task_supervisor.py \
  tests/unit/test_step_status_models.py \
  tests/unit/test_contracts_imports.py \
  tests/unit/test_mcp_client.py -q
```

**Exit:** App launch; Copilot cancel OK; login fill vẫn nhận password thật qua MCP path.

---

### Sprint 2 — SnapshotDistiller + SelfHealingInterceptor (dual threshold)

**Goal:** Lean snapshot; heal có confidence; **không** đè YAML khi score thấp.

| Order | Create / Modify | Work |
|------:|-----------------|------|
| 1 | ★ `browser/snapshot_distiller.py` | Emit `list[DistilledNode]`; không aria dump mặc định |
| 2 | ● `browser/dom_distiller.py` | Wrapper → SnapshotDistiller |
| 3 | ★ `core/fingerprint.py` | `score_match`; weights test_id ≫ role+name ≫ css ≫ xpath ≫ text |
| 4 | ● `constants.py` | `HEAL_RUN_THRESHOLD = 0.65`, `HEAL_PERSIST_THRESHOLD = 0.85` |
| 5 | ● `agent/healer.py` | SelfHealingInterceptor; ≥0.65 → Healed continue; luôn ghi confidence vào audit |
| 6 | ● `agent/auto_binder.py` | Persist SharedElement/YAML **chỉ nếu** ≥ 0.85; else audit warning |
| 7 | ● `agent/dual_loop_agent.py` | Interceptor + StepStatus; supervisor cancel by key |
| 8 | ● `agent/external_mcp_bridge.py` | BOUND patch chỉ khi ≥ persist threshold |
| 9 | ● `browser/locator_resolver.py` | Enrich fingerprint bền nếu cần |
| 10–12 | ★/● tests | distill, fingerprint, heal thresholds (0.70 no YAML / 0.90 patch), bind_heal_flow |

**Verify:**

```bash
uv run ruff check src/ai_qa_test_studio/browser \
  src/ai_qa_test_studio/agent/healer.py \
  src/ai_qa_test_studio/agent/auto_binder.py
uv run pytest tests/unit/test_snapshot_distiller.py \
  tests/unit/test_fingerprint_score.py \
  tests/unit/test_self_healing_interceptor.py \
  tests/integration/test_bind_heal_flow.py -q
```

**Exit:** Bind/heal green; confidence 0.70 không đè YAML; snapshot ≪ aria dump.

---

### Sprint 3 — ContextCompactor + SmartMCPToolExecutor + Hybrid Intent Orchestrator

**Goal:** MCP Host; pin compact; raw args; warm MCP; Intent 2-tier (no import cycle); fully_bound script gate; APPLY Pydantic; micro-router fallback CHAT_FAST.

| Order | Create / Modify | Work |
|------:|-----------------|------|
| 1 | ★ `agent/mcp_error_taxonomy.py` | TRANSIENT / STALE_REF / BAD_ARGS / DOM_CHANGED / FATAL |
| 2 | ★ `agent/mcp_tool_executor.py` | **Raw args in** → call → heal → retry; mask chỉ log/history |
| 3 | ● `agent/mcp_client.py` | Executor wrapper; reconnect TRANSIENT |
| 4 | ● `core/token_budget.py` | ContextCompactor pin rules |
| 5 | ★ `agent/intent_router.py` | HybridIntentRouter 2-tier; **không** import/gọi matcher; micro timeout+fallback CHAT_FAST |
| 6 | ★ `agent/skill_auto_matcher.py` | SkillSpec + matcher; IntentType từ `core/enums` |
| 7 | ★ `agent/builtin_skills.py` | 4 skills; IntentType từ enums |
| 8 | ★ helper `case_binding_summary` | `total_bindable_steps` (exclude Navigate/Wait/AssertUrl); `is_fully_bound` / partial / unbound |
| 9 | ★ `validate_case_schema` | Pydantic tối thiểu cho APPLY S3 |
| 10 | ● `agent/orchestrator.py` | `route` rồi `match`; SCRIPT theo fully_bound; APPLY→schema; CHAT_FAST skip MCP |
| 11 | ● skill_loader + copilot UI | Slash; Apply→APPLY; `@skill` |
| 12 | ● DualLoop + MCP warm + qa_playwright to_thread | như trước |
| 13 | ★/● tests | taxonomy; executor; compactor; router fallback; **no circular import**; fully_bound vs partial; schema APPLY; matcher; thinking |

**Verify:**

```bash
uv run ruff check src/ai_qa_test_studio/agent src/ai_qa_test_studio/core/enums.py
uv run mypy src/ai_qa_test_studio/agent
uv run pytest tests/unit/test_mcp_error_taxonomy.py \
  tests/unit/test_smart_mcp_tool_executor.py \
  tests/unit/test_context_compactor_snapshots.py \
  tests/unit/test_intent_router.py \
  tests/unit/test_skill_auto_matcher.py \
  tests/unit/test_thinking_policy.py \
  tests/unit/test_case_binding_summary.py \
  tests/unit/test_validate_case_schema.py \
  tests/unit/test_mcp_client.py \
  tests/unit/test_orchestrator.py -q
```

**Exit:** Không circular import; "hi"/timeout → CHAT_FAST; partial bind không deterministic exporter; APPLY S3 qua Pydantic; CHAT_FAST không mở browser; fill password raw.

---

### Sprint 4 — EvidenceCollector + ReportExporter + UI Signals + SWARM_AUDIT

**Goal:** Evidence; export off-thread; DualLoop MCP-first; RealityChecker trên APPLY; SWARM_AUDIT.

| Order | Create / Modify | Work |
|------:|-----------------|------|
| 1 | ★ `reporting/evidence.py` | EvidenceCollector → `.studio/runs/<run_id>/` (mask khi ghi) |
| 2 | ★ `reporting/report_service.py` | `async export_all` → `asyncio.to_thread` |
| 3–4 | ● excel_exporter / html_reporter | Prefer `screenshot_path` |
| 5 | ● UI session/widgets | supervisor keys; Stop; threadsafe emit; **Apply to Project** → DeliveryMode.APPLY |
| 6 | ● `agent/dual_loop_agent.py` | MCP-first; EXEC_AND_BIND; evidence paths |
| 7 | ● YAML / project_tree_ops | Persist Fingerprint only; APPLY gate |
| 8 | ● RealityChecker + SWARM_AUDIT | Thay/đè `validate_case_schema` S3; Evidence/Traceability swarm |
| 9–11 | ★/● tests | evidence; report; APPLY gate; swarm audit; full suite |

**Verify:**

```bash
uv run ruff check src tests
uv run mypy src
uv run pytest tests/unit/test_evidence_collector.py \
  tests/unit/test_report_service_thread.py \
  tests/integration -q
uv run pytest -q
```

**Exit:** Stop không freeze; Run trùng key hủy phiên cũ; export không block UI; full green.

---

## Phần E — Dependency & file checklist

```text
Sprint1 contracts / outbound-mask / supervisor(key+shiboken)
    └── Sprint2 DistilledNode / dual-threshold heal / BOUND≥0.85
            └── Sprint3 SmartExecutor(raw) / Compactor(pin) / warm / thinking
                    └── Sprint4 evidence / UI / MCP-first / swarm
```

| File | S1 | S2 | S3 | S4 |
|------|----|----|----|-----|
| `core/enums.py` | ★ (+ Intent enums) | | | |
| `core/contracts.py` | ★ | | | |
| `core/models.py` | ● | ● | | ● |
| `core/secret_masker.py` | ★ | | | |
| `core/async_task_supervisor.py` | ★ | | | |
| `core/fingerprint.py` | | ★ | | |
| `core/token_budget.py` | | | ● | |
| `core/chat_run_registry.py` | ● | | | |
| `browser/snapshot_distiller.py` | | ★ | | |
| `browser/dom_distiller.py` | | ● | | |
| `browser/locator_resolver.py` | | ● | ● | |
| `browser/engine.py` | | | ● | ● |
| `agent/healer.py` | | ● | ● | |
| `agent/auto_binder.py` | | ● | | |
| `agent/dual_loop_agent.py` | ● | ● | ● | ● |
| `agent/mcp_client.py` | ● | | ● | |
| `agent/mcp_error_taxonomy.py` | | | ★ | |
| `agent/mcp_tool_executor.py` | | | ★ | |
| `agent/external_mcp_bridge.py` | | ● | ● | |
| `agent/orchestrator.py` | | | ● | ● |
| `agent/intent_router.py` | | | ★ | |
| `agent/skill_auto_matcher.py` | | | ★ | |
| `agent/builtin_skills.py` | | | ★ | |
| `agent/skill_loader.py` | | | ● | |
| `agent/mcp_servers/qa_playwright.py` | | | ● | |
| `reporting/evidence.py` | | | | ★ |
| `reporting/report_service.py` | | | | ★ |
| `reporting/excel_exporter.py` | | | | ● |
| `reporting/html_reporter.py` | | | | ● |
| `constants.py` | ● | ● | ● | ● |
| UI session/run/report | | | ● warm | ● |

---

## Phần F — Knowledges → Studio (tham chiếu nhanh)

| Pattern | Studio target |
|---------|---------------|
| Skyvern lean tree | SnapshotDistiller → DistilledNode |
| Midscene locate / ReportSession | Heal Tier2 / EvidenceCollector |
| Hermes interrupt / compress | AsyncTaskSupervisor / ContextCompactor (pin) |
| Headroom compress | ContextCompressor + snapshot specialize |
| Automa destroy/stop | teardown + MCP close_all + key replace |
| Testsigma Status/Result | StepStatus + exporters |
| Agency Evidence / RealityChecker | Swarm + Critic (không vendor) |
| Ruflo smallest topology | Fast-path + roster gate |
| MarkItDown | document_to_markdown / ingest |

---

## Phần G — Risks & non-goals

- **Non-goal:** Vendor Skyvern/Midscene/Ruflo/ECC/agency vào `src/`.  
- **Risk:** Mask inbound → login fail — mitigated by invariant #1 + unit test raw args.  
- **Risk:** Persist heal thấp → suite hỏng — mitigated by dual threshold 0.65/0.85.  
- **Risk:** Supervisor construct trước qasync → `no running event loop` — mitigated by `_loop=None` + lazy `bind_loop()` trong `register()`.  
- **Risk:** Emit trên dead QObject / từ worker — mitigated by `call_soon_threadsafe` + shiboken + main-loop.  
- **Risk:** Compactor xóa snapshot đang retry — mitigated by pin latest+2.  
- **Risk:** Gỡ `page` sớm → `MCP_ACTIONS_ENABLED` đến hết S3.  
- **Risk:** Circular import — Fingerprint/DistilledNode trong `models.py`; `contracts` chỉ re-export; `models` không import `contracts`.

---

## Appendix — CodeGraph commands

```bash
codegraph explore "src/ai_qa_test_studio DualLoopAgent.run_case Healer.heal_step ExternalMcpSession.call_tool DomDistiller RunStepResult"
codegraph explore "DualLoopAgent.run_case heal_step execute_step who calls DualLoopAgent session_controller"
```

MCP: `codegraph_explore` only trên namespace hiện tại.
