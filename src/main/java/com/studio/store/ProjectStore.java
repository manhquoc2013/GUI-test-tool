package com.studio.store;

import com.studio.model.*;
import com.studio.service.ScriptHarnessTransformer;
import com.studio.util.EntityTime;
import com.studio.util.StudioPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectStore {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String GROUP_JSON = "group.json";
    private static final String SUITE_JSON = "suite.json";
    private final com.studio.db.LocalDataIndexService indexService = new com.studio.db.LocalDataIndexService();

    public ProjectStore() {
    }

    // ---- load ----

    /**
     * Recursively walk project dir and build root TestGroup.
     * Hierarchy is Group → Suite → Case (suites live on {@link TestGroup#getSuites()},
     * nested groups on {@link TestGroup#getChildren()} — never mixed).
     */
    public TestGroup loadTree() throws IOException {
        Path projectDir = StudioPaths.getProjectDir();
        TestGroup root = new TestGroup("Project", "Root project group");
        root.setType("group");
        root.setChildren(loadChildGroups(projectDir));
        root.setSuites(loadSuitesInDir(projectDir));
        return root;
    }

    /** Only directories that contain group.json. */
    private List<TestGroup> loadChildGroups(Path dir) throws IOException {
        List<TestGroup> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                if (!Files.exists(entry.resolve(GROUP_JSON))) continue;

                TestGroup group = mapper.readValue(entry.resolve(GROUP_JSON).toFile(), TestGroup.class);
                group.setType("group");
                group.setChildren(loadChildGroups(entry));
                group.setSuites(loadSuitesInDir(entry));
                result.add(group);
            }
        }
        return result;
    }

    /** Only directories that contain suite.json (not groups). */
    private List<TestSuite> loadSuitesInDir(Path dir) throws IOException {
        List<TestSuite> suites = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                if (!Files.exists(entry.resolve(SUITE_JSON))) continue;
                if (Files.exists(entry.resolve(GROUP_JSON))) continue;

                TestSuite suite = loadAndMigrateSuite(entry);
                mergeOrphanJavaCases(suite, entry);
                suites.add(suite);
            }
        }
        return suites;
    }

    /** Attach .java files not yet listed in suite.json as draft cases. */
    private void mergeOrphanJavaCases(TestSuite suite, Path suiteDir) throws IOException {
        if (suite.getCases() == null) {
            suite.setCases(new ArrayList<>());
        }
        try (DirectoryStream<Path> js = Files.newDirectoryStream(suiteDir, "*.java")) {
            for (Path javaFile : js) {
                String fileName = javaFile.getFileName().toString().replace(".java", "");
                boolean found = suite.getCases().stream().anyMatch(c -> c.getName().equals(fileName));
                if (!found) {
                    TestCase tc = new TestCase();
                    tc.setId("tc-" + fileName.toLowerCase().replaceAll("[^a-z0-9]", "-"));
                    tc.setName(fileName);
                    tc.setScriptFile(fileName + ".java");
                    tc.setStatus("Draft");
                    suite.getCases().add(tc);
                }
            }
        }
    }

    // ---- save ----

    /**
     * Atomically writes an object to a JSON file via a temporary sibling file and atomic move.
     * Prevents partial/corrupted files in case of sudden power loss or process kill.
     */
    public static void writeJsonAtomic(Path targetPath, Object value) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), targetPath.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(tempFile.toFile(), value);
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Atomically writes string content (e.g. Java source code) to a file via a temporary sibling file and atomic move.
     * Prevents partial/corrupted files in case of sudden power loss or process kill.
     */
    public static void writeTextAtomic(Path targetPath, String content) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), targetPath.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempFile, content != null ? content : "");
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {}
        }
    }

    public void saveGroup(TestGroup group, Path groupDir) throws IOException {
        Files.createDirectories(groupDir);
        writeJsonAtomic(groupDir.resolve(GROUP_JSON), group);
        indexService.indexGroup(group, groupDir);
    }

    public void saveSuite(TestSuite suite, Path suiteDir) throws IOException {
        Files.createDirectories(suiteDir);
        suite.migrateLegacyAssertionsToCases();
        suite.clearLegacyAssertions();
        backfillSuiteTimestamps(suite);
        writeJsonAtomic(suiteDir.resolve(SUITE_JSON), suite);
        String groupName = suiteDir.getParent() != null ? suiteDir.getParent().getFileName().toString() : "";
        indexService.indexSuite(suite, suiteDir, groupName);
    }

    /** Load suite.json and migrate legacy suite-level assertions onto cases. */
    public TestSuite loadAndMigrateSuite(Path suiteDir) throws IOException {
        Path suiteJsonPath = suiteDir.resolve(SUITE_JSON);
        if (!Files.exists(suiteJsonPath)) {
            Files.createDirectories(suiteDir);
            String name = suiteDir.getFileName().toString();
            TestSuite suite = new TestSuite(name, "");
            suite.setStatus("ACTIVE");
            suite.setBrowser("CHROMIUM");
            suite.setHeadless(false);
            suite.setCases(new ArrayList<>());
            String now = EntityTime.nowIso();
            suite.setCreatedAt(now);
            suite.setUpdatedAt(now);
            saveSuite(suite, suiteDir);
            return suite;
        }
        TestSuite suite = mapper.readValue(suiteJsonPath.toFile(), TestSuite.class);
        boolean changed = suite.migrateLegacyAssertionsToCases();
        if (changed) {
            suite.clearLegacyAssertions();
        }
        if (backfillSuiteTimestamps(suite)) {
            changed = true;
        }
        if (changed) {
            writeJsonAtomic(suiteJsonPath, suite);
        }
        return suite;
    }

    private boolean backfillSuiteTimestamps(TestSuite suite) {
        boolean changed = EntityTime.backfill(
                suite.getCreatedAt(), suite.getUpdatedAt(),
                suite::setCreatedAt, suite::setUpdatedAt);
        for (TestCase tc : suite.getCases()) {
            if (EntityTime.backfill(
                    tc.getCreatedAt(), tc.getUpdatedAt(),
                    tc::setCreatedAt, tc::setUpdatedAt)) {
                changed = true;
            }
        }
        return changed;
    }

    // ---- create ----

    public TestGroup createGroup(Path parentDir, String name) throws IOException {
        return createGroup(parentDir, name, "");
    }

    public TestGroup loadGroup(Path groupDir) throws IOException {
        Path groupJson = groupDir.resolve(GROUP_JSON);
        if (!Files.exists(groupJson)) {
            Files.createDirectories(groupDir);
            String name = groupDir.getFileName().toString();
            TestGroup group = new TestGroup(name, "");
            group.setType("group");
            saveGroup(group, groupDir);
            return group;
        }
        return mapper.readValue(groupJson.toFile(), TestGroup.class);
    }

    public TestGroup createGroup(Path parentDir, String name, String description) throws IOException {
        Path groupDir = parentDir.resolve(name);
        Files.createDirectories(groupDir);
        TestGroup group = new TestGroup(name, description != null ? description : "");
        group.setType("group");
        writeJsonAtomic(groupDir.resolve(GROUP_JSON), group);
        return group;
    }

    public TestSuite createSuite(Path groupDir, String name) throws IOException {
        return createSuite(groupDir, name, "", "DRAFT", "", null);
    }

    public TestSuite createSuite(Path groupDir, String name, String description,
                                 String status, String browser, Boolean headless) throws IOException {
        Path suiteDir = groupDir.resolve(name);
        Files.createDirectories(suiteDir);
        TestSuite suite = new TestSuite(name, description != null ? description : "");
        suite.setStatus(status != null && !status.isBlank() ? status : "DRAFT");
        suite.setBrowser(browser != null ? browser : "");
        suite.setHeadless(headless);
        String now = EntityTime.nowIso();
        suite.setCreatedAt(now);
        suite.setUpdatedAt(now);
        writeJsonAtomic(suiteDir.resolve(SUITE_JSON), suite);
        return suite;
    }

    public TestCase createCase(Path suiteDir, String name, String description) throws IOException {
        return createCase(suiteDir, name, description, "Draft", "", "", null);
    }

    public TestCase createCase(Path suiteDir, String name, String description,
                               String status, String startUrl, String browser, Boolean headless)
            throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        String caseId = "tc-" + UUID.randomUUID().toString().substring(0, 8);
        String safeFileBase = com.studio.util.StringEscapeUtil.safeFileName(name, "case");
        String scriptFileName = safeFileBase + ".java";
        TestCase tc = new TestCase(caseId, name,
                description != null ? description : "",
                scriptFileName,
                status != null && !status.isBlank() ? status : "Draft");
        tc.setStartUrl(startUrl != null ? startUrl : "");
        tc.setBrowser(browser != null ? browser : "");
        tc.setHeadless(headless);
        String now = EntityTime.nowIso();
        tc.setCreatedAt(now);
        tc.setUpdatedAt(now);
        suite.getCases().add(tc);
        suite.setUpdatedAt(now);
        backfillSuiteTimestamps(suite);
        writeJsonAtomic(suiteJson, suite);

        String safeClass = ScriptHarnessTransformer.sanitizeClassName(name);
        String stub = String.format(
                "package com.test;\n\nimport com.microsoft.playwright.*;\n\npublic class %s {\n    public static void main(String[] args) {\n        // Test case: %s\n    }\n}\n",
                safeClass, name);
        writeTextAtomic(suiteDir.resolve(scriptFileName), stub);

        return tc;
    }

    /** Persist suite-level metadata fields (not cases). */
    public void updateSuiteMeta(Path suiteDir, String description, String status,
                                String browser, Boolean headless) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        suite.setDescription(description != null ? description : "");
        if (status != null && !status.isBlank()) {
            suite.setStatus(status);
        }
        suite.setBrowser(browser != null ? browser : "");
        suite.setHeadless(headless);
        suite.setUpdatedAt(EntityTime.nowIso());
        backfillSuiteTimestamps(suite);
        writeJsonAtomic(suiteJson, suite);
    }

    /** Persist case-level metadata fields. */
    public void updateCaseMeta(Path suiteDir, String caseId, String description, String status,
                               String startUrl, String browser, Boolean headless) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        TestCase tc = requireCase(suite, caseId);
        tc.setDescription(description != null ? description : "");
        if (status != null && !status.isBlank()) {
            tc.setStatus(status);
        }
        tc.setStartUrl(startUrl != null ? startUrl : "");
        tc.setBrowser(browser != null ? browser : "");
        tc.setHeadless(headless);
        String now = EntityTime.nowIso();
        tc.setUpdatedAt(now);
        suite.setUpdatedAt(now);
        backfillSuiteTimestamps(suite);
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateGroupMeta(Path groupDir, String description) throws IOException {
        Path groupJson = groupDir.resolve(GROUP_JSON);
        TestGroup group = mapper.readValue(groupJson.toFile(), TestGroup.class);
        group.setDescription(description != null ? description : "");
        writeJsonAtomic(groupJson, group);
    }

    // ---- delete ----

    /** Deletes group directory and all nested groups / suites / cases on disk. */
    public void deleteGroup(Path groupDir) throws IOException {
        if (groupDir != null && Files.exists(groupDir)) {
            try (var walk = Files.walk(groupDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
        }
        indexService.deleteGroup(groupDir.getFileName().toString());
    }

    public void deleteSuite(Path suiteDir) throws IOException {
        if (suiteDir != null && Files.exists(suiteDir)) {
            try (var walk = Files.walk(suiteDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
            }
        }
        String groupName = suiteDir.getParent() != null ? suiteDir.getParent().getFileName().toString() : "";
        indexService.deleteSuite(groupName, suiteDir.getFileName().toString());
    }

    public void deleteCase(Path suiteDir, String caseId) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        // Before filtering the list, capture the case to delete:
        TestCase target = suite.getCases().stream()
            .filter(c -> c.getId().equals(caseId))
            .findFirst().orElse(null);

        suite.setCases(suite.getCases().stream()
                .filter(c -> !c.getId().equals(caseId))
                .collect(Collectors.toList()));
        writeJsonAtomic(suiteJson, suite);

        // Delete .java file using the captured target
        if (target != null && target.getScriptFile() != null) {
            Path javaFile = suiteDir.resolve(target.getScriptFile());
            Files.deleteIfExists(javaFile);
        }
        indexService.deleteCase(caseId);
    }

    // ---- rename ----

    public void renameGroup(Path groupDir, String newName) throws IOException {
        Path newDir = groupDir.resolveSibling(newName);
        Files.move(groupDir, newDir, StandardCopyOption.REPLACE_EXISTING);
        TestGroup group = mapper.readValue(newDir.resolve(GROUP_JSON).toFile(), TestGroup.class);
        group.setName(newName);
        writeJsonAtomic(newDir.resolve(GROUP_JSON), group);
    }

    public void renameSuite(Path suiteDir, String newName) throws IOException {
        Path newDir = suiteDir.resolveSibling(newName);
        Files.move(suiteDir, newDir, StandardCopyOption.REPLACE_EXISTING);
        TestSuite suite = mapper.readValue(newDir.resolve(SUITE_JSON).toFile(), TestSuite.class);
        suite.setName(newName);
        writeJsonAtomic(newDir.resolve(SUITE_JSON), suite);
    }

    public void renameCase(Path suiteDir, String caseId, String newName) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        for (TestCase tc : suite.getCases()) {
            if (tc.getId().equals(caseId)) {
                String oldName = tc.getName();
                tc.setName(newName);

                // Rename .java file if different
                if (!oldName.equals(newName)) {
                    Path oldFile = suiteDir.resolve(oldName + ".java");
                    Path newFile = suiteDir.resolve(newName + ".java");
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                    tc.setScriptFile(newName + ".java");
                }
                break;
            }
        }
        writeJsonAtomic(suiteJson, suite);
    }

    // ---- move / copy ----

    public void moveSuite(Path fromDir, Path toGroupDir) throws IOException {
        Path dest = toGroupDir.resolve(fromDir.getFileName());
        if (Files.exists(dest)) {
            throw new IOException("Suite '" + fromDir.getFileName() + "' đã tồn tại trong nhóm đích");
        }
        Files.move(fromDir, dest);
    }

    public void copySuite(Path fromDir, Path toGroupDir, String newName) throws IOException {
        Path toDir = toGroupDir.resolve(newName);
        if (Files.exists(toDir)) {
            throw new IOException("Suite '" + newName + "' đã tồn tại");
        }
        copyDirectory(fromDir, toDir);
        TestSuite suite = mapper.readValue(toDir.resolve(SUITE_JSON).toFile(), TestSuite.class);
        suite.setName(newName);
        writeJsonAtomic(toDir.resolve(SUITE_JSON), suite);
    }

    private void copyDirectory(Path from, Path to) throws IOException {
        try {
            try (var walk = Files.walk(from)) {
                walk.forEach(source -> {
                    try {
                        Path dest = to.resolve(from.relativize(source));
                        if (!Files.exists(dest)) {
                            Files.copy(source, dest);
                        }
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public TestCase copyCase(Path suiteDir, String caseId, String newName) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        TestCase original = suite.getCases().stream()
                .filter(c -> c.getId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        String newId = "tc-" + UUID.randomUUID().toString().substring(0, 8);
        TestCase copy = new TestCase(newId, newName, original.getDescription(), newName + ".java", original.getStatus());
        List<Assertion> copiedAsserts = new ArrayList<>();
        for (Assertion a : original.getAssertions()) {
            Assertion clone = new Assertion();
            clone.setType(a.getType());
            clone.setValue(a.getValue());
            clone.setLocator(a.getLocator());
            clone.setStatus(a.getStatus());
            clone.setExact(a.isExact());
            clone.setTimeoutMs(a.getTimeoutMs());
            copiedAsserts.add(clone);
        }
        copy.setAssertions(copiedAsserts);
        suite.getCases().add(copy);
        writeJsonAtomic(suiteJson, suite);

        // Copy .java file
        if (original.getScriptFile() != null) {
            Path srcFile = suiteDir.resolve(original.getScriptFile());
            Path dstFile = suiteDir.resolve(newName + ".java");
            Files.copy(srcFile, dstFile);
        }

        return copy;
    }

    // ---- reorder ----

    public void reorderCases(Path suiteDir, List<String> caseIds) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        List<TestCase> ordered = new ArrayList<>();
        for (String id : caseIds) {
            Optional<TestCase> found = suite.getCases().stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst();
            found.ifPresent(ordered::add);
        }
        suite.setCases(ordered);
        writeJsonAtomic(suiteJson, suite);
    }

    public void reorderAssertions(Path suiteDir, String caseId, List<Integer> indices) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        TestCase tc = requireCase(suite, caseId);

        List<Assertion> ordered = new ArrayList<>();
        for (int i : indices) {
            if (i >= 0 && i < tc.getAssertions().size()) {
                ordered.add(tc.getAssertions().get(i));
            }
        }
        tc.setAssertions(ordered);
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    // ---- status updates ----

    public void updateSuiteStatus(Path suiteDir, String status) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        suite.setStatus(status);
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateCaseStatus(Path suiteDir, String caseId, String status) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        for (TestCase tc : suite.getCases()) {
            if (tc.getId().equals(caseId)) {
                tc.setStatus(status);
                break;
            }
        }
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateCaseStartUrl(Path suiteDir, String caseId, String startUrl) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        for (TestCase tc : suite.getCases()) {
            if (tc.getId().equals(caseId)) {
                tc.setStartUrl(startUrl != null ? startUrl.trim() : "");
                break;
            }
        }
        writeJsonAtomic(suiteJson, suite);
    }

    // ---- assertions CRUD (per TestCase) ----

    public void addAssertion(Path suiteDir, String caseId, Assertion a) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        requireCase(suite, caseId).getAssertions().add(a);
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateAssertion(Path suiteDir, String caseId, int index, Assertion a) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        requireCase(suite, caseId).getAssertions().set(index, a);
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    public void deleteAssertion(Path suiteDir, String caseId, int index) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        requireCase(suite, caseId).getAssertions().remove(index);
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    public void replaceAssertions(Path suiteDir, String caseId, List<Assertion> assertions) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        requireCase(suite, caseId).setAssertions(new ArrayList<>(assertions));
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateCaseStepsAndData(Path suiteDir, String caseId, List<com.studio.model.TestStep> steps, List<com.studio.model.TestDataItem> dataSets) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        TestCase tc = requireCase(suite, caseId);
        if (steps != null) {
            tc.setTestSteps(new ArrayList<>(steps));
        }
        if (dataSets != null) {
            tc.setTestDataSets(new ArrayList<>(dataSets));
        }
        suite.clearLegacyAssertions();
        writeJsonAtomic(suiteJson, suite);
    }

    public void updateCasePriority(Path suiteDir, String caseId, String priority) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
        TestCase tc = requireCase(suite, caseId);
        tc.setPriority(priority);
        writeJsonAtomic(suiteJson, suite);
    }

    private TestCase requireCase(TestSuite suite, String caseId) {
        return suite.getCases().stream()
                .filter(c -> c.getId().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
    }

    // ---- script CRUD ----

    public void saveCaseScript(Path suiteDir, String caseId, String javaCode) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        TestCase matched = null;
        for (TestCase tc : suite.getCases()) {
            if (tc.getId().equals(caseId)) {
                matched = tc;
                break;
            }
        }
        if (matched == null) {
            throw new IOException("Case not found: " + caseId);
        }
        String fileName = matched.getScriptFile() != null && !matched.getScriptFile().isBlank()
                ? matched.getScriptFile()
                : com.studio.util.StringEscapeUtil.safeFileName(matched.getName(), "case") + ".java";
        matched.setScriptFile(fileName);
        writeTextAtomic(suiteDir.resolve(fileName), javaCode);
        if ("Draft".equals(matched.getStatus()) && javaCode != null && !javaCode.isBlank()) {
            matched.setStatus("Ready");
        }
        writeJsonAtomic(suiteJson, suite);
        String groupName = suiteDir.getParent() != null ? suiteDir.getParent().getFileName().toString() : "";
        indexService.indexCase(matched, groupName, suite.getName(), javaCode);
    }

    public String readCaseScript(Path suiteDir, String caseId) throws IOException {
        Path suiteJson = suiteDir.resolve(SUITE_JSON);
        TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);

        TestCase matched = null;
        for (TestCase tc : suite.getCases()) {
            if (tc.getId().equals(caseId)) {
                matched = tc;
                break;
            }
        }
        if (matched == null) {
            throw new IOException("Case not found: " + caseId);
        }
        String fileName = matched.getScriptFile() != null && !matched.getScriptFile().isBlank()
                ? matched.getScriptFile()
                : matched.getName() + ".java";
        Path scriptPath = suiteDir.resolve(fileName);
        if (!Files.exists(scriptPath)) {
            throw new IOException("Script file not found: " + scriptPath);
        }
        return Files.readString(scriptPath);
    }

    // ---- collect cases for run scope ----

    public List<CaseResult> collectCasesForScope(String scope, Path scopePath) throws IOException {
        List<CaseResult> results = new ArrayList<>();

        switch (scope) {
            case "ALL":
                results = collectAllCases(StudioPaths.getProjectDir(), "");
                break;
            case "GROUP":
                // parentPath must be relative from project root so resolveSuiteDir
                // rebuilds project/<group>/…/<suite> (not project/<suite>).
                results = collectAllCases(scopePath, relativeFromProject(scopePath));
                break;
            case "SUITE":
                results = collectSuiteCases(scopePath);
                break;
            case "CASE":
                results = collectSingleCase(scopePath);
                break;
        }
        return results;
    }

    /** Walk group subtree collecting cases with skip logic. */
    private List<CaseResult> collectAllCases(Path groupDir, String parentPath) throws IOException {
        List<CaseResult> results = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(groupDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                String childPath = parentPath.isEmpty() ? entry.getFileName().toString() : parentPath + "/" + entry.getFileName().toString();

                if (Files.exists(entry.resolve(SUITE_JSON))) {
                    results.addAll(enumerateSuiteCases(entry, parentPath, entry.getFileName().toString()));
                } else if (Files.exists(entry.resolve(GROUP_JSON))) {
                    results.addAll(collectAllCases(entry, childPath));
                }
            }
        }
        return results;
    }

    private List<CaseResult> enumerateSuiteCases(Path suiteDir, String groupPath, String suiteName) throws IOException {
        List<CaseResult> results = new ArrayList<>();
        TestSuite suite = loadAndMigrateSuite(suiteDir);

        boolean suiteActive = "ACTIVE".equals(suite.getStatus());
        boolean suiteHasAnyActiveAssert = suite.hasAnyActiveAssertions();

        for (int i = 0; i < suite.getCases().size(); i++) {
            TestCase tc = suite.getCases().get(i);
            CaseResult cr = new CaseResult();
            cr.setIndex(i);
            cr.setGroupPath(groupPath);
            cr.setSuiteName(suiteName);
            cr.setSuiteDir(suiteDir.toAbsolutePath().normalize().toString());
            cr.setTestCaseId(tc.getId());
            cr.setTestCaseName(tc.getName());
            cr.setPriority(tc.getPriority());
            cr.setScriptFile(tc.getScriptFile());

            if (!suiteActive) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("Suite status is " + suite.getStatus());
            } else if (!suiteHasAnyActiveAssert) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("Suite has no active asserts");
            } else if (!"Ready".equals(tc.getStatus())) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No script (case Draft)");
            } else if (!tc.hasActiveAssertions()) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No active asserts on this case");
            } else {
                cr.setStatus("PENDING");
            }

            results.add(cr);
        }
        return results;
    }

    private List<CaseResult> collectSuiteCases(Path suiteDir) throws IOException {
        List<CaseResult> results = new ArrayList<>();
        TestSuite suite = loadAndMigrateSuite(suiteDir);
        String suiteName = suiteDir.getFileName().toString();
        String groupPath = relativeGroupPath(suiteDir);
        String suiteDirAbs = suiteDir.toAbsolutePath().normalize().toString();

        for (int i = 0; i < suite.getCases().size(); i++) {
            TestCase tc = suite.getCases().get(i);
            CaseResult cr = new CaseResult();
            cr.setIndex(i);
            cr.setGroupPath(groupPath);
            cr.setSuiteName(suiteName);
            cr.setSuiteDir(suiteDirAbs);
            cr.setTestCaseId(tc.getId());
            cr.setTestCaseName(tc.getName());
            cr.setPriority(tc.getPriority());
            cr.setScriptFile(tc.getScriptFile());

            if ("Draft".equals(tc.getStatus())) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No script (case Draft)");
            } else if (!tc.hasActiveAssertions()) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No active asserts on this case");
            } else {
                cr.setStatus("PENDING");
            }

            results.add(cr);
        }
        return results;
    }

    private List<CaseResult> collectSingleCase(Path scopePath) throws IOException {
        // scopePath = <suiteDir>/<CaseName>.java (or script file name)
        List<CaseResult> results = new ArrayList<>();
        if (scopePath == null || scopePath.getParent() == null) {
            return results;
        }
        Path suiteDir = scopePath.getParent();
        String caseName = scopePath.getFileName().toString().replace(".java", "");

        TestSuite suite = loadAndMigrateSuite(suiteDir);

        Optional<TestCase> tcOpt = suite.getCases().stream()
                .filter(c -> c.getName().equals(caseName) || c.getScriptFile() != null && c.getScriptFile().equals(caseName + ".java"))
                .findFirst();

        if (tcOpt.isPresent()) {
            TestCase tc = tcOpt.get();
            CaseResult cr = new CaseResult();
            cr.setIndex(0);
            cr.setGroupPath(relativeGroupPath(suiteDir));
            cr.setSuiteName(suiteDir.getFileName().toString());
            cr.setSuiteDir(suiteDir.toAbsolutePath().normalize().toString());
            cr.setTestCaseId(tc.getId());
            cr.setTestCaseName(tc.getName());
            cr.setPriority(tc.getPriority());
            cr.setScriptFile(tc.getScriptFile());

            if ("Draft".equals(tc.getStatus())) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No script (case Draft)");
            } else if (!tc.hasActiveAssertions()) {
                cr.setStatus("SKIPPED");
                cr.setSkipReason("No active asserts on this case");
            } else {
                cr.setStatus("PENDING");
            }

            results.add(cr);
        }
        return results;
    }

    /**
     * Relative path from project root to the suite's parent group.
     * Empty when the suite sits directly under the project root.
     */
    private static String relativeGroupPath(Path suiteDir) {
        if (suiteDir == null) return "";
        Path parent = suiteDir.toAbsolutePath().normalize().getParent();
        return relativeFromProject(parent);
    }

    /** Relative path from project root to {@code dir}; empty if dir is the project root. */
    private static String relativeFromProject(Path dir) {
        if (dir == null) return "";
        Path projectDir = StudioPaths.getProjectDir().toAbsolutePath().normalize();
        Path abs = dir.toAbsolutePath().normalize();
        if (abs.equals(projectDir)) {
            return "";
        }
        if (!abs.startsWith(projectDir)) {
            return abs.getFileName() != null ? abs.getFileName().toString() : "";
        }
        return projectDir.relativize(abs).toString().replace('\\', '/');
    }
}
