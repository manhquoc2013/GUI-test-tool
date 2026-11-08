# Implementation Plan: Professional Documentation Suite (v2.3.1)

## Overview
Comprehensive update of all project documentation to enterprise open-source standard, with full bilingual English & Vietnamese coverage, technical diagrams, callout blocks, feature matrices, and coverage of v2.3.1 capabilities.

## Execution Slices

### Slice 1: User Manual Modernization (`docs/guide/USER_GUIDE.md` & `docs/guide/USER_GUIDE_EN.md`)
- Update `docs/guide/USER_GUIDE.md`:
  - Upgrade preamble to version 2.3.1.
  - Section 9 (Trợ lý AI): Add Section 9.3 for Interactive Clarification Cards & Ambiguity Analyzer; Section 9.4 for Conversational Project Advisor (`searchCases`, `evaluateCase` A-D QA audit, `analyzeSuiteGaps`, `CHAT_ADVICE`); Section 9.5 for Multi-Specialist Roster; Section 9.6 for MarkItDown Document Ingestion.
  - Section 12 (Đóng gói & CI/CD): Add Linux Debian (.deb) offline bundle details; Windows Inno Setup HKA registry, in-place upgrade, and safe taskkill termination; GitHub Actions Unified Dual Release pipeline (6 targets).
  - Add standard callout alerts (`[!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`) and Mermaid diagrams.
  - Add Quick Cheat Sheet (Keyboard shortcuts & CLI options).
- Create `docs/guide/USER_GUIDE_EN.md`:
  - Full English translation mirroring the 16 chapters with identical screenshot references, tables, and Mermaid diagrams.
  - Update top navigation in both guides to allow instantaneous language toggling.

### Slice 2: Repository Showcase (`README.md`)
- Modernize README.md with bilingual banner (`[🇻🇳 Tiếng Việt](docs/guide/USER_GUIDE.md) | [🇬🇧 English](docs/guide/USER_GUIDE_EN.md)`).
- Add high-contrast feature comparison matrix vs Traditional Selenium / Raw Playwright CLI / Cypress.
- Highlight AI Clarification Cards, Conversational QA Advisor, Self-Healing Engine, and Offline Universal Installers.
- Provide clear multi-platform installation table (macOS DMG, Windows EXE, Linux DEB, Portable ZIP).
- Add Quick Start and Developer CLI reference.

### Slice 3: Technical Blueprint (`docs/ARCHITECTURE.md`)
- Update Technology Stack and Package Structure to reflect all v2.3.1 models (`AiClarificationQuestion`, `HealedStepMeta`), stores (`AiChatHistoryStore`, `TestDataProfileStore`), and services (`AiProjectAdvisorService`, `OptimizedSelfHealingExecutor`, `MarkdownConverterEngine`).
- Add Mermaid Sequence Diagram for AI Clarification Loop and Conversational Advisor.
- Add Mermaid State Diagram for Self-Healing Locator Lifecycle.
- Document In-Process JVM Test Execution, H2 cache synchronization, and AES-256-GCM credential encryption.

### Slice 4: Multi-Platform Build & Delivery Guide (`docs/BUILD.md`)
- Document complete multi-platform packaging:
  - macOS DMG (`scripts/build-installer.sh`)
  - Windows Setup EXE (`scripts\build-installer.bat` via Inno Setup 6)
  - Linux Debian DEB (`scripts/build-installer-linux.sh` via jpackage)
  - Portable bundles (`scripts/build-portable.sh` / `.bat`)
- Document CI/CD Dual Release matrix (`.github/workflows/release-portable.yml`).
- Add troubleshooting matrix for JDK 26, Node.js bundling, Inno Setup HKA permissions, and headless Chromium execution.

### Slice 5: Verification & Quality Assurance
- Run `mvn test -Dtest=UserGuideServiceTest` to ensure in-app resource bundling passes.
- Verify Markdown formatting, code fences, Mermaid diagram syntax, and link validity.
- Update `tasks/learnings.md` and complete review checklist.
