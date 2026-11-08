package com.studio.service;

import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized logging service for AI interactions, prompt generation, raw responses,
 * token accounting, and recovery diagnostics.
 */
public final class AiLoggingService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final List<String> IN_MEMORY_LOGS = new ArrayList<>();
    private static final int MAX_IN_MEMORY_LOGS = 300;
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB

    private AiLoggingService() {}

    public static synchronized void log(String level, String category, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String entry = String.format("[%s] [%-5s] [%s] %s", timestamp, level, category, message);

        // 1. Maintain in-memory ring buffer
        IN_MEMORY_LOGS.add(entry);
        if (IN_MEMORY_LOGS.size() > MAX_IN_MEMORY_LOGS) {
            IN_MEMORY_LOGS.remove(0);
        }

        // 2. Output to console
        if ("ERROR".equalsIgnoreCase(level) || "WARN".equalsIgnoreCase(level)) {
            System.err.println(entry);
        } else {
            System.out.println(entry);
        }

        // 3. Persist to disk: data/logs/ai.log
        try {
            Path logDir = StudioPaths.getLogsDir();
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("ai.log");

            if (Files.exists(logFile) && Files.size(logFile) > MAX_FILE_SIZE_BYTES) {
                Path backup = logDir.resolve("ai.old.log");
                Files.move(logFile, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.writeString(logFile, entry + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public static void info(String category, String message) {
        log("INFO", category, message);
    }

    public static void warn(String category, String message) {
        log("WARN", category, message);
    }

    public static void error(String category, String message) {
        log("ERROR", category, message);
    }

    public static void error(String category, String message, Throwable t) {
        String msg = message;
        if (t != null) {
            msg += " | " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        log("ERROR", category, msg);
    }

    public static synchronized List<String> getRecentLogs() {
        return new ArrayList<>(IN_MEMORY_LOGS);
    }

    public static synchronized String getRecentLogsAsString(int maxLines) {
        if (IN_MEMORY_LOGS.isEmpty()) {
            return "Chưa có log tương tác AI nào được ghi nhận.";
        }
        int count = Math.min(maxLines, IN_MEMORY_LOGS.size());
        int start = IN_MEMORY_LOGS.size() - count;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < IN_MEMORY_LOGS.size(); i++) {
            sb.append(IN_MEMORY_LOGS.get(i)).append("\n");
        }
        return sb.toString();
    }

    public static synchronized void clearForTesting() {
        IN_MEMORY_LOGS.clear();
    }
}

