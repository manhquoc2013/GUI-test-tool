# Specification: Comprehensive UI/UX Redesign with AtlantaFX, Low-Code/No-Code Step Builder & Dual-View Code Editor
**Status**: COMPLETED

## 1. Objective & Problem Statement
The current UI of Test Studio relies on stock JavaFX controls styled with custom CSS (`studio.css`), unicode/emoji icons, and a text-based code area. This creates several UX bottlenecks:
1. **Lack of Visual/No-Code Authoring**: Test cases must be created either through raw Playwright Java scripts or basic text forms. Non-technical QA and manual testers cannot easily compose, inspect, or drag-and-drop test steps.
2. **Basic Code Editor**: The current editor uses standard JavaFX `TextArea`, lacking line numbers and a synchronized view with the visual step builder.
3. **Inconsistent Iconography & Visual Hierarchy**: Emoji characters (`▶️`, `🔴`, `✨`, `📸`) render inconsistently across operating systems and lack pro-grade IDE visual polish.
4. **Theme Fragmentation**: Custom CSS themes require tedious manual upkeep and lack modern component primitives (segmented buttons, pill badges, toggle switches, elevated cards).

## 2. Target Architecture & Requirements
1. **AtlantaFX Theme Framework Integration**:
   - Integrate `io.github.mkpaz:atlantafx-base:2.0.1`.
   - Support seamless switching between **Primer Dark** (default) and **Primer Light**.
   - Standardize all UI controls using AtlantaFX classes (`.accent`, `.success`, `.danger`, `.flat`, `.small`, `Styles.TOGGLE_BUTTON`, `Breadcrumbs`, `Card`, `Badge`).
2. **Vector Icon System**:
   - Extend the native `com.studio.util.UiIcons` SVGPath registry (24×24 paths, CSS `-fx-fill` from AtlantaFX tokens).
   - Replace unicode/emoji icons with sharp, theme-aware vector icons across Shell, Explorer, Workspace, AI Copilot, and Reports.
3. **Low-Code / No-Code Visual Step Builder**:
   - Implement `VisualStepFlowPane` and `StepCardView` to render `TestStep` objects as draggable, interactive action cards (`NAVIGATE`, `CLICK`, `FILL`, `SELECT`, `HOVER`, `WAIT_FOR_ELEMENT`, `ASSERT_VISIBLE`, `ASSERT_TEXT`, `DELAY`, `CUSTOM_JS`, plus `RAW` for unmodelled Java).
   - Support drag-and-drop step reordering, enable/disable toggling (`[👁️]`), parameter insertion (`{{env.VAR}}`), and per-step Playwright command copy.
   - Disabled steps remain persisted in the model (`TestStep.enabled = false`) but are excluded from the generated script.
4. **Dual-View Code Editor**:
   - Implement `PlaywrightCodeArea` as a native JavaFX editor with a synchronized line-number gutter and O(n) line counting.
   - RichTextFX was evaluated and dropped: its bytecode is not compatible with JavaFX 26 / JDK 26, so syntax highlighting is deferred. The 3-way view toggle (`No-Code | Code | Split`) and bidirectional synchronization between `List<TestStep>` and the Java script are implemented.
   - Synchronization is lossless: the recorded Java envelope (`package`/`import`/`class`/`main`) is preserved on every regeneration, and body lines the builder does not model (comments, hand-written Playwright calls) survive as `RAW` steps instead of being deleted.
5. **Full System Shell & Navigation Redesign**:
   - **Single collapsible sidebar** (220px, shrinks to a 56px icon rail): one nav entry per page — Explorer, Reports, AI Assistant — plus the "Suite đang mở" context block and the primary "Chạy tất cả" action. Navigation lives in exactly one place; global actions (theme, cleanup, settings, help) live in the header only.
   - **Document Tab Bar** with file-type badges, dirty state tracking (`●`), and close handling. Reserved for open documents (suites, report details) only — fixed pages never become tabs, and the strip hides itself when no document is open.
   - **Status Bar** footer displaying runner state, active Playwright engine, browser mode, and healed locators count.
   - **Contextual AI Copilot Drawer** dockable inside the workspace for instant selector fixes and assertion generation (docked right drawer, background LLM call, DOM context capped at 6,000 chars).
   - **Reports Dashboard**: 4 KPI Summary Cards, interactive step execution timeline, and screenshot evidence viewer.

## 3. Commands & Verification Strategy
- Build & Verification: `mvn clean test` (all 411+ existing tests + new UI tests must pass).
- Compile check: `mvn clean compile -DskipTests` (0 errors, 0 warnings).
- Layout & FXML integrity tests: Ensure all FXMLs load cleanly with no runtime reflection or controller linkage errors.

## 4. Iteration 2 — No-Code Builder Usability
**Status**: COMPLETED

### Objective
The No-Code step builder was technically correct but hostile to use: every card rendered two permanent rows (including a value field that is disabled and unusable for RAW steps), the action picker showed raw action codes (`RAW`, `NAVIGATE`) instead of what the step does, the step number collapsed to "…", and copy/toggle/delete competed with each other as three equal icons. Two personas drive the redesign: a manual tester who has never written a selector, and a QA engineer reading a recorded script.

### Requirements
1. **Collapsed summary, expandable editor (accordion)**
   - A card always shows one line: drag handle, step number, action chip, human-readable summary, enable toggle, overflow menu, expand chevron.
   - Selecting the row (or the chevron, or Enter/Space while focused) expands the editor; only the fields the action actually consumes are rendered.
2. **Human labels, preserved model values**
   - Every persisted action code maps to a Vietnamese label plus a vector icon (`StepAction`); the model still stores `NAVIGATE`/`CLICK`/…/`RAW` so generated scripts and existing suites are unaffected.
   - Summaries read like instructions ("nhập «admin» vào input#email"), and RAW steps show their statement in a monospace line.
3. **Fewer, better-placed controls**
   - Copy / delete move into a `⋯` overflow menu that appears when the card is hovered or selected; the enable toggle stays visible (it is a state, not a destructive action).
   - Deleting a step that already carries a selector or value asks for confirmation; deleting an empty step is immediate.
4. **Responsive workspace**
   - The editor header degrades gracefully: view-mode buttons become icon-only (tooltips preserved) before any label is ellipsized, and the line-count caption only shows when it fits whole.
   - The Asserts panel is removed from the split when the workspace is too narrow for both, and a header toggle restores it; both panels keep a usable minimum width.
5. **Empty state and entry points**
   - An empty case shows a real empty state (icon, title, explanation, primary CTA) rather than a single muted sentence.
   - Adding a step expands it and puts the caret in its primary field.

### Acceptance / Verification
- `mvn -o test` green; new guards: `StepActionTest`, `StepCardViewUxTest`, `WorkspaceResponsiveLayoutTest`, extended `StepBuilderLayoutTest`.
- `javac -Xlint:all` clean on `src/main` and `src/test`.
- Both themes verified by rendering the step builder and the editor header to PNG.
