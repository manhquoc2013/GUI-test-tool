# Specification: Multi-Screen SRS Semantic Sectioning & Multi-Batch 4-Agent Orchestration
**Status**: IN_PROGRESS

## 1. Objective & Problem Statement
When a user attaches a large SRS document (e.g., 60 pages covering 10 functional screens) and requests test scenarios for 10 screens, the system previously produced only ~10 test cases (1 per screen) due to three architectural bottlenecks:
1. **Severe Document Truncation (`AiTokenOptimizer`)**: Attached documents were truncated at `4,000` characters (~1.5 pages), discarding ~97% of a 60-page SRS before reaching the model.
2. **Single-Pass Output Bottleneck (`AiTestGeneratorService`)**: All 4 agents were simulated inside a single `chatCompletion` call. Due to LLM single-response output token limits (4K–8K tokens), the LLM compressed its output to 1 test case per screen (~10 cases total).
3. **Global `cases.size() == 1` Check (`AiMultiAgentCoordinator`)**: Agent 3 (Reality Checker) and Agent 4 (Loop Engineer) only triggered scenario expansion when the entire suite had `cases.size() == 1`. When 10 cases were returned for 10 screens (1 case/screen), `cases.size() == 10 != 1`, bypassing per-screen coverage expansion completely.

## 2. Target Architecture & Requirements
1. **Full SRS Semantic Sectioning (`ScreenSpecSection` Mining - Agent 1 Spec Miner)**:
   - Parse the full converted Markdown of attached SRS documents without the 4,000-char cutoff.
   - Ignore PDF page-marker headings (`### Trang N`, `Page N`) and boilerplate document sections (`Giới thiệu`, `Mục đích`, `Phạm vi`, `Thuật ngữ`, `Tài liệu tham khảo`, `Lịch sử sửa đổi`).
   - Extract functional screens / modules (`Màn hình`, `Screen`, `Trang`, `Chức năng`, `Module`, `Phân hệ`, `Form`, `UC-xx`, `FR-xx`, or numbered functional sections) along with their specific UI fields, buttons, business rules, and validation constraints.
   - Support explicit screen count requests in the user prompt (e.g., `"10 màn hình"`, `"5 chức năng"`), ensuring all requested screens are inventoried from the SRS or prompt.
2. **Multi-Batch Orchestration per Screen Group (Agent 2 Test Architect)**:
   - When multiple screens/modules are detected (`screens.size() >= 2`), partition screens into focused batches (e.g., 2 screens per batch) so each LLM invocation receives the full SRS specification for those screens and has sufficient output token capacity to generate **3–5+ detailed test cases per screen**.
   - Emit granular progress updates per batch (`✍️ [Agent 2/4 - Test Architect] Đang thiết kế Test Case cho Màn hình X-Y/N...`).
3. **Per-Screen Coverage Audit & Expansion (Agent 3 Reality Checker + Agent 4 Loop Engineer)**:
   - Audit test case coverage **per screen / functional feature**, not just `cases.size() == 1`.
   - Enforce a minimum of **3 to 5 test cases per screen** (Happy Path [P0], Negative/Validation [P1], Boundary BVA [P1/P2], and Business Rule/Permission/Workflow [P2]) -> 10 screens produce **30 to 50+ detailed test cases**.
   - When any screen (either from SRS `ScreenSpecSection`s or from distinct `TCxx_<Feature>_...` groups) has fewer than the minimum required cases (e.g., only 1 case per screen), Agent 4 synthesizes SRS-grounded companion test cases using the fields, actions, and validation rules of that specific screen.
   - Sequentially normalize test case numbering (`TC01_...` through `TC40_...`) while preserving screen-specific functional tokens.
4. **Structure-Preserving Token Optimization (`AiTokenOptimizer`)**:
   - Raise the document context budget from 4,000 chars to scale dynamically with the model token limit (up to 60,000+ chars).
   - Provide `compressDocumentSmart` that preserves headings, tables, and balanced content across all sections of a multi-page document rather than blindly cutting at character 4,000.

## 3. Commands & Verification Strategy
- Compile & Run Unit Tests: `mvn -o test`
- New & Updated Test Suites:
  - `AiMultiAgentLargeSrsMultiScreenTest`: Verifies 60-page SRS with 10 screens produces 30–50+ detailed test cases (3–5+ cases per screen) across both multi-batch LLM execution and single-case-per-screen self-healing.
  - `AiTokenOptimizerTest`: Verifies large document handling without 4,000-char cliff truncation.
  - `AiMultiAgentLoopAndNamingTest`: Regression verification for 4-agent pipeline and functional naming.
