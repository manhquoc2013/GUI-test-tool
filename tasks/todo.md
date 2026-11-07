# Tasks: Local Embedded Database (H2) Acceleration

- [ ] Task 1: Create `DatabaseManager` with connection pooling & schema DDL <!-- id: 1 -->
- [ ] Task 2: Unit tests for `DatabaseManager` <!-- id: 2 -->
- [ ] Task 3: Create `LocalDataIndexService` for synchronizing disk data into H2 <!-- id: 3 -->
- [ ] Task 4: Create `ProjectDbService` and `ReportDbService` with fast indexed queries <!-- id: 4 -->
- [ ] Task 5: Unit tests for `LocalDataIndexService` and query services <!-- id: 5 -->
- [ ] Task 6: Integrate DB index write-through into `ProjectStore`, `ReportStore`, and `AiChatHistoryStore` <!-- id: 6 -->
- [ ] Task 7: Initialize background/startup DB synchronization in `App.java` and stores <!-- id: 7 -->
- [ ] Task 8: Run full unit test suite (`mvn test -o`) and verify zero regression <!-- id: 8 -->
