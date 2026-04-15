package li.masciul.sugardaddi.data.network;

/**
 * RetryStrategy - Defines how failed network requests should be retried
 *
 * Simple, practical retry strategies for network operations.
 * Each strategy defines the behavior when a request fails.
 *
 * USAGE EXAMPLES:
 * - NONE: One-time operations (user-initiated actions)
 * - LINEAR: Background sync, non-critical updates
 * - EXPONENTIAL: API calls, critical operations (recommended default)
 *
 * @author SugarDaddi Team
 * @version 1.0
 */
public enum RetryStrategy {

    /**
     * No retries - Fail immediately on first error
     *
     * USE CASES:
     * - User-initiated actions where immediate feedback is important
     * - Operations that should not be retried (e.g., invalid credentials)
     * - Testing/debugging when you want to see failures immediately
     */
    NONE(0, 0),

    /**
     * Linear backoff - Wait same amount of time between each retry
     * Formula: delay = baseDelay * attemptNumber
     *
     * Example with 1000ms base delay:
     * - Attempt 1: immediate
     * - Attempt 2: wait 1000ms (1s)
     * - Attempt 3: wait 1000ms (1s)
     * - Attempt 4: wait 1000ms (1s)
     *
     * USE CASES:
     * - Background synchronization
     * - Non-critical updates
     * - When server load is not a concern
     */
    LINEAR(3, 1000),

    /**
     * Exponential backoff - Wait increasingly longer between retries
     * Formula: delay = baseDelay * (2 ^ attemptNumber)
     *
     * Example with 1000ms base delay:
     * - Attempt 1: immediate
     * - Attempt 2: wait 1000ms (1s)
     * - Attempt 3: wait 2000ms (2s)
     * - Attempt 4: wait 4000ms (4s)
     *
     * USE CASES:
     * - API calls (recommended)
     * - Critical operations that must succeed
     * - When server might be overloaded
     * - Rate-limited APIs
     *
     * RECOMMENDED DEFAULT for most network operations
     */
    EXPONENTIAL(3, 1000),

    /**
     * Aggressive exponential - More retries, faster initial retry
     * Formula: delay = baseDelay * (2 ^ attemptNumber)
     *
     * Example with 500ms base delay:
     * - Attempt 1: immediate
     * - Attempt 2: wait 500ms
     * - Attempt 3: wait 1000ms (1s)
     * - Attempt 4: wait 2000ms (2s)
     * - Attempt 5: wait 4000ms (4s)
     *
     * USE CASES:
     * - Mission-critical operations
     * - Flaky network conditions
     * - When high success rate is more important than server load
     */
    AGGRESSIVE_EXPONENTIAL(5, 500);

    // ========== PROPERTIES ==========

    private final int maxRetries;
    private final long baseDelayMs;

    // ========== CONSTRUCTOR ==========

    RetryStrategy(int maxRetries, long baseDelayMs) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
    }

    // ========== PUBLIC API ==========

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    /**
     * Calculate delay before next retry attempt
     *
     * @param attemptNumber Which retry attempt (1-based: 1 = first retry, 2 = second retry, etc.)
     * @return Delay in milliseconds to wait before this retry
     */
    public long getDelayForAttempt(int attemptNumber) {
        if (attemptNumber <= 0) {
            return 0; // No delay for original attempt
        }

        switch (this) {
            case NONE:
                return 0;

            case LINEAR:
                return baseDelayMs;

            case EXPONENTIAL:
            case AGGRESSIVE_EXPONENTIAL:
                long delay = baseDelayMs * (long) Math.pow(2, attemptNumber - 1);
                return Math.min(delay, 30_000); // Cap at 30 seconds

            default:
                return baseDelayMs;
        }
    }

    public boolean shouldRetry(int attemptNumber) {
        return attemptNumber < maxRetries;
    }

    public int getTotalAttempts() {
        return maxRetries + 1; // +1 for original attempt
    }

    public String getDescription() {
        switch (this) {
            case NONE:
                return "No retries (fail fast)";
            case LINEAR:
                return String.format("Linear: %d retries, %dms delay", maxRetries, baseDelayMs);
            case EXPONENTIAL:
                return String.format("Exponential: %d retries, %dms base delay", maxRetries, baseDelayMs);
            case AGGRESSIVE_EXPONENTIAL:
                return String.format("Aggressive: %d retries, %dms base delay", maxRetries, baseDelayMs);
            default:
                return name();
        }
    }

    @Override
    public String toString() {
        return getDescription();
    }
}