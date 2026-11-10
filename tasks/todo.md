# Todo List: Restore Undone Message Content and Files to Chat Input on Undo
<!-- Progress: 3/3 tasks (100%) | Status: COMPLETED -->

- [x] 1. Extend `AiChatMessage.java` with `rawPrompt`, `skill`, `attachedFilePaths`, and `AttachedDocSnapshot` to persist attachment data across chat sessions <!-- id: 1 -->
- [x] 2. Update `AiAssistantPageController.java`: populate `rawPrompt` and `attachedDocsSnapshot` in `sendPrompt()`, and restore message content + attached file chips into `promptInputArea` in `handleUndoMessage()` <!-- id: 2 -->
- [x] 3. Add comprehensive unit tests in `AiAssistantChatFlowTest.java` verifying that undo on user and assistant messages restores prompt text, skill, and file attachments into the input box <!-- id: 3 -->
