---
name: test-quality-gate
description: Multi-phase test quality gate to audit coverage completeness, assertion strength, error handling, and test isolation before committing or approving test suites.
model: inherit
---

# Test Quality Gate Skill

Use this skill to audit generated or updated test cases before merging or running in production CI.

## Verification Checklist

1. **Coverage Completeness**:
   - Are both happy paths and negative edge cases present?
   - Are boundary conditions explicitly tested?
   - Does every test have an associated `TEST-ID` traceable to requirements?

2. **Assertion Quality**:
   - Every test MUST contain at least one meaningful assertion checking business outcomes (e.g. `assertThat(locator).hasText(...)` or status code).
   - Reject arbitrary assertions like `assertThat(true).isTrue()`.

3. **No Silent Error Swallowing**:
   - Grep for `catch` blocks in test code. Reject any empty catch blocks or catch blocks that log but do not fail the test when unexpected errors occur.

4. **Resource Management**:
   - Ensure browser contexts or pages do not leak.
   - Ensure temporary files or test data are cleaned up after suite completion.

