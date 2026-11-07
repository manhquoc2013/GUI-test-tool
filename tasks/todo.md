# Tasks: Local Embedded Database (H2) Acceleration

- [x] Task 1: Create `DatabaseManager` with connection pooling & schema DDL <!-- id: 1 -->
- [x] Task 2: Unit tests for `DatabaseManager` <!-- id: 2 -->
- [x] Task 3: Create `LocalDataIndexService` for synchronizing disk data into H2 <!-- id: 3 -->
- [x] Task 4: Create `ProjectDbService` and `ReportDbService` with fast indexed queries <!-- id: 4 -->
- [x] Task 5: Unit tests for `LocalDataIndexService` and query services <!-- id: 5 -->
- [x] Task 6: Integrate DB index write-through into `ProjectStore`, `ReportStore`, and `AiChatHistoryStore` <!-- id: 6 -->
- [x] Task 7: Initialize background/startup DB synchronization in `App.java` and stores <!-- id: 7 -->
- [x] Task 8: Run full unit test suite (`mvn test -o`) and verify zero regression <!-- id: 8 -->
