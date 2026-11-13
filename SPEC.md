# Spec: QA Copilot + 3-Role Orchestrator (Phase 5)

**Status**: ACTIVE  
**Supersedes**: Phase 4C Fluent UI (archived `docs/specs/archive/2026-11-13-phase-4c-fluent-ui/`)  
**Parent product**: AI QA Test Studio v4.0  
**Blueprint**: `docs/Bản Thiết Kế Tổng Thể AI QA Test Studio.md`  
**Capability index**: `CAPABILITY_MAP.md`  
**ADR**: `docs/adr/0001-qa-copilot-three-role-orchestrator.md`

## Objective

Thay **AI Wizard modal** bằng **một QA Copilot** nhúng nửa dưới cột phải (`BrowserAgentPane`), vừa chat streaming vừa **mặc định** chạy pipeline đa role:

**Planner → Composer → Critic (1 pass)**

để từ yêu cầu người dùng tạo/sửa kịch bản chất lượng cao, có lịch sử phiên, Stop, Copy, Undo→fill input — không thêm cột thứ 5, không swarm debate không kiểm soát.

**Users**: QA manual (VN-first); automation engineers (Apply YAML/steps/Test Data).

**Success**: Top Bar mở Copilot (Compose thay Wizard); mọi prompt user đi qua orchestrator 3 role mặc định; Stop hủy sạch stream/pipeline; Undo xoá turn + fill lại ô nhập; gates `ruff` / `mypy` / `pytest` xanh.

## Decisions locked

| # | Decision |
|---|---|
| Shell | 1 Copilot trong cột phải; Segmented `AI Copilot` \| `Agent Live Feed`; CDP viewport phía trên |
| Wizard | Modal `AiTestWizard` deprecated sau Compose; Top Bar / Palette → focus Copilot mode Compose |
| Orchestrator | **Mặc định bắt buộc** 3 role: Planner → Composer → Critic (đúng 1 vòng Critic) |
| Handoff | Structured JSON/artifacts giữa role — **không** chat tự do agent↔agent trên UI |
| Apply | Mọi ghi `TestCase` / Test Data / steps qua preview → **Apply** (trừ khi user bật auto-apply sau này) |
| LLM | OpenAI-compatible primary; Gemini adapter phụ; keys/`base_url` qua `EnvManager` |
| Offline | Không có API key → provider `offline` (deterministic mock) để UI/tests chạy không gọi mạng |
| Persist | Chat sessions: `{data_root}/copilot/sessions.json` |
| Token | UI giữ full history phiên; LLM chỉ sliding 6–8 turn + chip context; `@DOM` → `DOMDistiller` + Headroom |
| Busy | Khi Run/Sync/Launch `_busy`: Copilot Q&A/Compose vẫn Stop được; tool đụng browser bị block |
| Language | VN-first UI; role ids EN (`planner`, `composer`, `critic`) |

## Default orchestrator (non-negotiable)

Every user-submitted Copilot turn (Compose **and** Case chat) runs:

```text
User message (+ optional chips)
        │
        ▼
   ┌─────────┐
   │ Planner │  → short plan (3–7 steps) visible in thread
   └────┬────┘
        ▼
   ┌──────────┐
   │ Composer │  → draft / patch / answer artifact (streamed)
   └────┬─────┘
        ▼
   ┌────────┐
   │ Critic │  → exactly one review pass (gaps, BVA, UNBOUND, contradictions)
   └────┬───┘
        ▼
   User sees merged result + Apply (when mutating)
```

| Role | Responsibility | Must produce |
|------|----------------|--------------|
| **Planner** | Phân tích yêu cầu + ngữ cảnh chip; chia việc | Plan có bước đánh số; không mutate project |
| **Composer** | Thực thi plan: sinh/sửa case, steps, Test Data, giải thích lỗi | Artifact (markdown/YAML/patch JSON); stream chunks |
| **Critic** | Soát 1 lần output Composer | `approve` \| `revise_notes`; nếu `revise_notes` → Composer **một** lần sửa cuối rồi dừng (không vòng Critic thứ 2) |

**Out of default path**: free-form multi-agent debate; >1 Critic loop; parallel specialists không Planner. Dual-Loop + Healer + Ralph **giữ nguyên** cho Run browser — không thay bằng Critic.

**Stop semantics**: Cancel abort token ở bất kỳ role nào đang chạy; bubble assistant đánh dấu `stopped`; không ghi Apply tự động từ bản dở.

## UX contracts

### History
- Phiên: `id`, `title`, `mode` (`compose` \| `case_chat`), optional `case_id`, timestamps, messages + role artifacts.
- Persist dưới `data/` (JSON); New / Rename / Delete / Resume.
- Restart app khôi phục danh sách phiên.

### Stop
- Nút Stop thay Send khi `streaming` hoặc `orchestrator_busy` hoặc agent Run liên quan.
- Hủy `stream_chat` + pipeline role; nếu user Stop trong lúc Dual-Loop Run từ Copilot tool — gọi cùng `session.stop`.

### Copy
- Mỗi bubble (user / planner / composer / critic): Copy plain text; InfoBar “Đã copy”.

### Undo user message
- Undo bubble user (thường tin gần nhất): xoá turn đó **và** mọi planner/composer/critic/tool sinh ra sau turn; điền nguyên văn vào `input_box`; không auto-send.
- Nếu đang stream: Stop trước rồi Undo.

### Context chips
- `@Case` (default on cho case_chat), `@DOM` (off), `@Last Error` (off).
- Chỉ chip bật mới vào prompt Composer (sau Planner).

### Prompt starters (empty thread)
- Compose: «Tạo kịch bản từ SRS», «Từ URL đang mở…»
- Case: «Giải thích lỗi bước vừa chạy», «Sinh 5 bộ Test Data BVA», «Chuyển kịch bản sang YAML»

## Architecture (touch points)

```text
src/ai_qa_test_studio/
  agent/
    llm_gateway.py           # UnifiedLLMGateway AsyncIterator stream
    orchestrator.py          # Planner→Composer→Critic pipeline + cancel
    dual_loop_agent.py       # unchanged Run path
  core/
    chat_history.py          # session persist / sliding window for LLM
  ui/widgets/
    chat_pane.py             # CopilotChatPane (history UI, chips, stop, copy, undo)
    browser_agent_pane.py    # Segmented Copilot | Agent Feed
    top_toolbar.py           # AI Wizard → Copilot entry
```

## Testing strategy

- Unit: orchestrator order Planner→Composer→Critic; cancel mid-role; Critic revise → single Composer follow-up max.
- Unit: history persist round-trip; undo removes trailing role messages and returns text.
- UI smoke: pane tabs; Stop toggles; Copy/Undo signals.
- Gates: `uv run ruff check src tests`, `uv run mypy src`, `uv run pytest`.

## Boundaries

**In scope**: Copilot UI; history/stop/copy/undo; gateway streaming; **default 3-role orchestrator**; Compose thay Wizard; Apply drafts vào Explorer/Editor/Test Data; wire Top Bar + Palette.

**Out of scope**: Unlimited agent debate; changing Dual-Loop/Healer algorithms; new MCP servers; light theme; auto-apply without confirm (phase sau).

## Implementation slices

| ID | Slice | Done when |
|----|--------|-----------|
| C0 | SPEC/ADR/tasks (this doc) | Human accepted |
| C1 | `chat_pane` + Segmented in `BrowserAgentPane` + history/stop/copy/undo (mock stream OK) | UX contracts pass without live LLM |
| C2 | `llm_gateway` + EnvManager keys + real stream | Chunks hiện trên UI; Stop hủy stream |
| C3 | `orchestrator` 3 role mặc định + timeline trong thread | Mọi send đi đủ 3 role; Critic 1 pass |
| C4 | Compose mode (SRS→draft) + Apply; deprecate Wizard modal | Top Bar Copilot; Wizard không còn entry chính |
| C5 | Case tools (Test Data / steps) + chips `@DOM`/`@Last Error` | Apply xác nhận; token pill ổn với DOM |
