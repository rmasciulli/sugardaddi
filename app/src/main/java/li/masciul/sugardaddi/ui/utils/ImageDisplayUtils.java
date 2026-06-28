package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;

/**
 * ImageDisplayUtils - the single source of truth for "which image do I show for an
 * item, and how do I load it", shared by the detail renderers (hero) and the search
 * delegates (card).
 *
 * STRICT image/thumbnail separation - one purpose per slot, no cross-bleed:
 *   HERO (detail) : userImagePath     → imagePath     → imageUrl
 *   CARD (search) : userThumbnailPath → thumbnailPath → thumbnailUrl → imageUrl
 *
 * The user's image and thumbnail are distinct choices and NEVER appear in each
 * other's slot - that bleed was the cause of "deleting the image shows the
 * thumbnail" and "the hero shows up as the card thumbnail". imageUrl stays as the
 * card's last resort ONLY because recipe sources (TheMealDB/TheCocktailDB) expose
 * no thumbnailUrl - it's the single remote image they have; product sources
 * populate thumbnailUrl and never reach it.
 *
 * Loading detail: local user images reuse a deterministic path on replace, so the
 * Glide cache is keyed on the file's mtime - without that, an overwrite serves the
 * stale cached bitmap. Remote URLs need no signature (a new URL is a new key).
 */
public final class ImageDisplayUtils {

    /** Hero override height in dp (width = screen width). */
    private static final int HERO_HEIGHT_DP = 200;
    /** Card thumbnail is a square of this many dp. */
    private static final int CARD_SIZE_DP = 72;

    private ImageDisplayUtils() {} // no instances

    // ===================================================================
    // SOURCE RESOLUTION  (strict image vs thumbnail separation)
    // ===================================================================

    /** Returns the File iff the path is non-blank AND the file exists on disk, else null. */
    @Nullable
    private static File existingFile(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        return f.exists() ? f : null;
    }

    /** Hero source for a product - image sources only. Returns a File, a URL String, or null. */
    @Nullable
    public static Object resolveProductImageSource(@NonNull FoodProduct product) {
        File userImage = existingFile(product.getUserImagePath());      // user's full-size image
        if (userImage != null) return userImage;

        File imagePath = existingFile(product.getImagePath());          // auto-cached full image
        if (imagePath != null) return imagePath;

        String imageUrl = product.getImageUrl();                        // remote full image
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;

        return null;
    }

    /** Card source for a product - thumbnail sources only (imageUrl = last-resort remote). */
    @Nullable
    public static Object resolveProductThumbnailSource(@NonNull FoodProduct product) {
        File userThumb = existingFile(product.getUserThumbnailPath());   // user's thumbnail
        if (userThumb != null) return userThumb;

        File thumbPath = existingFile(product.getThumbnailPath());       // auto-cached on favourite
        if (thumbPath != null) return thumbPath;

        String thumbUrl = product.getThumbnailUrl();                    // remote small image
        if (thumbUrl != null && !thumbUrl.trim().isEmpty()) return thumbUrl;

        String imageUrl = product.getImageUrl();                        // only reached without a thumbnailUrl
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;

        return null;
    }

    /** Hero source for a recipe - image sources only. */
    @Nullable
    public static Object resolveRecipeImageSource(@NonNull Recipe recipe) {
        File userImage = existingFile(recipe.getUserImagePath());
        if (userImage != null) return userImage;

        File imagePath = existingFile(recipe.getImagePath());
        if (imagePath != null) return imagePath;

        String imageUrl = recipe.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;

        return null;
    }

    /** Card source for a recipe - thumbnail sources only (imageUrl = TheMealDB/TheCocktailDB's only remote image). */
    @Nullable
    public static Object resolveRecipeThumbnailSource(@NonNull Recipe recipe) {
        File userThumb = existingFile(recipe.getUserThumbnailPath());
        if (userThumb != null) return userThumb;

        File thumbPath = existingFile(recipe.getThumbnailPath());
        if (thumbPath != null) return thumbPath;

        String thumbUrl = recipe.getThumbnailUrl();
        if (thumbUrl != null && !thumbUrl.trim().isEmpty()) return thumbUrl;

        String imageUrl = recipe.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl;

        return null;
    }

    // ===================================================================
    // LOADING
    // ===================================================================

    /**
     * Shared Glide load for a non-null source. The mtime signature (Files only)
     * makes an in-place overwrite invalidate the stale cached bitmap.
     */
    private static void load(@NonNull Context context, @NonNull Object source,
                             @NonNull ImageView imageView, int widthPx, int heightPx) {
        RequestBuilder<Drawable> request = Glide.with(context)
                .load(source)
                .override(widthPx, heightPx)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_food_placeholder)
                .error(R.drawable.ic_food_error)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade());

        if (source instanceof File) {
            request = request.signature(new ObjectKey(((File) source).lastModified()));
        }
        request.into(imageView);
    }

    /**
     * Load a detail hero image (screen-width × 200dp). Visibility is NOT managed
     * here - container-gated renderers toggle their own container and call this only
     * when source != null; Default/Off keep the ImageView visible by default.
     */
    public static void loadHeroImage(@NonNull Context context,
                                     @Nullable Object source,
                                     @Nullable ImageView imageView) {
        if (imageView == null) return;
        if (source == null) {
            imageView.setImageResource(R.drawable.ic_food_placeholder);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            return;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        load(context, source, imageView, dm.widthPixels, Math.round(HERO_HEIGHT_DP * dm.density));
    }

    /** Load a search/favourites card thumbnail (72dp square). */
    public static void loadCardThumbnail(@NonNull Context context,
                                         @Nullable Object source,
                                         @Nullable ImageView imageView) {
        if (imageView == null) return;
        if (source == null) {
            imageView.setImageResource(R.drawable.ic_food_placeholder);
            return;
        }
        int sizePx = Math.round(CARD_SIZE_DP * context.getResources().getDisplayMetrics().density);
        load(context, source, imageView, sizePx, sizePx);
    }

    /**
     * Make an already-bound ImageView open the full-screen viewer on tap.
     *
     * Pass the FULL-IMAGE source (resolveProductImageSource / resolveRecipeImageSource),
     * NOT the thumbnail source the card is displaying - the viewer always shows the
     * original. For a non-favourited card that full source is usually the remote
     * imageUrl, which the viewer fetches over the network (Option A).
     *
     * RECYCLING-SAFE: when {@code fullSource} is null the listener is cleared and the
     * view made non-clickable, so a recycled RecyclerView holder never keeps a stale
     * tap target pointing at the previous item's image. Callers must therefore call
     * this on EVERY bind, not just when a source exists.
     *
     * @param context    any Context capable of starting an Activity.
     * @param imageView  the bound card/hero ImageView (may be null - then no-op).
     * @param fullSource result of resolve*ImageSource: a File, a URL String, or null.
     */
    public static void bindFullScreenTap(@NonNull Context context,
                                         @Nullable ImageView imageView,
                                         @Nullable Object fullSource) {
        if (imageView == null) return;
        if (fullSource == null) {
            imageView.setOnClickListener(null);
            imageView.setClickable(false);
            return;
        }
        imageView.setOnClickListener(v -> ImageViewerLauncher.open(context, fullSource));
    }
}