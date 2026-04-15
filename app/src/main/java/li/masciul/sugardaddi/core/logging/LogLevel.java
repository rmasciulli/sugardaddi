package li.masciul.sugardaddi.core.logging;

/**
 * LogLevel - Structured logging levels for error and event tracking
 *
 * Provides a hierarchy of log severity levels for consistent logging
 * across the entire application. Each level represents a different
 * severity and determines how/when messages are logged.
 *
 * USAGE:
 * - VERBOSE: Detailed trace information (e.g., "Entering method X")
 * - DEBUG: Debugging information (e.g., "Retry attempt 2/3")
 * - INFO: General informational messages (e.g., "Search completed")
 * - WARN: Warning messages (e.g., "Slow network detected")
 * - ERROR: Error events that might still allow the app to continue (e.g., "API call failed")
 * - CRITICAL: Critical errors requiring immediate attention (e.g., "Data corruption detected")
 * - NONE: Disable all logging
 *
 * LOG FILTERING:
 * Each level includes all higher severity levels.
 * Example: LogLevel.WARN includes WARN, ERROR, and CRITICAL
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public enum LogLevel {
    /** No logging at all */
    NONE(0, "NONE"),

    /** Critical errors requiring immediate attention */
    CRITICAL(1, "CRITICAL"),

    /** Error events that might still allow the app to continue */
    ERROR(2, "ERROR"),

    /** Warning messages for potentially harmful situations */
    WARN(3, "WARN"),

    /** Informational messages highlighting progress */
    INFO(4, "INFO"),

    /** Detailed debugging information */
    DEBUG(5, "DEBUG"),

    /** Most detailed trace information */
    VERBOSE(6, "VERBOSE");

    private final int level;
    private final String name;

    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    /**
     * Check if this level should log messages of the given severity
     *
     * Example:
     * LogLevel.WARN.shouldLog(LogLevel.ERROR) → true (ERROR is higher severity)
     * LogLevel.WARN.shouldLog(LogLevel.DEBUG) → false (DEBUG is lower severity)
     */
    public boolean shouldLog(LogLevel messageLevel) {
        return messageLevel.level <= this.level;
    }

    /**
     * Check if this level is at least as severe as the given level
     */
    public boolean atLeast(LogLevel other) {
        return this.level <= other.level;
    }

    /**
     * Check if this level is more severe than the given level
     */
    public boolean moreSevereThan(LogLevel other) {
        return this.level < other.level;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}