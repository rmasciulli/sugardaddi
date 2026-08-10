package li.masciul.sugardaddi.ui.utils;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import li.masciul.sugardaddi.core.models.FoodProduct;
import li.masciul.sugardaddi.core.models.Recipe;

/**
 * Binds the shared thumbnail_72dp.xml include (container + image + expand
 * icon) for any search card - product or recipe, any source. This exact
 * four-step body (resolve thumbnail source -> show/collapse the container
 * -> load -> bind full-screen tap against the separate full-size source)
 * was previously copy-pasted independently into 8 different search
 * delegates, with cosmetic drift between them but no real behavioral
 * difference. Written once here instead.
 *
 * Takes the three views directly rather than a ViewHolder type, since each
 * delegate has its own distinct ViewHolder class with no shared interface -
 * this is the smallest common contract that works for all of them.
 */
public final class CardThumbnailHelper {

    private CardThumbnailHelper() {}

    /**
     * @param thumbnailContainer thumbnail_72dp's root view - nullable only
     *                           for defensiveness; every real caller has it
     * @param thumbnailImage     thumbnail_72dp's ImageView
     * @param thumbnailExpandIcon thumbnail_72dp's expand-icon overlay -
     *                            nullable; bindFullScreenTap handles a null
     *                            icon gracefully (tap still works, no visual hint)
     */
    public static void bindProductThumbnail(@NonNull Context context,
                                            @Nullable View thumbnailContainer,
                                            @NonNull ImageView thumbnailImage,
                                            @Nullable View thumbnailExpandIcon,
                                            @NonNull FoodProduct product) {
        Object source = ImageDisplayUtils.resolveProductThumbnailSource(product);
        if (source != null) {
            if (thumbnailContainer != null) thumbnailContainer.setVisibility(View.VISIBLE);
            ImageDisplayUtils.loadCardThumbnail(context, source, thumbnailImage);
        } else if (thumbnailContainer != null) {
            thumbnailContainer.setVisibility(View.GONE);
        }
        // Full-screen tap opens the separate, full-size image source - not
        // necessarily the same as the thumbnail source (e.g. a small
        // remote thumbnail vs. a larger remote original).
        ImageDisplayUtils.bindFullScreenTap(context, thumbnailImage, thumbnailExpandIcon,
                ImageDisplayUtils.resolveProductImageSource(product));
    }

    /** Recipe counterpart - see bindProductThumbnail's Javadoc for the shared contract. */
    public static void bindRecipeThumbnail(@NonNull Context context,
                                           @Nullable View thumbnailContainer,
                                           @NonNull ImageView thumbnailImage,
                                           @Nullable View thumbnailExpandIcon,
                                           @NonNull Recipe recipe) {
        Object source = ImageDisplayUtils.resolveRecipeThumbnailSource(recipe);
        if (source != null) {
            if (thumbnailContainer != null) thumbnailContainer.setVisibility(View.VISIBLE);
            ImageDisplayUtils.loadCardThumbnail(context, source, thumbnailImage);
        } else if (thumbnailContainer != null) {
            thumbnailContainer.setVisibility(View.GONE);
        }
        ImageDisplayUtils.bindFullScreenTap(context, thumbnailImage, thumbnailExpandIcon,
                ImageDisplayUtils.resolveRecipeImageSource(recipe));
    }
}