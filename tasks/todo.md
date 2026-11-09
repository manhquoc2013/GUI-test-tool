# Todo List: Auto-Create Group and Suite on AI Case Save

- [x] 1. Implement `resolveAutoSuiteName()` and auto-group/suite creation in `applyCasesToSelectedSuite()` in `AiAssistantPageController.java` <!-- id: 1 -->
- [x] 2. Update AI prompt context in `executePromptInternal()` to avoid `"null"` when target group/suite is not yet assigned <!-- id: 2 -->
- [x] 3. Write unit test `AiAssistantAutoCreateSuiteTest.java` to verify auto-creation of group/suite and test case saving when none exists <!-- id: 3 -->
- [x] 4. Run `mvn clean test` across the entire project (305 tests pass) <!-- id: 4 -->
- [x] 5. Record learnings in `tasks/learnings.md` <!-- id: 5 -->
