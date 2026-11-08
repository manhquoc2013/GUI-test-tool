# Todo List: System Review Issue Resolution & Performance Optimization

- [x] 1. Fix duplicated AI error handling block in `AiAssistantPageController.java` to prevent NPE and double-rendering <!-- id: 1 -->
- [x] 2. Implement atomic JSON file write in `AiChatHistoryStore.java` using temp file + atomic move to prevent corruption <!-- id: 2 -->
- [x] 3. Eliminate N+1 JDBC connections via batch message indexing (`indexChatMessagesBatch`) in `LocalDataIndexService.java` <!-- id: 3 -->
- [x] 4. Remove redundant `refreshSessionsList` call in `handleDeleteSession` and unify session deletion SQL in `LocalDataIndexService` <!-- id: 4 -->
- [x] 5. Run full Maven test suite (`mvn clean test`), verify zero regressions, and update institutional learnings <!-- id: 5 -->
