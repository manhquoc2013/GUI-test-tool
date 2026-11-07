# Implementation Plan: Layout Redesign, Dual Dark/Light Theme & Vector Icons

## Slices of Delivery

- [ ] **Slice 1: Vector SVG Icon System (`UiIcons.java`)**
  - Implement SVG path definitions for standard IDE actions: Record, Run, Run All, Save, AI Sparkles, Split View, Settings, Help, Sun, Moon, Plus, Trash, Edit, Copy, Close, Refresh, Eye, Terminal, Globe, Filter.
  - Implement factory helpers to set `SVGPath` graphics on JavaFX buttons with responsive sizing and CSS class styling.
  - Unit test icon creation and path parsing.

- [ ] **Slice 2: Dual Dark & Light Theme Architecture**
  - Update `SettingsStore.Settings` to persist user theme preference (`"dark"` or `"light"`).
  - Create `src/main/resources/css/studio-light.css` with full light theme palette (slate/blue, WCAG AAA contrast).
  - Implement `ThemeService.java` to toggle theme dynamically across `Scene` and dialog panes.
  - Add theme toggle button (`themeToggleBtn`) to `main_shell.fxml` and connect in `MainShellController.java`.

- [ ] **Slice 3: Workspace Action Bar & Layout Overhaul**
  - Refactor `suite_workspace_page.fxml` toolbar into clear functional groups:
    - Target URL bar (bounded width, no crowding)
    - Execution group (Record, Run Case, Run Suite) with real SVG icons + clean text
    - Authoring group (Save, AI) with real SVG icons + clean text
    - View/Options group (Split, Terminal, Options checkboxes)
  - Adjust padding, margins, and CSS classes in `studio.css` and `studio-light.css` to eliminate text clipping/truncation.
  - Wire `SuiteWorkspacePageController.java` to attach SVG icons dynamically or via FXML.

- [ ] **Slice 4: Dialogs & Sub-components Polish**
  - Clean up `AiCasePromptDialog.java`: replace remaining broken emojis with SVG icons / clean text, fix truncated buttons.
  - Clean up `AiSettingsDialog.java`: attach SVG icons for visibility toggle, connection test, model refresh.
  - Polish `ExplorerPageController.java` and `MainShellController.java` button graphics.

- [ ] **Slice 5: Verification & Regression Testing**
  - Run full test suite (`mvn test -o`).
  - Verify zero console CSS warnings and zero truncation.
  - Update `tasks/learnings.md`.
