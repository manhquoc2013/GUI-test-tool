# Learnings — s-review remediation #2 (2026-11-14)

- Restoring sessions.json in the working tree is not enough if a prior commit already bloated HEAD; tip must match origin (or a tiny fixture) before push.
- Primitive unit tests for `stop_channel` do not guard call-site wiring — assert registry/`on_done` through `toggle_sidebar` mutual-exclusion path.
