package li.masciul.sugardaddi.core.logging;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.Error;
import li.masciul.sugardaddi.data.network.ApiConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ErrorLogger - Centralized error logging and tracking utility
 *
 * Provides a single point for all error logging in the application.
 * Handles structured logging, error history, and optional UI reporting.
 *
 * FEATURES:
 * - Structured logging with log levels
 * - Error history tracking (for debugging and ErrorLog activity)
 * - Optional UI reporting via ErrorReporter
 * - Thread-safe error collection
 * - Automatic log filtering based on log level
 * - Statistics tracking (error counts by type)
 *
 * USAGE:
 * ```java
 * // Simple logging
 * ErrorLogger.log(error);
 *
 * // Logging with context
 * ErrorLogger.log(error, "During product search");
 *
 * // Quick log from exception
 * ErrorLogger.logException(exception, "Failed to parse JSON");
 *
 * // Get error history (for ErrorLog activity)
 * List<Error> recentErrors = ErrorLogger.getRecentErrors(20);
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public final class ErrorLogger {

    private static final String TAG = "SugarDaddi_Error";

    // ========== CONFIGURATION ==========

    /** Maximum errors to keep in history */
    private static final int MAX_ERROR_HISTORY = 100;

    /** Current log level (can be changed dynamically) */
    private static LogLevel currentLogLevel = LogLevel.DEBUG;

    /** Error reporter for UI notifications (optional) */
    private static ErrorReporter errorReporter = new ErrorReporter.LogOnly();

    // ========== ERROR TRACKING ==========

    /** Thread-safe error history for debugging */
    private static final List<Error> errorHistory = new CopyOnWriteArrayList<>();

    /** Statistics */
    private static int totalErrors = 0;
    private static int networkErrors = 0;
    private static int serverErrors = 0;
    private static int databaseErrors = 0;

    // ========== CONSTRUCTOR ==========

    private ErrorLogger() {
        throw new UnsupportedOperationException("ErrorLogger is a utility class");
    }

    // ========== MAIN LOGGING METHODS ==========

    /**
     * Log an error
     */
    public static void log(@NonNull Error error) {
        log(error, null);
    }

    /**
     * Log an error with context
     *
     * @param error The error to log
     * @param context Additional context (e.g., "During barcode scan", "In search results")
     */
    public static void log(@NonNull Error error, @Nullable String context) {
        // Check if we should log this error based on current log level
        if (!shouldLog(error.getLogLevel())) {
            return;
        }

        // Add to history
        addToHistory(error);

        // Update statistics
        updateStatistics(error);

        // Log to Android logcat
        logToLogcat(error, context);

        // Report to UI (if reporter is configured and error should be shown)
        if (error.shouldShowToUser() && errorReporter.isEnabled()) {
            if (context != null) {
                errorReporter.report(error, context);
            } else {
                errorReporter.report(error);
            }
        }
    }

    /**
     * Log an exception as an error
     */
    public static void logException(@NonNull Throwable throwable, @Nullable String userMessage) {
        Error error = Error.fromThrowable(throwable, userMessage);
        log(error, "Exception occurred");
    }

    /**
     * Log an exception with context
     */
    public static void logException(@NonNull Throwable throwable, @Nullable String userMessage, @NonNull String context) {
        Error error = Error.fromThrowable(throwable, userMessage);
        log(error, context);
    }

    // ========== LOG LEVEL MANAGEMENT ==========

    /**
     * Set the current log level
     * Only errors at or above this level will be logged
     */
    public static void setLogLevel(@NonNull LogLevel level) {
        currentLogLevel = level;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.i(TAG, "Log level changed to: " + level.getName());
        }
    }

    /**
     * Get the current log level
     */
    @NonNull
    public static LogLevel getLogLevel() {
        return currentLogLevel;
    }

    /**
     * Check if a given log level should be logged
     */
    private static boolean shouldLog(@NonNull LogLevel level) {
        return currentLogLevel.shouldLog(level);
    }

    // ========== ERROR REPORTER MANAGEMENT ==========

    /**
     * Set the error reporter for UI notifications
     * Use ErrorReporter.LogOnly to disable UI reporting
     */
    public static void setErrorReporter(@NonNull ErrorReporter reporter) {
        errorReporter = reporter;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.i(TAG, "Error reporter set: " + reporter.getClass().getSimpleName());
        }
    }

    /**
     * Get the current error reporter
     */
    @NonNull
    public static ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    // ========== ERROR HISTORY ==========

    /**
     * Add error to history (with limit)
     */
    private static void addToHistory(@NonNull Error error) {
        errorHistory.add(0, error); // Add to beginning

        // Remove oldest if exceeding limit
        if (errorHistory.size() > MAX_ERROR_HISTORY) {
            errorHistory.remove(errorHistory.size() - 1);
        }
    }

    /**
     * Get recent errors (for debugging or ErrorLog activity)
     *
     * @param count Maximum number of errors to return
     * @return Immutable list of recent errors (most recent first)
     */
    @NonNull
    public static List<Error> getRecentErrors(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        int actualCount = Math.min(count, errorHistory.size());
        return new ArrayList<>(errorHistory.subList(0, actualCount));
    }

    /**
     * Get all error history
     */
    @NonNull
    public static List<Error> getAllErrors() {
        return new ArrayList<>(errorHistory);
    }

    /**
     * Clear error history
     */
    public static void clearHistory() {
        errorHistory.clear();
        if (ApiConfig.DEBUG_LOGGING) {
            Log.i(TAG, "Error history cleared");
        }
    }

    /**
     * Get error count
     */
    public static int getErrorCount() {
        return errorHistory.size();
    }

    // ========== STATISTICS ==========

    /**
     * Update error statistics
     */
    private static void updateStatistics(@NonNull Error error) {
        totalErrors++;

        switch (error.getType()) {
            case NETWORK:
                networkErrors++;
                break;
            case SERVER:
            case RATE_LIMITED:
                serverErrors++;
                break;
            case DATABASE:
                databaseErrors++;
                break;
        }
    }

    /**
     * Get error statistics summary
     */
    @NonNull
    public static String getStatistics() {
        return String.format(
                "Error Statistics:\n" +
                        "Total: %d\n" +
                        "Network: %d\n" +
                        "Server: %d\n" +
                        "Database: %d\n" +
                        "History Size: %d",
                totalErrors,
                networkErrors,
                serverErrors,
                databaseErrors,
                errorHistory.size()
        );
    }

    /**
     * Reset all statistics
     */
    public static void resetStatistics() {
        totalErrors = 0;
        networkErrors = 0;
        serverErrors = 0;
        databaseErrors = 0;
        if (ApiConfig.DEBUG_LOGGING) {
            Log.i(TAG, "Error statistics reset");
        }
    }

    // ========== INTERNAL LOGGING ==========

    /**
     * Log to Android logcat with appropriate level
     */
    private static void logToLogcat(@NonNull Error error, @Nullable String context) {
        String logMessage = context != null
                ? "[" + context + "] " + error.toLogString()
                : error.toLogString();

        switch (error.getLogLevel()) {
            case CRITICAL:
                Log.wtf(TAG, logMessage);
                break;
            case ERROR:
                Log.e(TAG, logMessage);
                break;
            case WARN:
                Log.w(TAG, logMessage);
                break;
            case INFO:
                Log.i(TAG, logMessage);
                break;
            case DEBUG:
                Log.d(TAG, logMessage);
                break;
            case VERBOSE:
                Log.v(TAG, logMessage);
                break;
        }
    }

    // ========== FILTERING ==========

    /**
     * Get errors by type
     */
    @NonNull
    public static List<Error> getErrorsByType(@NonNull Error.ErrorType type) {
        List<Error> filtered = new ArrayList<>();
        for (Error error : errorHistory) {
            if (error.getType() == type) {
                filtered.add(error);
            }
        }
        return filtered;
    }

    /**
     * Get errors by source
     */
    @NonNull
    public static List<Error> getErrorsBySource(@NonNull String sourceId) {
        List<Error> filtered = new ArrayList<>();
        for (Error error : errorHistory) {
            if (sourceId.equals(error.getSourceId())) {
                filtered.add(error);
            }
        }
        return filtered;
    }

    /**
     * Get errors by log level (at or above)
     */
    @NonNull
    public static List<Error> getErrorsByLogLevel(@NonNull LogLevel level) {
        List<Error> filtered = new ArrayList<>();
        for (Error error : errorHistory) {
            if (error.getLogLevel().atLeast(level)) {
                filtered.add(error);
            }
        }
        return filtered;
    }
}