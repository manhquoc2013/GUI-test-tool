# Technical Specification: Modern Layout & Dual-Theme (Dark/Light) Overhaul

## 1. Objective
Refactor the Playwright Automation Studio UI to:
1. Eliminate layout crowding, squished toolbars, and truncated button texts (`Ghi ...`, `Chạy ...`, `AI H...`).
2. Provide a seamless, toggleable Dark Theme (Tokyo Night / Modern Dark) and Light Theme (GitHub/VS Code Light) with persistent user preference.
3. Replace all emoji-based and missing-font icons with crisp, native, scalable vector SVG path graphics (`SVGPath`) on action buttons across the shell, workspace, explorer, and modal dialogs.
4. Establish clean visual grouping, balanced padding/margins, and cohesive color palettes.

## 2. Architecture & Components

### 2.1 Vector Icon Engine (`UiIcons.java`)
- Native `javafx.scene.shape.SVGPath` rendering using standard Lucide / VS Code Codicon SVG path data.
- Utility methods:
  - `createIcon(String svgPath, double size, String styleClass)`
  - `createButton(String text, String svgPath, double iconSize, String... styleClasses)`
- Zero dependency on external font files or system emoji tables. Eliminates all tofu boxes `[]` and DirectWrite `\uFE0F` variation selector issues on Windows.

### 2.2 Dual-Theme System (`ThemeService.java` & `studio-light.css`)
- `SettingsStore.Settings`: Add `theme` ("dark" | "light", default "dark").
- `studio.css`: Modernized dark theme with refined Tokyo Night tokens and balanced button styling.
- `studio-light.css`: Full light theme override (clean `#f8fafc` background, slate text `#0f172a`, crisp borders `#e2e8f0`, vibrant primary blue `#2563eb`).
- `MainShellController`:
  - Top header theme toggle button (`themeToggleBtn`) featuring Sun SVG (in dark mode) / Moon SVG (in light mode).
  - One-click instant theme toggle without application restart.
  - Automatic propagation to `DialogTheme` so modal dialogs inherit the active theme.

### 2.3 Workspace Layout Redesign (`suite_workspace_page.fxml` & Controller)
- **Top Bar**:
  - URL Input: Dedicated, comfortable width (`prefWidth="280"`, `minWidth="180"`), clean search/globe icon.
  - **Execution Group**:
    - Record (`toolbar-btn-record`): Red circle SVG + "Ghi hình"
    - Run Case (`toolbar-btn-run-case`): Green play SVG + "Chạy case"
    - Run Suite (`toolbar-btn-run`): Double play SVG + "Chạy suite"
  - **Authoring Group**:
    - AI Assist (`btn-ai-assist`): Sparkles SVG + "AI Hỗ trợ"
    - Save Code (`toolbar-btn-save`): Floppy disk SVG + "Lưu code"
  - **View & Options Group**:
    - Split Layout (`btn-layout-toggle`): Columns SVG + "Chia đôi"
    - Checkboxes: "Dừng khi lỗi", "Chung context" in a neat compact container.
- Button text truncation (`Ghi ...`, `Chạy ...`) is completely eliminated by setting appropriate flex constraints (`HBox.hgrow="NEVER"` on button groups, `HBox.hgrow="ALWAYS"` on flexible spacer).

### 2.4 Modal Dialogs Cleanup (`AiCasePromptDialog.java`, `AiSettingsDialog.java`)
- Replace all remaining broken emojis with SVG icons or clean labels.
- Fix button dimensions to prevent ellipsis truncation (`Đóng` instead of `✖[] Đ...`).

## 3. Testing & Verification
- Verify compilation with JDK 26 (`mvn clean compile`).
- Run all 44 existing unit tests with `-o` flag (`mvn test -o`).
- Add tests for theme persistence and SVG icon creation.
