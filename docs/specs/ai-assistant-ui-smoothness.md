# SPEC — AI Assistant: smooth streaming & composer states

Status: accepted
Date: 2026-11-11

## Problem (observed)

While a turn streams, the AI Assistant page stutters:

1. `paintStreamPreview()` repaints on every FX pulse (up to ~60 fps) and each repaint
   changes the wrapped `Label` height, so the whole `chatMessagesBox` re-lays out on
   every token batch.
2. Every repaint calls `chatScroll.setVvalue(1.0)`: the viewport jumps even when the
   user scrolled up to read an earlier answer.
3. `appendLiveTranscriptMessage()` removes the thinking bubble, appends a bubble, then
   re-adds the thinking bubble — two extra layout passes per sub-agent message.
4. The end of a turn calls `renderChatHistory()`, which clears and rebuilds the whole
   transcript: a visible flash/double paint.
5. The toolbar is a `FlowPane`: when the token/model badges or the history badge change
   width, the action buttons re-wrap. The send button also swaps its label to
   "Đang sinh kịch bản..." and the progress spinner appears/disappears, so the composer
   row re-flows on every turn.

## Requirements

1. Preview paint is throttled to a fixed cadence (8 fps) and the preview area has a
   fixed height (≈3 lines, clipped to its newest text), so streaming text never
   re-measures the transcript.
2. Auto-scroll only follows the stream while the user is pinned to the bottom; a user
   who scrolled up keeps their position.
3. A new sub-agent bubble is inserted before the running bubble; nothing is
   remove/re-added.
4. The end of a turn appends/refreshes only the messages that are missing on screen; it
   must not rebuild the transcript.
5. Toolbar: two groups (identity | actions) separated by a spacer; badge widths are
   bounded and ellipsize, so buttons never re-wrap.
6. Composer: the footer is a stable row (`Đính kèm` / `Tạo Test Script` / `Gửi`), the
   send button keeps its label and width, and the running state lives in a dedicated
   strip above the input with a "Dừng" action.
7. Cancelling a run is cooperative: the stream callback checks the run's cancel flag and
   aborts the turn; the transcript records one "đã dừng" assistant turn. A cancellation
   is rethrown by batch/retry loops instead of being counted as a transport failure.
8. State written by a model worker and read by the FX thread (per-agent metrics, the
   wave artefact map) is concurrent, so a render can never corrupt it.

## Implementation map

- `AiAssistantPageController`: throttled preview (`PREVIEW_PAINT_INTERVAL`,
  `buildPreviewViewport`), pin-aware scrolling, incremental transcript
  (`renderedBubbles`, `appendMissingTranscriptBubbles`), run strip state, cancel handler.
- `ai_assistant_page.fxml` + `studio.css` / `studio-light.css`: toolbar HBox with a
  growing title, shrinkable badges, fixed action group; composer run strip.
- `AiChatRunRegistry`: per-session cancel flags, cleared with the turn.
- `AiSubAgentOrchestrator`, `AiTestGeneratorService`: cancellation propagation.
- `AiSubAgentSession.metrics`: concurrent map (worker write / FX read).

## Non-goals

- No change to prompt content, model choice, orchestration policy, or answer quality.
- The live preview requirement of `docs/specs/ai-response-latency.md` stays: text and
  the received-character counter are still shown.
- No virtualisation of the transcript; only the per-frame re-layout is removed.
