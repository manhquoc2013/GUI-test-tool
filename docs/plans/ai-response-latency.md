# PLAN — AI response latency

## Slice 1 — Streaming seam (no UI yet)
- `AiStreamObserver`: functional `onDelta(String)` + `none()`.
- `OpenAiClient.completeChat(..., AiStreamObserver)` overload; deltas emitted from
  `readSseCompletion`; whole body emitted once on the non-stream fallback.
- `AI-TTFT` log line: `first_token_ms`, `total_ms`, `output_chars`.
- Tests: extend `OpenAiClientStreamingTest` to assert deltas arrive in order.

## Slice 2 — HTTP/1.1 resilience
- Static host registry `HTTP11_HOSTS`; requests carry `.version(...)` per host.
- On `RST_STREAM`/stream-reset transport error, mark host, retry immediately on HTTP/1.1.
- Tests: `OpenAiClientHttp2FallbackTest` for the predicate + registry.

## Slice 3 — Parallel sub-agents
- `AiSubAgentOrchestrator.orchestrate(parent, tasks, maxParallelism)`; default 4.
- Assigned-task notes are recorded up-front in plan order; results land as they finish;
  findings aggregated in plan order so the output stays deterministic.
- `AiLlmAgentExecutor.Completion` gains a labelled default method so each stream is tagged.
- Tests: `AiSubAgentOrchestratorTest` proves overlap > 1, ordering, failure isolation.

## Slice 4 — Bigger sub-agent budget
- `delegateToAgents` passes `AiModelCapabilities.resolveOutputCeiling(cfg)` for prose agents.

## Slice 5 — Live preview in the bubble
- `AiStreamAggregator`: thread-safe per-source tail buffers + counted-only sources.
- Thinking bubble gains counter + preview labels; repaint is coalesced on the FX thread.
- Both stylesheets get `.ai-thinking-preview-*` tokens.
- Tests: `AiStreamAggregatorTest`.

## Slice 6 — Verification
- `mvn -q test`, `mvn -q clean compile` (warnings fail the gate).
