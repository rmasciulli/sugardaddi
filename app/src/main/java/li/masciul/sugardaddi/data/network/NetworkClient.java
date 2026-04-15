package li.masciul.sugardaddi.data.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * NetworkClient - Generic HTTP client builder and request coordinator
 *
 * Creates and manages OkHttpClient instances for data sources.
 * Handles retry logic, interceptors, timeouts, and error mapping.
 *
 * This is the NEW generic network layer that works with NetworkConfig.
 * The old NetworkManager (OFF-specific) will be refactored to use this.
 *
 * FEATURES:
 * - Creates configured OkHttpClient from NetworkConfig
 * - Automatic retry with exponential backoff
 * - Request/response logging (debug mode)
 * - Error mapping and handling
 * - Connection pooling
 * - Header injection
 *
 * USAGE EXAMPLE:
 * <pre>
 * NetworkConfig config = new OpenFoodFactsConfig();
 * OkHttpClient client = NetworkClient.createHttpClient(config, context);
 * Retrofit retrofit = NetworkClient.createRetrofit(config, client);
 * OFFApiV1 api = retrofit.create(OFFApiV1.class);
 * </pre>
 *
 * @author SugarDaddi Team
 * @version 1.0
 */
public class NetworkClient {

    private static final String TAG = ApiConfig.NETWORK_LOG_TAG;

    // ========== HTTP CLIENT CREATION ==========

    @NonNull
    public static OkHttpClient createHttpClient(@NonNull NetworkConfig config, @NonNull Context context) {
        config.validate();

        if (config.isDebugLoggingEnabled()) {
            Log.d(TAG, "Creating HTTP client for " + config.getSourceId() + ": " + config.getSummary());
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Apply timeouts
        builder.connectTimeout(config.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeoutSeconds(), TimeUnit.SECONDS);

        // Enable retry on connection failure
        builder.retryOnConnectionFailure(true);

        // Add retry interceptor
        if (config.getRetryStrategy() != RetryStrategy.NONE) {
            builder.addInterceptor(new RetryInterceptor(config));
        }

        // Add header interceptor
        builder.addInterceptor(new HeaderInterceptor(config));

        // Add custom interceptors
        for (Interceptor interceptor : config.getInterceptors()) {
            builder.addInterceptor(interceptor);
        }

        // Add logging in debug mode
        if (config.isDebugLoggingEnabled()) {
            DetailedHttpLoggingInterceptor detailedLogging =
                    new DetailedHttpLoggingInterceptor(config.getSourceId(), true);
            builder.addInterceptor(detailedLogging);
        }

        return builder.build();
    }

    @NonNull
    public static Retrofit createRetrofit(@NonNull NetworkConfig config, @NonNull OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(config.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @NonNull
    public static Retrofit createRetrofit(@NonNull NetworkConfig config, @NonNull Context context) {
        OkHttpClient client = createHttpClient(config, context);
        return createRetrofit(config, client);
    }

    @NonNull
    public static <T> T createApiService(
            @NonNull NetworkConfig config,
            @NonNull Context context,
            @NonNull Class<T> serviceClass) {
        Retrofit retrofit = createRetrofit(config, context);
        return retrofit.create(serviceClass);
    }

    // ========== INTERCEPTORS ==========

    private static class HeaderInterceptor implements Interceptor {
        private final NetworkConfig config;

        HeaderInterceptor(NetworkConfig config) {
            this.config = config;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request original = chain.request();
            Request.Builder requestBuilder = original.newBuilder();

            Map<String, String> headers = config.getHeaders();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }

            Request request = requestBuilder.build();
            return chain.proceed(request);
        }
    }

    private static class RetryInterceptor implements Interceptor {
        private final NetworkConfig config;

        RetryInterceptor(NetworkConfig config) {
            this.config = config;
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();
            RetryStrategy strategy = config.getRetryStrategy();

            Response response = null;
            IOException lastException = null;

            int attemptNumber = 0;
            int maxAttempts = strategy.getTotalAttempts();

            while (attemptNumber < maxAttempts) {
                try {
                    if (response != null) {
                        response.close();
                    }

                    response = chain.proceed(request);

                    if (response.isSuccessful()) {
                        if (attemptNumber > 0 && config.isDebugLoggingEnabled()) {
                            Log.d(TAG, String.format("[%s] Request succeeded on attempt %d/%d",
                                    config.getSourceId(), attemptNumber + 1, maxAttempts));
                        }
                        return response;
                    }

                    int statusCode = response.code();
                    boolean shouldRetry = config.shouldRetryForError(statusCode, response.message());

                    if (!shouldRetry || attemptNumber >= maxAttempts - 1) {
                        if (config.isDebugLoggingEnabled()) {
                            Log.w(TAG, String.format("[%s] Request failed with status %d, no more retries",
                                    config.getSourceId(), statusCode));
                        }
                        return response;
                    }

                    if (config.isDebugLoggingEnabled()) {
                        Log.w(TAG, String.format("[%s] Request failed with status %d, retrying (attempt %d/%d)",
                                config.getSourceId(), statusCode, attemptNumber + 1, maxAttempts));
                    }

                } catch (IOException e) {
                    lastException = e;

                    boolean shouldRetry = config.shouldRetryForError(-1, e.getMessage());

                    if (!shouldRetry || attemptNumber >= maxAttempts - 1) {
                        if (config.isDebugLoggingEnabled()) {
                            Log.e(TAG, String.format("[%s] Network error, no more retries: %s",
                                    config.getSourceId(), e.getMessage()));
                        }
                        throw e;
                    }

                    if (config.isDebugLoggingEnabled()) {
                        Log.w(TAG, String.format("[%s] Network error, retrying (attempt %d/%d): %s",
                                config.getSourceId(), attemptNumber + 1, maxAttempts, e.getMessage()));
                    }
                }

                if (attemptNumber > 0) {
                    long delay = strategy.getDelayForAttempt(attemptNumber);
                    if (delay > 0) {
                        try {
                            if (config.isDebugLoggingEnabled()) {
                                Log.d(TAG, String.format("[%s] Waiting %dms before retry",
                                        config.getSourceId(), delay));
                            }
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (lastException != null) {
                                throw lastException;
                            }
                            throw new IOException("Request interrupted", ie);
                        }
                    }
                }

                attemptNumber++;
            }

            if (response != null) {
                return response;
            }
            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("Request failed after " + maxAttempts + " attempts");
        }
    }

    // ========== ERROR HANDLING ==========

    @NonNull
    public static String getErrorMessage(int statusCode, @Nullable String defaultMessage) {
        switch (statusCode) {
            case 400: return "Invalid request - please check your input";
            case 401: return "Authentication required";
            case 403: return "Access denied";
            case 404: return "Not found";
            case 408: return "Request timeout - please try again";
            case 429: return "Too many requests - please wait a moment";
            case 500: return "Server error - please try again later";
            case 502:
            case 503: return "Service temporarily unavailable";
            case 504: return "Gateway timeout - please try again";
            default:
                if (statusCode >= 500) {
                    return "Server error - please try again later";
                } else if (statusCode >= 400) {
                    return "Request failed: " + (defaultMessage != null ? defaultMessage : "Unknown error");
                }
                return defaultMessage != null ? defaultMessage : "Network error";
        }
    }

    public static boolean isRetryableError(int statusCode) {
        if (statusCode == -1) return true; // Network errors
        if (statusCode >= 500 && statusCode < 600) return true; // Server errors
        if (statusCode == 429) return true; // Rate limiting
        if (statusCode == 408 || statusCode == 504) return true; // Timeout
        return false; // Client errors (4xx) are not retryable
    }

    public static boolean isNetworkError(@Nullable String error) {
        if (error == null) return false;

        String lowerError = error.toLowerCase();
        return lowerError.contains("network") ||
                lowerError.contains("connection") ||
                lowerError.contains("timeout") ||
                lowerError.contains("unable to resolve host") ||
                lowerError.contains("no route to host");
    }
}