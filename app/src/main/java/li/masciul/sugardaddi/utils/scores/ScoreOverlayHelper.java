package li.masciul.sugardaddi.utils.scores;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import li.masciul.sugardaddi.managers.LanguageManager;

/**
 * ScoreOverlayHelper - FINAL WORKING VERSION
 *
 * Creates Nutri-Score stickers that:
 * 1. Use FIXED height (48dp horizontal, 100dp vertical) by default
 * 2. Calculate width based on aspect ratio
 * 3. Calculate blue strip height from actual dimensions
 * 4. Center text properly in blue strip
 * 5. Scale text size appropriately
 */
public class ScoreOverlayHelper {

    private static final String TAG = "ScoreOverlayHelper";

    // Standard sticker dimensions
    private static final int NUTRISCORE_HORIZONTAL_HEIGHT_DP = 48;
    private static final int NUTRISCORE_VERTICAL_HEIGHT_DP = 100;
    private static final int GREENSCORE_HORIZONTAL_HEIGHT_DP = 48;
    private static final int GREENSCORE_LEAF_SIZE_DP = 32;

    // Blue strip is approximately 20% of total height
    private static final float BLUE_STRIP_RATIO = 0.2224f;

    // Text size relative to blue strip height
    private static final float TEXT_SIZE_RATIO = 0.4470f;  // 50% of blue strip height

    /**
     * Create a Nutri-Score TPL sticker with localized text overlay
     *
     * Uses FIXED height (48dp for horizontal, 100dp for vertical)
     * Width adjusts based on image aspect ratio
     * Text is properly centered in blue strip
     *
     * @param context Context for resources
     * @param grade Nutri-Score grade (A-E, or null for unknown)
     * @param orientation "horizontal" or "vertical"
     * @return FrameLayout containing sticker with text overlay
     */
    public static FrameLayout createNutriScoreTplWithText(Context context, String grade, String orientation) {
        android.util.Log.d(TAG, "Creating Nutri-Score: grade=" + grade + ", orientation=" + orientation);

        // Get fixed height based on orientation
        int fixedHeightDp = orientation.equals("vertical")
                ? NUTRISCORE_VERTICAL_HEIGHT_DP
                : NUTRISCORE_HORIZONTAL_HEIGHT_DP;
        int fixedHeightPx = dpToPx(context, fixedHeightDp);

        // Create container with FIXED height
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,  // Width adjusts to image
                fixedHeightPx                         // FIXED height
        );
        container.setLayoutParams(containerParams);

        // Create and configure image
        ImageView stickerImage = new ImageView(context);
        int stickerDrawable = orientation.equals("vertical")
                ? ScoreUtils.getNutriScoreTplVertical(grade)
                : ScoreUtils.getNutriScoreTplHorizontal(grade);

        stickerImage.setImageResource(stickerDrawable);

        // Image fills fixed height, adjusts width for aspect ratio
        stickerImage.setAdjustViewBounds(true);
        stickerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // CENTER_HORIZONTAL gravity is critical: without it the image anchors to TOP|LEFT
        // (FrameLayout default), making the sticker appear left-shifted when the container
        // is wider than the image (e.g. when minWidth forces extra space).
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,   // Width adjusts for aspect ratio
                ViewGroup.LayoutParams.MATCH_PARENT    // Fill fixed height
        );
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        stickerImage.setLayoutParams(imageParams);
        container.addView(stickerImage);

        // Add text overlay for valid grades
        if (ScoreUtils.isValidNutriScoreGrade(grade)) {
            TextView tplText = createTplTextView(context, orientation);
            container.addView(tplText);

            // Setup proper text positioning and sizing after layout
            setupTextPositioning(container, tplText, stickerImage, fixedHeightPx);
        }

        android.util.Log.d(TAG, "Sticker created with fixed height: " + fixedHeightDp + "dp");
        return container;
    }

    /**
     * Setup text positioning and sizing based on actual dimensions
     *
     * Calculates:
     * - Blue strip height (20% of total height)
     * - Text size (50% of blue strip height)
     * - Proper vertical centering in blue strip
     */
    private static void setupTextPositioning(
            final FrameLayout container,
            final TextView textView,
            final ImageView imageView,
            final int fixedHeightPx) {

        container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                container.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Get actual dimensions after layout
                int actualWidth = imageView.getWidth();
                int actualHeight = imageView.getHeight();

                if (actualHeight > 0 && actualWidth > 0) {
                    // Calculate blue strip height (bottom 20% of sticker)
                    float blueStripHeightPx = actualHeight * BLUE_STRIP_RATIO;

                    // Calculate text size as percentage of blue strip height
                    float textSizePx = blueStripHeightPx * TEXT_SIZE_RATIO;

                    // Convert to SP
                    float density = container.getContext().getResources().getDisplayMetrics().scaledDensity;
                    float textSizeSp = textSizePx / density;

                    // Clamp for readability
                    textSizeSp = Math.max(4f, Math.min(textSizeSp, 12f));

                    // Apply text size
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);

                    // Update text position to center in blue strip
                    FrameLayout.LayoutParams textParams = (FrameLayout.LayoutParams) textView.getLayoutParams();

                    // Position text in the center of the blue strip
                    // Blue strip is in the bottom 20%, so center point is at 90% of total height
                    int blueStripCenterFromBottom = (int) (blueStripHeightPx / 2);
                    textParams.bottomMargin = blueStripCenterFromBottom - (int)(textSizePx / 2);

                    textView.setLayoutParams(textParams);

                    android.util.Log.d(TAG, String.format(
                            "Text positioned: width=%dpx, height=%dpx, blueStrip=%.1fpx, textSize=%.1fsp, bottomMargin=%dpx",
                            actualWidth, actualHeight, blueStripHeightPx, textSizeSp, textParams.bottomMargin
                    ));
                }
            }
        });
    }

    /**
     * Create the TPL text overlay TextView
     */
    private static TextView createTplTextView(Context context, String orientation) {
        TextView textView = new TextView(context);

        // Get localized text
        String tplText = getTplText(context);
        textView.setText(tplText);

        // Minimal padding
        int paddingPx = dpToPx(context, 1);
        textView.setPadding(paddingPx, 0, paddingPx, 0);

        // Style text
        textView.setTextColor(0xFFFFFFFF);  // White
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setLetterSpacing(0.05f);
        textView.setAllCaps(true);

        // Prevent overflow
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        // Position at bottom with initial placeholder
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        textParams.bottomMargin = dpToPx(context, 2);  // Initial placeholder

        textView.setLayoutParams(textParams);

        // Placeholder text size (will be adjusted after layout)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 5f);

        return textView;
    }

    /**
     * Setup a simple ImageView with proper sizing (for Green-Score leaf)
     */
    public static void setupStickerImageView(Context context, ImageView imageView,
                                             int drawableRes, int heightDp) {
        imageView.setImageResource(drawableRes);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(context, heightDp)
            );
        } else {
            params.height = dpToPx(context, heightDp);
        }
        imageView.setLayoutParams(params);
    }

    /**
     * Get localized TPL text
     */
    public static String getTplText(Context context) {
        LanguageManager.SupportedLanguage language = LanguageManager.getCurrentLanguage(context);

        switch (language) {
            case FRENCH:
                return "NOUVEAU CALCUL";
            case ENGLISH:
                return "NEW CALCUL";
            default:
                return "NEW CALCUL";
        }
    }

    /**
     * Convert dp to pixels
     */
    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // Standard dimensions
    public static int getNutriScoreHorizontalHeight() {
        return NUTRISCORE_HORIZONTAL_HEIGHT_DP;
    }

    public static int getNutriScoreVerticalHeight() {
        return NUTRISCORE_VERTICAL_HEIGHT_DP;
    }

    public static int getGreenScoreHorizontalHeight() {
        return GREENSCORE_HORIZONTAL_HEIGHT_DP;
    }

    public static int getGreenScoreLeafSize() {
        return GREENSCORE_LEAF_SIZE_DP;
    }
}