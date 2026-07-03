package li.masciul.sugardaddi.ui.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import li.masciul.sugardaddi.ui.activities.ImageViewerActivity;

/**
 * ImageViewerLauncher - the single opt-in entry point for opening any image
 * full-screen. Call sites pass the FULL-IMAGE source (the result of
 * ImageDisplayUtils.resolveProductImageSource / resolveRecipeImageSource), NOT
 * the thumbnail they are displaying - the viewer always shows the original.
 *
 * The source from resolve*ImageSource is an Object that is either:
 *   - a File   (a local original: a user-set userImagePath, or an
 *               auto-cached imagePath), or
 *   - a String (a remote imageUrl), or
 *   - null     (no image at all; this launcher then no-ops).
 *
 * We normalise that to a single String extra and let the Activity decide, by
 * disk existence, whether to load it as a local File (with an mtime cache
 * signature) or as a remote URL - mirroring ImageDisplayUtils' own logic.
 * Opening a remote URL performs a network fetch by design: there is
 * no guarantee of a local original for non-favorited search results.
 */
public final class ImageViewerLauncher {

    private ImageViewerLauncher() {} // no instances

    /**
     * Open {@code source} full-screen. No-ops if there is nothing to show.
     *
     * @param context any Context capable of starting an Activity.
     * @param source  a File, a URL String, or null (from resolve*ImageSource).
     */
    public static void open(@NonNull Context context, @Nullable Object source) {
        if (source == null) return;

        final String normalised;
        if (source instanceof File) {
            normalised = ((File) source).getAbsolutePath();
        } else if (source instanceof String) {
            normalised = (String) source;
        } else {
            return; // resolve*ImageSource never returns other types; be defensive.
        }

        if (normalised.trim().isEmpty()) return;
        ImageViewerActivity.start(context, normalised);
    }
}