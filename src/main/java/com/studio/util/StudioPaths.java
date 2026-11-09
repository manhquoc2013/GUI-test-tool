package com.studio.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class StudioPaths {
    private static Path APP_HOME;
    private static Path DATA_HOME;

    static {
        initPaths();
    }

    private static void initPaths() {
        // 1. System property (jpackage sets -Dstudio.home=$ROOTDIR)
        String prop = System.getProperty("studio.home");
        if (prop != null && !prop.isBlank() && !"$ROOTDIR".equalsIgnoreCase(prop.trim())) {
            APP_HOME = Paths.get(prop);
        }
        // 2. Environment variables
        if (APP_HOME == null) {
            String env = System.getenv("STUDIO_HOME");
            if (env == null || env.isBlank()) {
                env = System.getenv("APP_HOME");
            }
            if (env != null && !env.isBlank()) {
                APP_HOME = Paths.get(env);
            }
        }
        // 3. Jar / classpath location (jpackage: .../app/foo.jar → parent of app/)
        if (APP_HOME == null) {
            try {
                Path codeSource = Paths.get(StudioPaths.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                // Maven/IDE: .../target/classes → do NOT use target/ (wiped by mvn clean)
                if (Files.isDirectory(codeSource)
                        && "classes".equalsIgnoreCase(name(codeSource))) {
                    Path parent = codeSource.getParent();
                    if (parent != null && "target".equalsIgnoreCase(name(parent))) {
                        // fall through to user.dir
                    } else if (parent != null) {
                        APP_HOME = parent;
                    }
                } else {
                    Path parent = codeSource.getParent();
                    if (parent != null && "target".equalsIgnoreCase(name(parent))) {
                        // JAR inside target/ (e.g. target/playwright-studio.jar) -> fall through to user.dir
                    } else if (parent != null && "app".equalsIgnoreCase(name(parent))
                            && parent.getParent() != null) {
                        APP_HOME = parent.getParent();
                    } else if (parent != null) {
                        APP_HOME = parent;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // 4. user.dir fallback (dev + Maven javafx:run)
        if (APP_HOME == null) {
            APP_HOME = Paths.get(System.getProperty("user.dir"));
        }

        DATA_HOME = determineDataHome(APP_HOME);
    }

    public static Path determineDataHome(Path appHome) {
        // 1. Explicit system property override
        String prop = System.getProperty("studio.data.dir");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop);
        }
        // 2. Explicit environment variable override
        String env = System.getenv("STUDIO_DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        // 3. Check if appHome is an installed system location or read-only
        if (isSystemOrReadOnlyLocation(appHome)) {
            return getDefaultUserDataDir();
        }
        // 4. Portable or dev mode: store data alongside application
        return appHome;
    }

    public static boolean isSystemOrReadOnlyLocation(Path path) {
        if (path == null) {
            return true;
        }
        String raw = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String abs = path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);

        // macOS app bundles or Applications folder
        if (raw.contains("/applications/") || raw.startsWith("/applications")
                || raw.contains(".app/contents") || raw.endsWith(".app")
                || abs.contains("/applications/") || abs.contains(".app/contents") || abs.endsWith(".app")) {
            return true;
        }
        // Windows Program Files
        if (raw.contains("program files") || raw.contains("program files (x86)")
                || abs.contains("program files") || abs.contains("program files (x86)")) {
            return true;
        }
        String pf = System.getenv("ProgramFiles");
        if (pf != null && !pf.isBlank() && abs.startsWith(pf.replace('\\', '/').toLowerCase(Locale.ROOT))) {
            return true;
        }
        String pfx86 = System.getenv("ProgramFiles(x86)");
        if (pfx86 != null && !pfx86.isBlank() && abs.startsWith(pfx86.replace('\\', '/').toLowerCase(Locale.ROOT))) {
            return true;
        }
        String pfW64 = System.getenv("ProgramW6432");
        if (pfW64 != null && !pfW64.isBlank() && abs.startsWith(pfW64.replace('\\', '/').toLowerCase(Locale.ROOT))) {
            return true;
        }
        // Unix system directories
        if (raw.startsWith("/usr/") || raw.startsWith("/opt/") || raw.startsWith("/etc/")
                || abs.startsWith("/usr/") || abs.startsWith("/opt/") || abs.startsWith("/etc/")
                || abs.contains("/usr/") || abs.contains("/opt/") || abs.contains("/etc/")) {
            return true;
        }
        // Windows system directories (Windows\System32 etc)
        if (raw.contains("/windows/") || abs.contains("/windows/")) {
            return true;
        }
        // If not writable (attribute check + active write probe)
        try {
            if (Files.exists(path)) {
                if (!Files.isWritable(path)) {
                    return true;
                }
                Path probe = path.resolve(".studio-write-probe-" + System.currentTimeMillis());
                try {
                    Files.createFile(probe);
                    Files.deleteIfExists(probe);
                } catch (Exception writeFailed) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static Path getDefaultUserDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", ".");
        if (os.contains("mac")) {
            Path testStudio = Paths.get(userHome, "Library", "Application Support", "TestStudio");
            Path legacy = Paths.get(userHome, "Library", "Application Support", "PlaywrightAutomationStudio");
            if (!Files.exists(testStudio) && Files.exists(legacy)) {
                return legacy;
            }
            return testStudio;
        } else if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            Path base = (appdata != null && !appdata.isBlank())
                    ? Paths.get(appdata)
                    : Paths.get(userHome, "AppData", "Roaming");
            Path testStudio = base.resolve("TestStudio");
            Path legacy = base.resolve("PlaywrightAutomationStudio");
            if (!Files.exists(testStudio) && Files.exists(legacy)) {
                return legacy;
            }
            return testStudio;
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            Path base = (xdg != null && !xdg.isBlank())
                    ? Paths.get(xdg)
                    : Paths.get(userHome, ".local", "share");
            Path testStudio = base.resolve("test-studio");
            Path legacy = base.resolve("playwright-automation-studio");
            if (!Files.exists(testStudio) && Files.exists(legacy)) {
                return legacy;
            }
            return testStudio;
        }
    }

    private static String name(Path path) {
        Path fn = path.getFileName();
        return fn != null ? fn.toString() : "";
    }

    public static Path getAppHome() { return APP_HOME; }
    public static Path getDataHome() { return DATA_HOME; }
    public static Path getDataDir() { return resolve("data"); }
    public static Path getProjectDir() { return resolve("data", "project"); }
    public static Path getReportsDir() { return resolve("data", "reports"); }
    public static Path getConfigDir() { return resolve("data", "config"); }
    public static Path getExportsDir() { return resolve("data", "exports"); }
    public static Path getLogsDir() { return resolve("data", "logs"); }
    public static Path getWorkDir() { return resolve("work"); }
    public static Path getCompileDir() { return resolve("work", "compile"); }
    public static Path getHarnessDir() { return resolve("work", "harness"); }

    public static Path resolve(String... segments) {
        if (segments == null || segments.length == 0) {
            return APP_HOME;
        }
        String first = segments[0];
        if ("data".equalsIgnoreCase(first) || "work".equalsIgnoreCase(first)) {
            return Paths.get(DATA_HOME.toString(), segments);
        }
        return Paths.get(APP_HOME.toString(), segments);
    }

    public static Path getToolsDir() { return APP_HOME.resolve("tools"); }
    public static Path getNodeDir() { return getToolsDir().resolve("node"); }

    public static Path getBrowsersDir() {
        Path bundled = APP_HOME.resolve("browsers");
        if (Files.isDirectory(bundled)) {
            return bundled;
        }
        if (DATA_HOME != null && !DATA_HOME.equals(APP_HOME)) {
            Path inData = DATA_HOME.resolve("browsers");
            if (Files.isDirectory(inData)) {
                return inData;
            }
        }
        return bundled;
    }

    public static Path getNodeExecutable() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String nodeExe = os.contains("win") ? "node.exe" : "node";

        // 1. Check APP_HOME/tools/node/bin or APP_HOME/tools/node
        Path inAppBin = APP_HOME.resolve("tools").resolve("node").resolve("bin").resolve(nodeExe);
        if (Files.exists(inAppBin)) {
            return inAppBin;
        }
        Path inAppRoot = APP_HOME.resolve("tools").resolve("node").resolve(nodeExe);
        if (Files.exists(inAppRoot)) {
            return inAppRoot;
        }
        // 2. Check DATA_HOME/tools/node if distinct
        if (DATA_HOME != null && !DATA_HOME.equals(APP_HOME)) {
            Path inDataBin = DATA_HOME.resolve("tools").resolve("node").resolve("bin").resolve(nodeExe);
            if (Files.exists(inDataBin)) {
                return inDataBin;
            }
            Path inDataRoot = DATA_HOME.resolve("tools").resolve("node").resolve(nodeExe);
            if (Files.exists(inDataRoot)) {
                return inDataRoot;
            }
        }
        return inAppBin;
    }

    public static Path getPlaywrightCliDir() {
        Path inApp = APP_HOME.resolve("tools").resolve("playwright-cli");
        if (Files.isDirectory(inApp)) {
            return inApp;
        }
        if (DATA_HOME != null && !DATA_HOME.equals(APP_HOME)) {
            Path inData = DATA_HOME.resolve("tools").resolve("playwright-cli");
            if (Files.isDirectory(inData)) {
                return inData;
            }
        }
        return inApp;
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(getDataDir());
        Files.createDirectories(getProjectDir());
        Files.createDirectories(getReportsDir());
        Files.createDirectories(getConfigDir());
        Files.createDirectories(getExportsDir());
        Files.createDirectories(getLogsDir());
        Files.createDirectories(getWorkDir());
        Files.createDirectories(getCompileDir());
        Files.createDirectories(getHarnessDir());
    }

    public static void setPathsForTesting(Path appHome, Path dataHome) {
        APP_HOME = appHome;
        DATA_HOME = dataHome != null ? dataHome : determineDataHome(appHome);
    }

    public static void resetForTesting() {
        initPaths();
    }
}
