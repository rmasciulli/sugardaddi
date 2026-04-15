package li.masciul.sugardaddi.data.sources.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.Error;

/**
 * DataSourceException - Exception specific to data source operations
 *
 * UPDATED v2.0 (Network Refactor):
 * - Uses Error instead of ApiError
 * - Simplified error type enum (aligns with Error.ErrorType)
 * - Better error conversion with source tracking
 * - User-friendly messages
 *
 * USAGE:
 * ```java
 * // Throw exception
 * throw new DataSourceException(
 *     "OPENFOODFACTS",
 *     ErrorType.NETWORK_ERROR,
 *     "Connection timeout",
 *     ioException
 * );
 *
 * // Convert to Error for UI
 * Error error = exception.toError();
 * ErrorLogger.log(error, "During product search");
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public class DataSourceException extends Exception {

    private final String sourceId;
    private final ErrorType errorType;
    private final int httpCode;

    /**
     * Error types for data source operations
     * Simplified to align with Error.ErrorType
     */
    public enum ErrorType {
        /** Network connectivity issues */
        NETWORK_ERROR,

        /** Data parsing or format errors */
        PARSING_ERROR,

        /** Data source not available or disabled */
        NOT_AVAILABLE,

        /** Invalid request parameters */
        INVALID_REQUEST,

        /** API quota exceeded or rate limited */
        QUOTA_EXCEEDED,

        /** Database operation failed */
        DATABASE_ERROR,

        /** Resource not found */
        NOT_FOUND,

        /** Unknown error */
        UNKNOWN
    }

    // ========== CONSTRUCTORS ==========

    /**
     * Create exception with error type and message
     */
    public DataSourceException(@NonNull String sourceId, @NonNull ErrorType errorType,
                               @NonNull String message) {
        super(message);
        this.sourceId = sourceId;
        this.errorType = errorType;
        this.httpCode = -1;
    }

    /**
     * Create exception with error type, message, and cause
     */
    public DataSourceException(@NonNull String sourceId, @NonNull ErrorType errorType,
                               @NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.sourceId = sourceId;
        this.errorType = errorType;
        this.httpCode = -1;
    }

    /**
     * Create exception with HTTP code (for API errors)
     */
    public DataSourceException(@NonNull String sourceId, @NonNull ErrorType errorType,
                               @NonNull String message, int httpCode) {
        super(message);
        this.sourceId = sourceId;
        this.errorType = errorType;
        this.httpCode = httpCode;
    }

    /**
     * Create exception with HTTP code and cause
     */
    public DataSourceException(@NonNull String sourceId, @NonNull ErrorType errorType,
                               @NonNull String message, int httpCode, @Nullable Throwable cause) {
        super(message, cause);
        this.sourceId = sourceId;
        this.errorType = errorType;
        this.httpCode = httpCode;
    }

    // ========== GETTERS ==========

    @NonNull
    public String getSourceId() {
        return sourceId;
    }

    @NonNull
    public ErrorType getErrorType() {
        return errorType;
    }

    public int getHttpCode() {
        return httpCode;
    }

    // ========== ERROR CONVERSION ==========

    /**
     * Convert to Error for UI layer compatibility
     * Automatically maps DataSourceException.ErrorType → Error.ErrorType
     */
    @NonNull
    public Error toError() {
        String message = getUserFriendlyMessage();
        String technicalDetails = getTechnicalDetails();

        switch (errorType) {
            case NETWORK_ERROR:
                return Error.network(message, technicalDetails, sourceId);

            case PARSING_ERROR:
                return Error.parseError(message, technicalDetails);

            case NOT_AVAILABLE:
                return new Error(
                        Error.ErrorType.SERVER,
                        message,
                        technicalDetails,
                        -1,
                        li.masciul.sugardaddi.core.logging.LogLevel.WARN,
                        sourceId
                );

            case INVALID_REQUEST:
                return Error.invalidRequest(message, technicalDetails);

            case QUOTA_EXCEEDED:
                return Error.rateLimited(message, sourceId);

            case DATABASE_ERROR:
                return Error.database(message, technicalDetails);

            case NOT_FOUND:
                return Error.notFound(message);

            case UNKNOWN:
            default:
                return Error.unknown(message, technicalDetails);
        }
    }

    // ========== USER-FRIENDLY MESSAGES ==========

    /**
     * Get user-friendly error message based on error type
     */
    @NonNull
    private String getUserFriendlyMessage() {
        switch (errorType) {
            case NETWORK_ERROR:
                return "Network connection problem. Please check your internet connection.";

            case PARSING_ERROR:
                return "Unable to process server response. Please try again.";

            case NOT_AVAILABLE:
                return "Data source (" + sourceId + ") is currently unavailable.";

            case INVALID_REQUEST:
                return "Invalid search request. Please try different search terms.";

            case QUOTA_EXCEEDED:
                return "Too many requests. Please wait before searching again.";

            case DATABASE_ERROR:
                return "Database operation failed. Please try again.";

            case NOT_FOUND:
                return "Resource not found.";

            case UNKNOWN:
            default:
                return "An unexpected error occurred. Please try again.";
        }
    }

    /**
     * Get technical details for debugging
     */
    @NonNull
    private String getTechnicalDetails() {
        StringBuilder details = new StringBuilder();
        details.append("[").append(sourceId).append("] ");
        details.append(errorType.name()).append(": ");
        details.append(getMessage());

        if (httpCode > 0) {
            details.append(" (HTTP ").append(httpCode).append(")");
        }

        if (getCause() != null) {
            details.append(" | Cause: ")
                    .append(getCause().getClass().getSimpleName())
                    .append(": ")
                    .append(getCause().getMessage());
        }

        return details.toString();
    }

    // ========== STATIC FACTORY METHODS ==========

    /**
     * Create network error exception
     */
    @NonNull
    public static DataSourceException networkError(@NonNull String sourceId,
                                                   @NonNull String message,
                                                   @Nullable Throwable cause) {
        return new DataSourceException(sourceId, ErrorType.NETWORK_ERROR, message, cause);
    }

    /**
     * Create parsing error exception
     */
    @NonNull
    public static DataSourceException parsingError(@NonNull String sourceId,
                                                   @NonNull String message,
                                                   @Nullable Throwable cause) {
        return new DataSourceException(sourceId, ErrorType.PARSING_ERROR, message, cause);
    }

    /**
     * Create not available exception
     */
    @NonNull
    public static DataSourceException notAvailable(@NonNull String sourceId,
                                                   @NonNull String reason) {
        return new DataSourceException(sourceId, ErrorType.NOT_AVAILABLE, reason);
    }

    /**
     * Create invalid request exception
     */
    @NonNull
    public static DataSourceException invalidRequest(@NonNull String sourceId,
                                                     @NonNull String message) {
        return new DataSourceException(sourceId, ErrorType.INVALID_REQUEST, message);
    }

    /**
     * Create quota exceeded exception
     */
    @NonNull
    public static DataSourceException quotaExceeded(@NonNull String sourceId,
                                                    @NonNull String message) {
        return new DataSourceException(sourceId, ErrorType.QUOTA_EXCEEDED, message, 429);
    }

    /**
     * Create database error exception
     */
    @NonNull
    public static DataSourceException databaseError(@NonNull String sourceId,
                                                    @NonNull String message,
                                                    @Nullable Throwable cause) {
        return new DataSourceException(sourceId, ErrorType.DATABASE_ERROR, message, cause);
    }

    /**
     * Create not found exception
     */
    @NonNull
    public static DataSourceException notFound(@NonNull String sourceId,
                                               @NonNull String message) {
        return new DataSourceException(sourceId, ErrorType.NOT_FOUND, message, 404);
    }

    /**
     * Create unknown error exception
     */
    @NonNull
    public static DataSourceException unknown(@NonNull String sourceId,
                                              @NonNull String message,
                                              @Nullable Throwable cause) {
        return new DataSourceException(sourceId, ErrorType.UNKNOWN, message, cause);
    }

    /**
     * Create exception from HTTP status code
     */
    @NonNull
    public static DataSourceException fromHttpCode(@NonNull String sourceId,
                                                   int httpCode,
                                                   @Nullable String message) {
        ErrorType errorType;
        String defaultMessage = message != null ? message : "HTTP error " + httpCode;

        if (httpCode == 429) {
            errorType = ErrorType.QUOTA_EXCEEDED;
        } else if (httpCode == 404) {
            errorType = ErrorType.NOT_FOUND;
        } else if (httpCode >= 400 && httpCode < 500) {
            errorType = ErrorType.INVALID_REQUEST;
        } else if (httpCode >= 500) {
            errorType = ErrorType.NETWORK_ERROR; // Treat server errors as network issues
        } else {
            errorType = ErrorType.UNKNOWN;
        }

        return new DataSourceException(sourceId, errorType, defaultMessage, httpCode);
    }

    // ========== UTILITY METHODS ==========

    /**
     * Check if this error is retryable
     */
    public boolean isRetryable() {
        return errorType == ErrorType.NETWORK_ERROR ||
                errorType == ErrorType.QUOTA_EXCEEDED ||
                errorType == ErrorType.UNKNOWN;
    }

    /**
     * Get suggested retry delay in seconds
     */
    public int getRetryDelaySeconds() {
        switch (errorType) {
            case NETWORK_ERROR:
                return 2;
            case QUOTA_EXCEEDED:
                return 30;
            case UNKNOWN:
                return 5;
            default:
                return 0;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("DataSourceException{source=%s, type=%s, message='%s', httpCode=%d}",
                sourceId, errorType, getMessage(), httpCode);
    }
}