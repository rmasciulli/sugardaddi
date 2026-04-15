package li.masciul.sugardaddi.core.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.logging.LogLevel;

/**
 * Error - Generic error model for all application errors
 *
 * Replaces the old ApiError class with a more generic, reusable error model
 * that can be used for network errors, database errors, business logic errors,
 * and any other error type in the application.
 *
 * DESIGN PHILOSOPHY:
 * - Lightweight: Just data, no complex logic
 * - Generic: Works for API, DB, business logic, UI errors
 * - Structured: Consistent error categorization
 * - User-friendly: Messages suitable for display
 * - Debug-friendly: Technical details for logging
 * - Open-source friendly: Easy to understand and extend
 *
 * USAGE EXAMPLE:
 * ```java
 * // Network error
 * Error error = Error.network("Connection timeout", "SocketTimeoutException: Read timed out");
 *
 * // Database error
 * Error error = Error.database("Failed to insert product", "SQLException: UNIQUE constraint failed");
 *
 * // Custom error with specific type
 * Error error = new Error(
 *     ErrorType.RATE_LIMITED,
 *     "Too many requests",
 *     "Exceeded 100 req/min limit",
 *     429,
 *     LogLevel.WARN
 * );
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public class Error {

    /**
     * Error types for categorization and UI handling
     * Generic enough to be used across the entire application
     */
    public enum ErrorType {
        /** Network connectivity issues (no internet, timeout, DNS failure) */
        NETWORK,

        /** Server-side errors (500, 503, gateway errors) */
        SERVER,

        /** Database errors (query failed, constraint violation, corruption) */
        DATABASE,

        /** Request was cancelled by user or system */
        CANCELLED,

        /** No results found (successful request, empty data) */
        NO_DATA,

        /** Invalid parameters or malformed request */
        INVALID_REQUEST,

        /** API rate limiting or quota exceeded */
        RATE_LIMITED,

        /** Data parsing or serialization errors */
        PARSE_ERROR,

        /** Validation errors (user input, business rules) */
        VALIDATION,

        /** Permission or authentication errors */
        PERMISSION,

        /** Resource not found (404, missing file, etc.) */
        NOT_FOUND,

        /** Unknown or unexpected errors */
        UNKNOWN
    }

    // ========== PROPERTIES ==========

    @NonNull
    private final ErrorType type;

    @NonNull
    private final String message;              // User-friendly message

    @Nullable
    private final String technicalDetails;     // Technical details for debugging

    private final int httpCode;                // HTTP status code (if applicable, -1 otherwise)

    private final long timestamp;              // When the error occurred

    @NonNull
    private final LogLevel logLevel;           // Severity level for logging

    @Nullable
    private final String sourceId;             // Data source identifier (if applicable)

    // ========== CONSTRUCTOR ==========

    /**
     * Main constructor - use static factory methods for convenience
     */
    public Error(
            @NonNull ErrorType type,
            @NonNull String message,
            @Nullable String technicalDetails,
            int httpCode,
            @NonNull LogLevel logLevel,
            @Nullable String sourceId) {
        this.type = type;
        this.message = message;
        this.technicalDetails = technicalDetails;
        this.httpCode = httpCode;
        this.logLevel = logLevel;
        this.sourceId = sourceId;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Simplified constructor without source ID
     */
    public Error(
            @NonNull ErrorType type,
            @NonNull String message,
            @Nullable String technicalDetails,
            int httpCode,
            @NonNull LogLevel logLevel) {
        this(type, message, technicalDetails, httpCode, logLevel, null);
    }

    // ========== STATIC FACTORY METHODS ==========

    /**
     * Create network error
     */
    @NonNull
    public static Error network(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.NETWORK, message, technicalDetails, -1, LogLevel.ERROR);
    }

    /**
     * Create network error with source ID
     */
    @NonNull
    public static Error network(@NonNull String message, @Nullable String technicalDetails, @Nullable String sourceId) {
        return new Error(ErrorType.NETWORK, message, technicalDetails, -1, LogLevel.ERROR, sourceId);
    }

    /**
     * Create server error from HTTP status code
     */
    @NonNull
    public static Error server(int httpCode, @NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.SERVER, message, technicalDetails, httpCode, LogLevel.ERROR);
    }

    /**
     * Create server error with source ID
     */
    @NonNull
    public static Error server(int httpCode, @NonNull String message, @Nullable String technicalDetails, @Nullable String sourceId) {
        return new Error(ErrorType.SERVER, message, technicalDetails, httpCode, LogLevel.ERROR, sourceId);
    }

    /**
     * Create database error
     */
    @NonNull
    public static Error database(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.DATABASE, message, technicalDetails, -1, LogLevel.ERROR);
    }

    /**
     * Create cancelled error
     */
    @NonNull
    public static Error cancelled(@NonNull String message) {
        return new Error(ErrorType.CANCELLED, message, "Operation cancelled", -1, LogLevel.INFO);
    }

    /**
     * Create no data error
     */
    @NonNull
    public static Error noData(@NonNull String message) {
        return new Error(ErrorType.NO_DATA, message, "Query returned no results", 200, LogLevel.INFO);
    }

    /**
     * Create invalid request error
     */
    @NonNull
    public static Error invalidRequest(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.INVALID_REQUEST, message, technicalDetails, 400, LogLevel.WARN);
    }

    /**
     * Create rate limited error
     */
    @NonNull
    public static Error rateLimited(@NonNull String message, @Nullable String sourceId) {
        return new Error(ErrorType.RATE_LIMITED, message, "Rate limit exceeded", 429, LogLevel.WARN, sourceId);
    }

    /**
     * Create parse error
     */
    @NonNull
    public static Error parseError(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.PARSE_ERROR, message, technicalDetails, -1, LogLevel.ERROR);
    }

    /**
     * Create validation error
     */
    @NonNull
    public static Error validation(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.VALIDATION, message, technicalDetails, -1, LogLevel.WARN);
    }

    /**
     * Create permission error
     */
    @NonNull
    public static Error permission(@NonNull String message) {
        return new Error(ErrorType.PERMISSION, message, "Permission denied", 403, LogLevel.ERROR);
    }

    /**
     * Create not found error
     */
    @NonNull
    public static Error notFound(@NonNull String message) {
        return new Error(ErrorType.NOT_FOUND, message, "Resource not found", 404, LogLevel.WARN);
    }

    /**
     * Create unknown error
     */
    @NonNull
    public static Error unknown(@NonNull String message, @Nullable String technicalDetails) {
        return new Error(ErrorType.UNKNOWN, message, technicalDetails, -1, LogLevel.ERROR);
    }

    /**
     * Create error from HTTP status code (with smart type detection)
     */
    @NonNull
    public static Error fromHttpCode(int code, @Nullable String serverMessage, @Nullable String sourceId) {
        String message;
        ErrorType type;
        LogLevel logLevel;

        switch (code) {
            case 400:
            case 422:
                type = ErrorType.INVALID_REQUEST;
                message = "Invalid request. Please check your input.";
                logLevel = LogLevel.WARN;
                break;

            case 401:
            case 403:
                type = ErrorType.PERMISSION;
                message = "Access denied. Permission required.";
                logLevel = LogLevel.ERROR;
                break;

            case 404:
                type = ErrorType.NOT_FOUND;
                message = "Resource not found.";
                logLevel = LogLevel.WARN;
                break;

            case 429:
                type = ErrorType.RATE_LIMITED;
                message = "Too many requests. Please wait before trying again.";
                logLevel = LogLevel.WARN;
                break;

            case 500:
            case 502:
            case 503:
            case 504:
                type = ErrorType.SERVER;
                message = "Server error. Please try again later.";
                logLevel = LogLevel.ERROR;
                break;

            default:
                type = ErrorType.SERVER;
                message = "Unexpected server error (HTTP " + code + ").";
                logLevel = LogLevel.ERROR;
                break;
        }

        return new Error(type, message, serverMessage, code, logLevel, sourceId);
    }

    /**
     * Create error from Throwable
     */
    @NonNull
    public static Error fromThrowable(@NonNull Throwable t, @Nullable String userMessage) {
        String message = userMessage != null ? userMessage : "An unexpected error occurred.";
        String technicalDetails = t.getClass().getSimpleName() + ": " + t.getMessage();

        // Try to categorize based on exception type
        ErrorType type = ErrorType.UNKNOWN;
        if (t instanceof java.net.SocketTimeoutException ||
                t instanceof java.net.UnknownHostException ||
                t instanceof java.io.IOException) {
            type = ErrorType.NETWORK;
        } else if (t instanceof com.google.gson.JsonParseException ||
                t instanceof com.google.gson.JsonSyntaxException) {
            type = ErrorType.PARSE_ERROR;
        } else if (t instanceof android.database.SQLException) {
            type = ErrorType.DATABASE;
        }

        return new Error(type, message, technicalDetails, -1, LogLevel.ERROR);
    }

    // ========== GETTERS ==========

    @NonNull
    public ErrorType getType() {
        return type;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getTechnicalDetails() {
        return technicalDetails;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public LogLevel getLogLevel() {
        return logLevel;
    }

    @Nullable
    public String getSourceId() {
        return sourceId;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if this is a network-related error
     */
    public boolean isNetworkError() {
        return type == ErrorType.NETWORK;
    }

    /**
     * Check if this is a server-side error
     */
    public boolean isServerError() {
        return type == ErrorType.SERVER || type == ErrorType.RATE_LIMITED;
    }

    /**
     * Check if this error is potentially retryable
     * (network errors, server errors, rate limits usually are)
     */
    public boolean isRetryable() {
        return type == ErrorType.NETWORK ||
                type == ErrorType.SERVER ||
                type == ErrorType.RATE_LIMITED;
    }

    /**
     * Check if user can take action to fix this error
     */
    public boolean isUserActionable() {
        return type == ErrorType.NETWORK ||
                type == ErrorType.INVALID_REQUEST ||
                type == ErrorType.VALIDATION;
    }

    /**
     * Get suggested action message for UI
     */
    @NonNull
    public String getSuggestedAction() {
        switch (type) {
            case NETWORK:
                return "Check your internet connection and try again.";
            case SERVER:
                return "Please try again in a few moments.";
            case RATE_LIMITED:
                return "Please wait before trying again.";
            case NO_DATA:
                return "Try different search terms.";
            case INVALID_REQUEST:
            case VALIDATION:
                return "Please check your input and try again.";
            case PERMISSION:
                return "You don't have permission to access this resource.";
            case NOT_FOUND:
                return "The requested resource was not found.";
            case DATABASE:
                return "Database operation failed. Please contact support.";
            case CANCELLED:
                return ""; // No action needed
            default:
                return "Please try again or contact support.";
        }
    }

    /**
     * Check if this error should be shown to the user
     * (vs. just logged for debugging)
     */
    public boolean shouldShowToUser() {
        return type != ErrorType.CANCELLED &&
                logLevel.atLeast(LogLevel.WARN);
    }

    // ========== FORMATTING ==========

    /**
     * Get formatted log message for debugging
     */
    @NonNull
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(logLevel.getName()).append("] ");
        sb.append(type.name()).append(": ");
        sb.append(message);

        if (sourceId != null) {
            sb.append(" (source: ").append(sourceId).append(")");
        }

        if (httpCode > 0) {
            sb.append(" [HTTP ").append(httpCode).append("]");
        }

        if (technicalDetails != null) {
            sb.append(" - ").append(technicalDetails);
        }

        return sb.toString();
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("Error{type=%s, message='%s', httpCode=%d, logLevel=%s, source=%s}",
                type, message, httpCode, logLevel, sourceId != null ? sourceId : "none");
    }

    /**
     * Get user-friendly display text (message + suggested action)
     */
    @NonNull
    public String getDisplayText() {
        String action = getSuggestedAction();
        if (action.isEmpty()) {
            return message;
        }
        return message + "\n" + action;
    }
}