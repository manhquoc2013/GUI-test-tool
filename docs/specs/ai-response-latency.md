# SPEC — AI response latency

Status: accepted
Date: 2026-11-11

## Problem (measured, not estimated)

One user turn at 20:00 on 2026-11-11 took **5m 25s**, from `data/logs/ai.log`:

| window | duration | prompt tokens | note |
|---|---|---|---|
| 20:00:28 → 20:01:19 | 51.0s | 9,946 | first model call |
| 20:01:19 → 20:01:46 | 26.6s | 10,469 | hit 4096 output cap → continuation |
| 20:01:46 → 20:02:07 | 21.4s | 15,001 | continuation 1/3 |
| 20:02:07 → 20:02:36 | 28.8s | 19,087 | continuation 2/3 |
| 20:02:36 → 20:03:39 | 62.7s | 10,616 | second sub-agent |
| 20:03:39 → 20:04:16 | 37.4s | 14,990 | restart retry |
| 20:04:42 → 20:05:53 | 71.0s | 10,220 | `RST_STREAM: Protocol error` + retry |

Network is **not** the bottleneck: measured to `api.foza.ai`, HTTP/2 handshake
`dns=0.09s connect=0.24s tls=0.38s`, HTTP/1.1 `connect=0.34s tls=0.74s`.

## Requirements

1. Sub-agents of one turn must run concurrently (bounded), not one after another.
2. Sub-agent output budget uses the model's own ceiling (DeepSeek 8192), so prose replies
   stop being cut at 4096 and resumed with the whole 15k-token prompt again.
3. The chat bubble shows a live preview of incoming text plus a received-character counter,
   so the 20–60s of one provider call is visible progress instead of a frozen spinner.
4. A host that answers HTTP/2 with `RST_STREAM` is retried on HTTP/1.1 and remembered for the
   rest of the process, so one protocol mismatch does not cost a minute.
5. Time-to-first-token and total stream time are logged, so future slowness is diagnosable.
6. Raw JSON deltas are never pasted into the bubble; JSON-producing calls only update the
   character counter.

## Non-goals

- No change to prompt content, model choice, or answer quality.
- No forced downgrade of provider features (SSE stays enabled).
