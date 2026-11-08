---
name: playwright-test-healer
description: Structured self-healing workflow to diagnose and repair failing Playwright Java test scripts, fixing syntax errors, stale locators, and timing issues with minimal diffs and anti-thrashing protection.
model: inherit
---

# Playwright Test Healer Skill

Use this skill when a Playwright test script fails during compilation or runtime execution.

## Healing Workflow

1. **Capture Failure Artifacts**:
   - Inspect standard output, error streams, and stack trace.
   - Retrieve the failed step number and target locator.
   - Inspect failure screenshot and DOM snapshot if available.

2. **Categorize Root Cause**:
   - **Compile/Import Error**: Missing classes or mismatched method signatures.
   - **Stale/Missing Locator**: Target element changed ID, class, or structure.
   - **Timing/Race Condition**: Action executed before page load or animation completed.
   - **Assertion Mismatch**: Actual value differs from expected value.

3. **Apply Minimal Diff Recovery**:
   - If locator issue: Check DOM snapshot or heuristic attributes (`data-testid`, text, placeholder) and replace with resilient selector.
   - If timing issue: Insert `page.waitForLoadState(LoadState.NETWORKIDLE)` or locator assertion before action.
   - If compilation issue: Add required imports or adjust type casting.

4. **Anti-Thrashing Protocol**:
   - Count edits for the file in the current healing session.
   - If attempt count reaches **3** without passing, stop modifying the file.
   - Report:
     1. Why the previous 3 hypotheses failed.
     2. Evidence from failure screenshots/logs.
     3. Whether the issue is an application bug or an environment limitation.

