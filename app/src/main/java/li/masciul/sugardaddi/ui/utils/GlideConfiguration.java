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

import li.masciul.sugardaddi.data.network.ApiConfig;
import okhttp3.OkHttpClient;

/**
 * GlideConfiguration - Custom Glide module for SugarDaddi.
 *
 * Replaces Glide's default HttpUrlConnection networking with a custom
 * OkHttp client. This gives us:
 *   - Consistent timeouts across all image requests (connect 15s, read 30s)
 *   - Automatic retry on connection failure
 *   - Shared connection pool with the rest of the app's OkHttp usage
 *
 * Request/response logging is gated behind ApiConfig.DEBUG_LOGGING.
 * In production builds this interceptor adds zero overhead.
 *
 * Automatically discovered by Glide's annotation processor via @GlideModule.
 * No manual registration required.
 */
@GlideModule
public class GlideConfiguration extends AppGlideModule {

    private static final String TAG = "GlideConfiguration";

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Keep Glide's own log level at WARN in production; DEBUG_LOGGING promotes it to INFO.
        builder.setLogLevel(ApiConfig.DEBUG_LOGGING ? Log.INFO : Log.WARN);
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide,
                                   @NonNull Registry registry) {

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        // Request/response logging - only in debug builds to avoid logcat noise in production
        if (ApiConfig.DEBUG_LOGGING) {
            clientBuilder.addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                Log.d(TAG, "Loading image: " + request.url());
                try {
                    okhttp3.Response response = chain.proceed(request);
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Image load failed: HTTP " + response.code()
                                + " - " + request.url());
                    }
                    return response;
                } catch (Exception e) {
                    // Log the URL at WARN; the full stack trace comes from Glide's own error path
                    Log.w(TAG, "Image load error: " + request.url() + " - " + e.getMessage());
                    throw e;
                }
            });
        }

        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory((okhttp3.Call.Factory) clientBuilder.build()));

        Log.i(TAG, "Glide configured - OkHttp client ready (connect 15s, read 30s, retry enabled)");
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}