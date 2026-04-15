package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * GlideConfiguration - Custom Glide setup for SugarDaddi
 *
 * Configures Glide with:
 * - Custom OkHttp client with proper timeouts (15s/30s/15s)
 * - Retry on connection failure
 * - Request/response logging
 *
 * Fixes:
 * - SocketTimeoutException (read timed out)
 * - HttpException (status code: -1)
 * - Cache corruption from failed loads
 *
 * This class is automatically discovered by Glide's annotation processor.
 * No manual registration required!
 */
@GlideModule
public class GlideConfiguration extends AppGlideModule {

    private static final String TAG = "GlideConfiguration";

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Enable logging for debugging
        builder.setLogLevel(Log.INFO);
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Create OkHttp client with custom timeouts and retry logic
        OkHttpClient client = new OkHttpClient.Builder()
                // Connection timeout: Time to establish connection
                .connectTimeout(15, TimeUnit.SECONDS)

                // Read timeout: Time to read data from server
                .readTimeout(30, TimeUnit.SECONDS)

                // Write timeout: Time to write data to server
                .writeTimeout(15, TimeUnit.SECONDS)

                // Retry on connection failure
                .retryOnConnectionFailure(true)

                // Add interceptor for logging (optional, can be removed in production)
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    Log.d(TAG, "Loading image: " + request.url());

                    try {
                        okhttp3.Response response = chain.proceed(request);
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Image load failed: " + response.code() + " - " + request.url());
                        }
                        return response;
                    } catch (Exception e) {
                        Log.e(TAG, "Image load error: " + request.url(), e);
                        throw e;
                    }
                })

                .build();

        // Replace Glide's default networking with our custom OkHttp client
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(client));

        Log.i(TAG, "Glide configured with custom OkHttp client (timeouts: 15s/30s/15s, retry enabled)");
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // We don't use manifest parsing, so disable it for performance
        return false;
    }
}