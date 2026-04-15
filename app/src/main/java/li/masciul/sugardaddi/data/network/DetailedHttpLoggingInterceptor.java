package li.masciul.sugardaddi.data.network;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * DetailedHttpLoggingInterceptor - Comprehensive HTTP request/response logging
 *
 * ✅ NEW: DETAILED HTTP LOGGING
 * - Logs ALL HTTP requests before they're sent
 * - Logs ALL HTTP responses after they're received
 * - Includes timing information
 * - Shows request/response headers
 * - Shows request/response bodies (with size limits)
 * - Color-coded log levels for easy reading
 * - Respects debug flags
 *
 * LOG FORMAT:
 * ```
 * ╔═══════════════════════════════════════════════
 * ║ → HTTP REQUEST
 * ║ POST https://world.openfoodfacts.org/api/v2/search
 * ║ Headers:
 * ║   User-Agent: SugarDaddi/1.0
 * ║   Content-Type: application/json
 * ║ Body: {"query":"chocolate","page_size":10}
 * ╚═══════════════════════════════════════════════
 *
 * ╔═══════════════════════════════════════════════
 * ║ ← HTTP RESPONSE
 * ║ 200 OK (328ms)
 * ║ https://world.openfoodfacts.org/api/v2/search
 * ║ Headers:
 * ║   Content-Type: application/json
 * ║   Content-Length: 15234
 * ║ Body: {"count":157,"page":1,"products":[...]}
 * ║ Body size: 15234 bytes
 * ╚═══════════════════════════════════════════════
 * ```
 *
 * USAGE:
 * ```java
 * OkHttpClient.Builder builder = new OkHttpClient.Builder();
 * builder.addInterceptor(new DetailedHttpLoggingInterceptor("OpenFoodFacts", true));
 * ```
 */
public class DetailedHttpLoggingInterceptor implements Interceptor {

    private static final String TAG = ApiConfig.NETWORK_LOG_TAG;

    // Configuration
    private final String sourceId;
    private final boolean enabled;
    private static final int MAX_BODY_LOG_SIZE = 2000; // Max characters to log
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    // Log prefixes for visual clarity
    private static final String REQUEST_PREFIX = "→ REQUEST";
    private static final String RESPONSE_PREFIX = "← RESPONSE";
    private static final String SEPARATOR = "════════════════════════════════════════";

    public DetailedHttpLoggingInterceptor(@NonNull String sourceId, boolean enabled) {
        this.sourceId = sourceId;
        this.enabled = enabled;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        if (!enabled) {
            return chain.proceed(request);
        }

        // Log request
        logRequest(request);

        // Execute request and measure time
        long startTime = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            logRequestFailure(request, e, elapsedMs);
            throw e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        // Log response
        response = logResponse(response, elapsedMs);

        return response;
    }

    /**
     * Log HTTP request before sending
     */
    private void logRequest(@NonNull Request request) {
        try {
            Log.d(TAG, "╔" + SEPARATOR);
            Log.d(TAG, "║ " + REQUEST_PREFIX + " [" + sourceId + "]");
            Log.d(TAG, "║ " + request.method() + " " + request.url());

            // Log headers
            Headers headers = request.headers();
            if (headers.size() > 0) {
                Log.d(TAG, "║ Headers:");
                for (int i = 0; i < headers.size(); i++) {
                    String name = headers.name(i);
                    String value = headers.value(i);
                    // Mask sensitive headers
                    if (name.equalsIgnoreCase("Authorization") ||
                            name.equalsIgnoreCase("Cookie")) {
                        value = "***MASKED***";
                    }
                    Log.d(TAG, "║   " + name + ": " + value);
                }
            }

            // Log request body if present
            RequestBody body = request.body();
            if (body != null) {
                String bodyString = getRequestBodyString(body);
                if (bodyString != null && !bodyString.isEmpty()) {
                    Log.d(TAG, "║ Body: " + truncate(bodyString));
                }
            }

            Log.d(TAG, "╚" + SEPARATOR);

        } catch (Exception e) {
            Log.e(TAG, "Error logging request", e);
        }
    }

    /**
     * Log HTTP response after receiving
     */
    @NonNull
    private Response logResponse(@NonNull Response response, long elapsedMs) {
        try {
            Log.d(TAG, "╔" + SEPARATOR);
            Log.d(TAG, "║ " + RESPONSE_PREFIX + " [" + sourceId + "]");
            Log.d(TAG, "║ " + response.code() + " " + response.message() +
                    " (" + elapsedMs + "ms)");
            Log.d(TAG, "║ " + response.request().url());

            // Log headers
            Headers headers = response.headers();
            if (headers.size() > 0) {
                Log.d(TAG, "║ Headers:");
                for (int i = 0; i < headers.size(); i++) {
                    Log.d(TAG, "║   " + headers.name(i) + ": " + headers.value(i));
                }
            }

            // Log response body if present
            ResponseBody body = response.body();
            if (body != null) {
                long contentLength = body.contentLength();
                MediaType contentType = body.contentType();

                // Only log text-based responses
                if (isTextBased(contentType)) {
                    BufferedSource source = body.source();
                    source.request(Long.MAX_VALUE); // Buffer entire body
                    Buffer buffer = source.getBuffer();

                    Charset charset = contentType != null ?
                            contentType.charset(UTF8) : UTF8;

                    if (contentLength != 0) {
                        String bodyString = buffer.clone().readString(charset);
                        Log.d(TAG, "║ Body: " + truncate(bodyString));
                        Log.d(TAG, "║ Body size: " + contentLength + " bytes");
                    }
                } else {
                    Log.d(TAG, "║ Body: [Binary content, type=" + contentType +
                            ", size=" + contentLength + " bytes]");
                }
            }

            Log.d(TAG, "╚" + SEPARATOR);

        } catch (Exception e) {
            Log.e(TAG, "Error logging response", e);
        }

        return response;
    }

    /**
     * Log request failure
     */
    private void logRequestFailure(@NonNull Request request, @NonNull Exception e, long elapsedMs) {
        Log.e(TAG, "╔" + SEPARATOR);
        Log.e(TAG, "║ ✗ REQUEST FAILED [" + sourceId + "]");
        Log.e(TAG, "║ " + request.method() + " " + request.url());
        Log.e(TAG, "║ Failed after " + elapsedMs + "ms");
        Log.e(TAG, "║ Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        Log.e(TAG, "╚" + SEPARATOR);
    }

    /**
     * Extract request body as string
     */
    private String getRequestBodyString(@NonNull RequestBody body) {
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);

            Charset charset = UTF8;
            MediaType contentType = body.contentType();
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }

            return buffer.readString(charset);
        } catch (Exception e) {
            return "[Error reading body: " + e.getMessage() + "]";
        }
    }

    /**
     * Check if content type is text-based
     */
    private boolean isTextBased(MediaType contentType) {
        if (contentType == null) {
            return false;
        }

        String type = contentType.type();
        String subtype = contentType.subtype();

        return "text".equals(type) ||
                "application".equals(type) && (
                        "json".equals(subtype) ||
                                "xml".equals(subtype) ||
                                "javascript".equals(subtype) ||
                                "x-www-form-urlencoded".equals(subtype)
                );
    }

    /**
     * Truncate string if too long
     */
    @NonNull
    private String truncate(@NonNull String str) {
        if (str.length() <= MAX_BODY_LOG_SIZE) {
            return str;
        }
        return str.substring(0, MAX_BODY_LOG_SIZE) +
                "... [truncated, total: " + str.length() + " chars]";
    }
}