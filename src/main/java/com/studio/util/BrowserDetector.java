package com.studio.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrowserDetector {

    public static class BrowserInfo {
        public final String id;
        public final String name;
        public final String type;
        public final String path;

        public BrowserInfo(String id, String name, String type, String path) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.path = path;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static List<BrowserInfo> detectAvailable() {
        List<BrowserInfo> list = new ArrayList<>();

        Path pwBase = resolvePlaywrightBrowsersPath();
        if (pwBase != null && Files.isDirectory(pwBase)) {
            if (hasPrefixedChild(pwBase, "chromium")) {
                list.add(new BrowserInfo("CHROMIUM", "Chromium (Playwright)", "BUNDLED", pwBase.toString()));
            }
            if (hasPrefixedChild(pwBase, "firefox")) {
                list.add(new BrowserInfo("FIREFOX", "Firefox (Playwright)", "BUNDLED", pwBase.toString()));
            }
            if (hasPrefixedChild(pwBase, "webkit")) {
                list.add(new BrowserInfo("WEBKIT", "WebKit (Playwright)", "BUNDLED", pwBase.toString()));
            }
        }

        // Always offer Chromium as fallback option when no bundled browsers found
        if (list.stream().noneMatch(b -> "CHROMIUM".equals(b.id))) {
            list.add(0, new BrowserInfo("CHROMIUM", "Chromium (Playwright)", "BUNDLED",
                    pwBase != null ? pwBase.toString() : ""));
        }

        detectSystemBrowsers(list);
        return list;
    }

    static Path resolvePlaywrightBrowsersPath() {
        String env = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        Path portable = StudioPaths.getAppHome().resolve("browsers");
        if (Files.isDirectory(portable)) {
            return portable;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                return Paths.get(local, "ms-playwright");
            }
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Caches", "ms-playwright");
        }
        return Paths.get(System.getProperty("user.home"), ".cache", "ms-playwright");
    }

    /** True if any direct child directory name starts with the given prefix (e.g. chromium-1234). */
    static boolean hasPrefixedChild(Path base, String prefix) {
        if (base == null || !Files.isDirectory(base)) {
            return false;
        }
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith(lowerPrefix)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private static void detectSystemBrowsers(List<BrowserInfo> list) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            if (Files.exists(Paths.get("/Applications/Google Chrome.app"))) {
                list.add(new BrowserInfo("CHROME", "Google Chrome", "SYSTEM", "/Applications/Google Chrome.app"));
            }
            if (Files.exists(Paths.get("/Applications/Microsoft Edge.app"))) {
                list.add(new BrowserInfo("EDGE", "Microsoft Edge", "SYSTEM", "/Applications/Microsoft Edge.app"));
            }
        } else if (os.contains("win")) {
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFiles != null) {
                Path chrome = Paths.get(programFiles, "Google", "Chrome", "Application", "chrome.exe");
                if (Files.exists(chrome)) {
                    list.add(new BrowserInfo("CHROME", "Google Chrome", "SYSTEM", chrome.toString()));
                }
            }
            if (programFilesX86 != null) {
                Path edge = Paths.get(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe");
                if (Files.exists(edge)) {
                    list.add(new BrowserInfo("EDGE", "Microsoft Edge", "SYSTEM", edge.toString()));
                }
            }
        } else {
            if (Files.exists(Paths.get("/usr/bin/google-chrome"))
                    || Files.exists(Paths.get("/usr/bin/google-chrome-stable"))) {
                Path chrome = Files.exists(Paths.get("/usr/bin/google-chrome"))
                        ? Paths.get("/usr/bin/google-chrome")
                        : Paths.get("/usr/bin/google-chrome-stable");
                list.add(new BrowserInfo("CHROME", "Google Chrome", "SYSTEM", chrome.toString()));
            }
            if (Files.exists(Paths.get("/usr/bin/microsoft-edge"))
                    || Files.exists(Paths.get("/usr/bin/microsoft-edge-stable"))) {
                Path edge = Files.exists(Paths.get("/usr/bin/microsoft-edge"))
                        ? Paths.get("/usr/bin/microsoft-edge")
                        : Paths.get("/usr/bin/microsoft-edge-stable");
                list.add(new BrowserInfo("EDGE", "Microsoft Edge", "SYSTEM", edge.toString()));
            }
        }
    }
}
