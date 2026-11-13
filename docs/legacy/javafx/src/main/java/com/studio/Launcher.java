package com.studio;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Non-modular entry point for JavaFX application execution from classpath.
 * <p>
 * Bypasses the JVM LauncherHelper check: "Error: JavaFX runtime components are missing,
 * and are required to run this application", which is triggered when the main class
 * directly extends {@link javafx.application.Application} while JavaFX dependencies
 * reside on the classpath rather than the module path.
 */
public class Launcher {

    public static void main(String[] args) {
        // Set up an uncaught exception handler so crashes are never completely silent
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logFatalError("Uncaught exception on thread: " + thread.getName(), throwable);
        });

        try {
            App.main(args);
        } catch (Throwable t) {
            logFatalError("Fatal error during application startup", t);
            System.exit(1);
        }
    }

    private static void logFatalError(String context, Throwable t) {
        System.err.println("[FATAL] " + context);
        if (t != null) {
            t.printStackTrace(System.err);
        }
        try {
            Path logDir = Paths.get("data", "logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            Path logFile = logDir.resolve("startup-error.log");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            StringWriter sw = new StringWriter();
            if (t != null) {
                t.printStackTrace(new PrintWriter(sw));
            }
            String entry = String.format("[%s] %s%n%s%n%n", timestamp, context, sw);
            Files.writeString(logFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
