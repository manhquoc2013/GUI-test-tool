---
name: ai-test-suite-generator
description: End-to-end skill to generate complete, resilient Playwright Java test suites from user requirements, PRD/SRS documents, or live DOM elements with dual representation (NLP steps + test data + runnable code).
---

# AI Test Suite Generator Skill

Use this skill when you need to generate high-coverage, automated Playwright Java test cases and scripts for Test Studio.

## Workflow

1. **Phase 1: Requirement Analysis & Boundary Mining**:
   - Extract user stories, core workflows, and acceptance criteria.
   - Design test scenarios covering:
     - Happy Path / Normal operations.
     - Equivalence Partitioning & Boundary Value Analysis (BVA).
     - Negative validation (missing required fields, format violations, unauthenticated access).
   - Assign unique `TEST-ID`s (e.g. `TEST-AUTH-001`, `TEST-PAYMENT-002`).

2. **Phase 2: Live DOM Inspection (If Target Page Available)**:
   - Run live DOM scan on the target URL or read from `LiveDomScannerCache`.
   - Map semantic intent to real element attributes (`data-testid`, `role`, stable `id`, accessible name).

3. **Phase 3: Dual Representation Generation**:
   - Synthesize test cases with:
     - `testSteps`: Human-readable steps with action, target, test data, and expected result.
     - `testDataSets`: Parameterized scenarios with exact inputs and outcomes.
     - `scriptCode`: Self-contained Playwright Java class with explicit waits and assertions.

4. **Phase 4: Pre-Delivery Validation**:
   - Verify class syntax, import statements, and selector health.
   - Verify that all assertions are specific and no silent failure catches exist.
