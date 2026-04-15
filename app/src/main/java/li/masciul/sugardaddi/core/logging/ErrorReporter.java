package li.masciul.sugardaddi.core.logging;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.models.Error;

/**
 * ErrorReporter - Interface for reporting errors to UI/notification systems
 *
 * This is a HOOK for future notification implementations. Currently, errors
 * are just logged, but this interface allows us to easily add Toast, SnackBar,
 * AlertDialog, or custom notification systems later without changing error
 * handling code throughout the app.
 *
 * DESIGN PHILOSOPHY:
 * - Decoupling: Error creation is separate from error display
 * - Flexibility: Implementations can choose how to display (Toast, SnackBar, Dialog, etc.)
 * - Extensibility: Easy to add new notification channels (push, in-app, etc.)
 * - Testability: Can be mocked for testing
 *
 * FUTURE IMPLEMENTATIONS:
 * - ToastErrorReporter: Shows Toast messages
 * - SnackBarErrorReporter: Shows SnackBar at bottom of screen
 * - DialogErrorReporter: Shows AlertDialog for critical errors
 * - CompositeErrorReporter: Combines multiple reporters
 * - LogOnlyErrorReporter: Default implementation (just logs)
 *
 * USAGE EXAMPLE:
 * ```java
 * // In Application class or dependency injection
 * ErrorReporter reporter = new ToastErrorReporter(context);
 * ErrorLogger.setReporter(reporter);
 *
 * // Anywhere in the app
 * Error error = Error.network("Connection failed", "Timeout");
 * ErrorLogger.log(error); // Will be logged AND shown as Toast
 * ```
 *
 * @author SugarDaddi Team
 * @version 2.0 (Network Refactor)
 */
public interface ErrorReporter {

    /**
     * Report an error to the user
     *
     * Implementations decide how to display the error based on:
     * - Error type (critical vs warning)
     * - Error log level (ERROR vs WARN)
     * - User preferences (notifications enabled/disabled)
     * - Current UI context (Activity visible, app in background, etc.)
     *
     * @param error The error to report
     */
    void report(@NonNull Error error);

    /**
     * Report an error with additional context
     *
     * @param error The error to report
     * @param context Additional context (e.g., "During search", "While saving product")
     */
    void report(@NonNull Error error, @NonNull String context);

    /**
     * Check if this reporter can display errors
     * (e.g., might return false if notifications are disabled)
     */
    boolean isEnabled();

    /**
     * Enable or disable this reporter
     */
    void setEnabled(boolean enabled);

    /**
     * NO-OP implementation for when no UI reporting is needed
     * Just logs errors without showing anything to the user
     */
    class LogOnly implements ErrorReporter {
        @Override
        public void report(@NonNull Error error) {
            // Just log, don't show to user
            android.util.Log.e("SugarDaddi_Error", error.toLogString());
        }

        @Override
        public void report(@NonNull Error error, @NonNull String context) {
            android.util.Log.e("SugarDaddi_Error", "[" + context + "] " + error.toLogString());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean enabled) {
            // No-op
        }
    }
}