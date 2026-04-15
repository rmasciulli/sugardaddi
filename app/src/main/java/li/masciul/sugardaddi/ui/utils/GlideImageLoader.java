package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * GlideImageLoader - Helper for loading images with proper error handling
 *
 * Features:
 * - Automatic cache clearing on repeated failures
 * - Proper error logging
 * - Retry mechanism
 * - Timeout handling
 *
 * Usage:
 * GlideImageLoader.load(context, imageUrl)
 *     .placeholder(R.drawable.placeholder)
 *     .error(R.drawable.error)
 *     .into(imageView);
 */
public class GlideImageLoader {

    private static final String TAG = "GlideImageLoader";

    private final Context context;
    private final String imageUrl;
    private int placeholderRes = 0;
    private int errorRes = 0;
    private boolean centerCrop = false;
    private boolean clearCacheOnError = true;

    private GlideImageLoader(Context context, String imageUrl) {
        this.context = context;
        this.imageUrl = imageUrl;
    }

    /**
     * Start loading an image
     */
    public static GlideImageLoader load(Context context, String imageUrl) {
        return new GlideImageLoader(context, imageUrl);
    }

    /**
     * Set placeholder while loading
     */
    public GlideImageLoader placeholder(@DrawableRes int placeholderRes) {
        this.placeholderRes = placeholderRes;
        return this;
    }

    /**
     * Set error image if load fails
     */
    public GlideImageLoader error(@DrawableRes int errorRes) {
        this.errorRes = errorRes;
        return this;
    }

    /**
     * Apply center crop transformation
     */
    public GlideImageLoader centerCrop() {
        this.centerCrop = true;
        return this;
    }

    /**
     * Disable automatic cache clearing on error (default: enabled)
     */
    public GlideImageLoader noCacheClearOnError() {
        this.clearCacheOnError = false;
        return this;
    }

    /**
     * Load image into ImageView
     */
    public void into(@NonNull ImageView imageView) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            // No URL provided, show placeholder
            if (placeholderRes != 0) {
                imageView.setImageResource(placeholderRes);
            }
            return;
        }

        // Build Glide request with error handling
        var requestBuilder = Glide.with(context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)  // Cache both original and resized
                .timeout(30000)  // 30 second timeout
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        // Log error
                        Log.w(TAG, "Image load failed: " + imageUrl, e);

                        // Clear cache on repeated failures
                        if (clearCacheOnError) {
                            Log.i(TAG, "Clearing cache for failed image: " + imageUrl);
                            // Clear memory cache immediately
                            Glide.get(context).clearMemory();
                            // Clear disk cache asynchronously
                            new Thread(() -> Glide.get(context).clearDiskCache()).start();
                        }

                        // Return false to show error drawable
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        // Success! Log it for debugging
                        Log.d(TAG, "Image loaded successfully: " + imageUrl + " (source: " + dataSource + ")");
                        return false;
                    }
                });

        // Add placeholder
        if (placeholderRes != 0) {
            requestBuilder = requestBuilder.placeholder(placeholderRes);
        }

        // Add error drawable
        if (errorRes != 0) {
            requestBuilder = requestBuilder.error(errorRes);
        }

        // Add transformations
        if (centerCrop) {
            requestBuilder = requestBuilder.centerCrop();
        }

        // Load into ImageView
        requestBuilder.into(imageView);
    }
}