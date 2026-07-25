package com.studio.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StudioPaths {
    private static Path APP_HOME;

    static {
        // 1. System property (jpackage sets -Dstudio.home=$ROOTDIR)
        String prop = System.getProperty("studio.home");
        if (prop != null && !prop.isBlank()) {
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
                    if (parent != null && "app".equalsIgnoreCase(name(parent))
                            && parent.getParent() != null) {
                        APP_HOME = parent.getParent();
                    } else {
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
    }

    private static String name(Path path) {
        Path fn = path.getFileName();
        return fn != null ? fn.toString() : "";
    }

    public static Path getAppHome() { return APP_HOME; }
    public static Path getDataDir() { return resolve("data"); }
    public static Path getProjectDir() { return resolve("data", "project"); }
    public static Path getReportsDir() { return resolve("data", "reports"); }
    public static Path getConfigDir() { return resolve("data", "config"); }
    public static Path getExportsDir() { return resolve("data", "exports"); }
    public static Path getWorkDir() { return resolve("work"); }
    public static Path getCompileDir() { return resolve("work", "compile"); }
    public static Path getHarnessDir() { return resolve("work", "harness"); }
    public static Path resolve(String... segments) { return Paths.get(APP_HOME.toString(), segments); }

    public static Path getToolsDir() { return resolve("tools"); }
    public static Path getNodeDir() { return getToolsDir().resolve("node"); }

    public static Path getNodeExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        String nodeExe = os.contains("win") ? "node.exe" : "node";
        return getNodeDir().resolve("bin").resolve(nodeExe);
    }

    public static Path getPlaywrightCliDir() { return getToolsDir().resolve("playwright-cli"); }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(getDataDir());
        Files.createDirectories(getProjectDir());
        Files.createDirectories(getReportsDir());
        Files.createDirectories(getConfigDir());
        Files.createDirectories(getExportsDir());
        Files.createDirectories(getWorkDir());
        Files.createDirectories(getCompileDir());
        Files.createDirectories(getHarnessDir());
    }
}
