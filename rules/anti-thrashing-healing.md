---
paths:
  - "**/*.java"
  - "skills/**/*.md"
  - ".agents/**/*.md"
---

# Anti-Thrashing & Self-Healing Guard Rules

1. **Max Patch Limit**:
   - When an AI agent is automatically debugging or fixing a failing test script or source file, it must track the number of patch attempts.
   - If the same file is modified **more than 3 times** without achieving a green test or passing build, the agent MUST immediately stop modifying the file.

2. **Forensics on Halt**:
   - On reaching the limit, the agent must review the git diff across all 3 attempts.
   - The agent must assess whether:
     a) The failure is caused by an incorrect test assumption.
     b) The failure represents a real bug in the application logic that cannot be fixed by modifying the test.
     c) The environment or external dependency is unavailable.

3. **Escalation**:
   - Report the full forensic summary, failing stack trace, and attempted hypotheses directly to the developer instead of looping infinitely.

