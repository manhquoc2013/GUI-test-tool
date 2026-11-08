# Specification: Professional Comprehensive Bilingual Documentation Overhaul (v2.3.1)

## 1. Objective & Scope
Modernize and professionalize the entire documentation suite of **Test Studio (v2.3.1)** to top-tier enterprise open-source standards. Synchronize all newly delivered core innovations from v2.2.0, v2.3.0, and v2.3.1:
- Interactive Clarification Cards (Ambiguity Analyzer, Interactive Question Chips, Prompt Augmentation).
- Conversational AI Project Advisor (Global test search, QA audit ranking A-D, anti-pattern detection, test gap analysis, natural technical advice).
- Optimized Self-Healing Locator Engine (<30ms heuristic recovery, cached strategies, HealedLocatorWrapper).
- Structured Test Diagnostics (ISTQB/IEEE error classifications, auto reproduction steps).
- Multi-Platform Installer & In-Place Upgrades (Windows Inno Setup HKA, Linux Debian .deb app-image with offline Chromium, macOS DMG, Unified Dual Release CI/CD).
- Standard Technical Document Elements: Mermaid diagrams, GitHub callout alerts (`[!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`), feature matrix tables, and keyboard/CLI cheat sheets.
- Full Bilingual English-Vietnamese documentation across both user and developer guides.

---

## 2. Target Files & Deliverables
1. **`docs/guide/USER_GUIDE.md`** & **`docs/guide/USER_GUIDE_EN.md`**:
   - Master user manual used both on GitHub and inside the JavaFX in-app Help Viewer.
   - Comprehensive bilingual coverage: Vietnamese and English documentation.
   - Detailed coverage of v2.3.0 & v2.3.1 AI, installer, and self-healing features.
   - Professional Mermaid process workflows and GitHub callouts.
2. **`README.md`**:
   - Professional GitHub presentation with badges, screenshot showcases, feature comparison matrix, quick start, installation matrix (macOS, Windows, Linux, Portable), and shortcut cheat sheet.
   - Bilingual language selector at top (`[🇻🇳 Tiếng Việt](docs/guide/USER_GUIDE.md) | [🇬🇧 English](docs/guide/USER_GUIDE_EN.md)`).
3. **`docs/ARCHITECTURE.md`**:
   - Complete architectural blueprint updated with all new services (`AiProjectAdvisorService`, `AiTestGeneratorService`, `MarkdownConverterEngine`, `OptimizedSelfHealingExecutor`).
   - Detailed Mermaid data flows, state machines, and sequence diagrams for AI prompt clarification and self-healing runtime.
4. **`docs/BUILD.md`**:
   - Unified build and packaging handbook covering macOS DMG, Windows Inno Setup EXE, Linux Debian .deb, and portable zero-install bundles.
   - Matrix of commands, environment prerequisites (JDK 26, Node.js 20+, Inno Setup 6, jpackage), and CI/CD troubleshooting.

---

## 3. Formatting & Quality Standards
- **Callouts**: Standard GitHub Alerts (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`).
- **Diagrams**: Valid Mermaid diagrams (`flowchart TD`, `flowchart LR`, `sequenceDiagram`).
- **Tables**: Clean Markdown tables for feature matrices, assertion types, CLI options, and configuration keys.
- **File Links**: Verified file links adhering to project layout.
- **Test Integrity**: Must pass all automated documentation and resource tests (`mvn test -Dtest=UserGuideServiceTest`).
