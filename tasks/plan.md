# Plan: Comprehensive UI/UX Redesign with AtlantaFX, Low-Code/No-Code Step Builder & RichTextFX Dual View

## Slice 1: Core Dependencies, AtlantaFX Theming & Vector Icons
- **Files**:
  - `pom.xml`
  - `src/main/java/com/studio/service/ThemeService.java`
  - `src/main/java/com/studio/util/UiIcons.java`
  - `src/main/resources/css/studio.css`
  - `src/main/resources/css/studio-light.css`
- **Details**:
  - Add `atlantafx-base:2.0.1`, `ikonli-javafx:12.3.1`, `ikonli-feather-pack:12.3.1`, and `richtextfx:0.11.4` to `pom.xml`.
  - Refactor `ThemeService` to initialize and toggle AtlantaFX themes (`PrimerDark`, `PrimerLight`, `Dracula`).
  - Update `UiIcons` utility to produce high-resolution vector icons using `FontIcon` from Ikonli.

## Slice 2: Modern App Shell (Activity Rail, Header, Tab Strip, Status Bar)
- **Files**:
  - `src/main/resources/fxml/main_shell.fxml`
  - `src/main/java/com/studio/controller/MainShellController.java`
  - `src/main/java/com/studio/service/StatusBarService.java`
- **Details**:
  - Redesign `main_shell.fxml` with a 48px `ActivityRail` on the left.
  - Implement collapsible sidebar and standard document tab strip.
  - Add footer status bar showing Playwright engine, browser mode, self-healed locators, and test stats.

## Slice 3: Low-Code / No-Code Visual Step Builder & RichTextFX Dual View
- **Files**:
  - `src/main/java/com/studio/ui/StepCardView.java`
  - `src/main/java/com/studio/ui/VisualStepFlowPane.java`
  - `src/main/java/com/studio/ui/PlaywrightCodeArea.java`
  - `src/main/resources/fxml/suite_workspace_page.fxml`
  - `src/main/java/com/studio/controller/SuiteWorkspacePageController.java`
- **Details**:
  - Build `StepCardView` representing individual `TestStep` items with action badges, selector inputs, parameter pills, and drag-and-drop handles.
  - Build `PlaywrightCodeArea` using RichTextFX with Java syntax highlighting and line numbers.
  - Implement a 3-way toggle (`No-Code`, `Code`, `Split`) in `SuiteWorkspacePageController` with bidirectional sync.

## Slice 4: Explorer Page & AI Copilot Drawer Redesign
- **Files**:
  - `src/main/resources/fxml/explorer_page.fxml`
  - `src/main/java/com/studio/controller/ExplorerPageController.java`
  - `src/main/resources/fxml/ai_assistant_page.fxml`
  - `src/main/java/com/studio/controller/AiAssistantPageController.java`
- **Details**:
  - Upgrade `ExplorerPage`: Segmented filter control, hover quick actions (`[▶] [✎] [+]`), and structured Detail Card.
  - Implement Contextual AI Copilot drawer inside `SuiteWorkspacePage` for 1-click selector fix and assertion generator.

## Slice 5: Reports Dashboard & Evidence Visualizer Redesign
- **Files**:
  - `src/main/resources/fxml/reports_page.fxml`
  - `src/main/java/com/studio/controller/ReportsPageController.java`
  - `src/main/resources/fxml/report_detail_page.fxml`
  - `src/main/java/com/studio/controller/ReportDetailPageController.java`
  - `src/main/java/com/studio/controller/EvidenceViewerDialog.java`
- **Details**:
  - Add 4 KPI Summary Cards at the top of the reports view.
  - Display interactive Step Execution Timeline with pass/fail/healed badges.
  - Polish Evidence Viewer modal with zoom/pan and comparison view.

## Slice 6: Regression Verification & Polish
- **Files**:
  - All test suites in `src/test/java/`
  - `tasks/learnings.md`
- **Details**:
  - Ensure all 411+ unit and controller tests pass with zero failures.
  - Check dark and light theme consistency.
