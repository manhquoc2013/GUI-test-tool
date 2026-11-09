# Specification: AI Agent Test Case Generation & Export with Conditional Script Omission
**Status**: COMPLETED

## 1. Objective & Scope
Enhance the AI Assistant in **Test Studio** to support generating detailed, high-coverage test cases from user requirements and attached documents (PRD, SRS, specs, business rules) and allow users to download/export the generated test suite directly in multiple formats, with a strict rule: **omit automation test scripts when no test page URL is provided or when disabled**.

Key Capabilities:
1. **Knowledge-Driven Test Case Generation (Consulting `docs/knowledge`)**:
   - Inspired by **Testsigma** (`testsigma-dev`), **Agency-Agents** (`agency-agents-main/testing`), **Fabric** (`Fabric-main`), and **ECC** (`ECC-main`):
     - Exhaustive test cases with multi-scenario coverage: Happy Path (Positive), Negative Validation (Error Handling), Boundary Value Analysis (BVA & Edge Cases), and Precondition setups.
     - Granular, structured test steps: Step Order, Action, Target/Field name according to document, Test Data, Description, Expected Result.
     - Parameterized test data sets covering equivalence partitions.
     - Verifiable acceptance criteria and assertion points.
2. **Conditional Test Script Generation (No URL -> No Script)**:
   - When no URL/link is detected in the prompt, documents, or live DOM, or when the user toggles off the "Tạo Test Script" option:
     - Prompt instructs AI to completely omit Playwright Java script code (`scriptCode = ""`).
     - AI focuses 100% of reasoning on deep, exhaustive test case specifications.
     - UI displays an informative notice in the script editor and focuses by default on the Test Steps tab.
3. **Download / Export Generated Test Cases Directly**:
   - Provide a download/export action directly in the AI Assistant toolbar (`exportMenuBtn` / `exportExcelBtn`).
   - Formats supported:
     - **Excel (.xlsx)**: Professional 3-sheet workbook compliant with QA/ISTQB standards (Sheet 1: Tổng quan / Overview, Sheet 2: Danh sách Test Cases, Sheet 3: Chi tiết các bước kiểm thử).
     - **Markdown (.md)**: Formatted test specifications document with matrix tables, steps, and test data.
     - **JSON (.json)**: Portable structured test suite format for archival and external integration.
4. **UI Toggle & Controls**:
   - "Tạo Test Script" CheckBox toggle in the AI input area:
     - Automatically adapts based on whether an active URL is present, while giving the user full manual control to turn it on or off.

---

## 2. Architecture & Data Flow

```
[User Prompt / Attached Docs (.md, .pdf, .docx, .xlsx)]
                   │
                   ▼
       [URL & Requirement Detection]
  (Detects presence of http:// or https://)
                   │
                   ├── If No URL or "Tạo Test Script" disabled:
                   │   └── Flag: generateScript = false
                   │       Prompt: Focus on detailed QA test steps, preconditions, BVA data, assertions.
                   │               Set scriptCode = ""
                   │
                   └── If URL present & "Tạo Test Script" enabled:
                       └── Flag: generateScript = true
                           Prompt: Dual representation (NLP Steps + Playwright Java code)
                   │
                   ▼
        [AiTestGeneratorService]
                   │
                   ▼
          [LLM Chat Completion]
                   │
                   ▼
         [List<AiGeneratedCase>]
                   │
       ┌───────────┴───────────┐
       ▼                       ▼
[AI Assistant UI]      [Direct Download / Export]
- Preview Steps Tab    - TestCaseExcelExportService.exportAiGeneratedCases (.xlsx)
- Notice if No Script  - Markdown Export (.md)
- Apply to Suite       - JSON Export (.json)
```

---

## 3. Verification & Testing Strategy
- `TestCaseExcelExportServiceTest`:
  - Unit test for `exportAiGeneratedCases`: Verify that generating an Excel file from `List<AiGeneratedCase>` yields valid 3 sheets (Overview, Test Cases, Steps), correct row counts, and proper cell values.
- `AiTestGeneratorServiceTest`:
  - Verify that when no URL is present or `generateScript` is false, prompt contains explicit directive to omit scriptCode, and returned cases handle empty `scriptCode` gracefully.
- UI Controller verification:
  - Verify download menu actions, file chooser handling, and script tab notification.
