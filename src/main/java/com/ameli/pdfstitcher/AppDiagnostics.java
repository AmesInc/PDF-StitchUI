package com.ameli.pdfstitcher;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.prefs.Preferences;

final class AppDiagnostics {
    private static final String PREF_DEVELOPER_LOGGING = "developerLoggingEnabled";
    private static final Logger LOGGER = Logger.getLogger("com.ameli.pdfstitcher");
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(PdfStitcherApp.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile FileHandler fileHandler;

    private AppDiagnostics() {
    }

    static void initialize() {
        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(true);
        setDeveloperLoggingEnabled(isDeveloperLoggingEnabled());
        installUncaughtExceptionHandler();
        info("Diagnostics initialized. Developer logging=" + isDeveloperLoggingEnabled());
    }

    static boolean isDeveloperLoggingEnabled() {
        return PREFERENCES.getBoolean(PREF_DEVELOPER_LOGGING, false);
    }

    static void setDeveloperLoggingEnabled(boolean enabled) {
        PREFERENCES.putBoolean(PREF_DEVELOPER_LOGGING, enabled);
        if (enabled) {
            enableFileLogging();
            info("Developer logging enabled.");
        } else {
            info("Developer logging disabled.");
            disableFileLogging();
        }
    }

    static Path getLogDirectory() {
        return Path.of(System.getProperty("user.home"), ".pdf-stitchui", "logs");
    }

    static void openLogDirectory() throws IOException {
        Path directory = Files.createDirectories(getLogDirectory());
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory.toFile());
        } else {
            throw new IOException("Desktop folder opening is not supported on this device.");
        }
    }

    static void info(String message) {
        LOGGER.info(prefix(message));
    }

    static void warning(String message) {
        LOGGER.warning(prefix(message));
    }

    static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, prefix(message), throwable);
    }

    private static String prefix(String message) {
        return "[" + TIMESTAMP.format(LocalDateTime.now()) + "] " + message;
    }

    private static synchronized void enableFileLogging() {
        if (fileHandler != null) {
            return;
        }
        try {
            Path logDirectory = Files.createDirectories(getLogDirectory());
            FileHandler handler = new FileHandler(logDirectory.resolve("pdf-stitchui-%g.log").toString(), 1_000_000, 5, true);
            handler.setLevel(Level.ALL);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            fileHandler = handler;
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, prefix("Unable to enable developer file logging."), exception);
        }
    }

    private static synchronized void disableFileLogging() {
        if (fileHandler == null) {
            return;
        }
        Handler handler = fileHandler;
        LOGGER.removeHandler(handler);
        handler.close();
        fileHandler = null;
    }

    private static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                error("Uncaught exception on thread '" + thread.getName() + "'.", throwable));
    }
}
