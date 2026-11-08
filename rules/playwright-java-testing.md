---
paths:
  - "src/main/java/com/studio/**/*.java"
  - "src/test/java/com/studio/**/*.java"
  - "data/project/**/*.java"
---

# Playwright Java Automation Testing Rules

1. **AAA Structure**:
   - Arrange: Set up inputs, navigate to page, initialize state.
   - Act: Execute user actions (click, fill, select).
   - Assert: Verify outcomes with explicit Playwright assertions.

2. **Locator Priorities**:
   - Prefer semantic locators: `page.getByRole(...)`, `page.getByText(...)`, `page.locator("[data-testid='...']")`.
   - Avoid brittle absolute XPath (e.g. `/html/body/div[2]/div/span[1]`).

3. **Explicit Waits**:
   - Do NOT use `Thread.sleep()`.
   - Use `page.waitForLoadState(LoadState.NETWORKIDLE)` or web-first assertions that auto-retry until timeout.

4. **Self-Healing Integration**:
   - For interactive elements susceptible to DOM changes, wrap with `StudioRuntime.healLocator("selector", "element description")`.

5. **Error & Exception Safety**:
   - Never use empty catch blocks in test scripts.
   - Throw descriptive assertion errors with context when expectations are not met.

