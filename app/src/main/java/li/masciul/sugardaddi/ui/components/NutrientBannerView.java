package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.core.enums.NutrientBannerStyle;
import li.masciul.sugardaddi.core.enums.NutrientLevel;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * NutrientBannerView - Scalable custom view for nutrient information banners
 *
 * REFACTORED VERSION 2.0 - Production-ready, device-agnostic design
 *
 * Key improvements:
 * - Uses LinearLayout with weights (scales perfectly on any screen)
 * - Maintains aspect ratio while respecting parent constraints
 * - Calculates optimal size in portrait, maintains it in landscape
 * - Text always properly centered (no absolute positioning)
 * - Supports both VERTICAL (1:1.4) and MODERN (1:1.2) styles
 *
 * Architecture:
 * LinearLayout (vertical)
 * ├── Top section (white) - nutrient name
 * ├── Middle section (white) - value + unit
 * └── Bottom section (colored) - % DRV
 *
 * Each section uses layout_weight for proportional sizing.
 *
 * @version 2.0 - Complete refactor for scalability
 */
public class NutrientBannerView extends LinearLayout {

    // ========== CONSTANTS ==========

    private static final int DEFAULT_MAX_WIDTH_DP = 92;  // Fallback if calculation fails
    private static int sMaxBannerWidthDp = -1;  // Calculated once, shared across all instances

    // ========== VIEWS ==========

    private LinearLayout topSection;
    private LinearLayout middleSection;
    private LinearLayout bottomSection;

    private TextView nameText;
    private TextView valueText;
    private TextView percentText;

    // ========== STYLING ==========

    private Typeface robotoCondensed;
    private Typeface robotoCondensedBold;

    private final DecimalFormat valueFormatter = createValueFormatter();
    private final DecimalFormat percentFormatter = createPercentFormatter();

    // ========== STATE ==========


    private Paint borderPaint;
    private NutrientBannerStyle currentStyle = NutrientBannerStyle.VERTICAL;
    private int currentWidthDp = -1;  // Actual width in dp
    private int currentHeightDp = -1;  // Actual height in dp

    // ========== CONSTRUCTORS ==========

    public NutrientBannerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public NutrientBannerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NutrientBannerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // ========== INITIALIZATION ==========

    private void init(Context context) {
        // Configure container
        setOrientation(VERTICAL);
        setWeightSum(100);  // Total = 100 weight units

        // Initialize border paint FIRST
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1));
        borderPaint.setColor(0xFFE0E0E0); // Gray
        borderPaint.setAntiAlias(true);
        borderPaint.setStrokeJoin(Paint.Join.MITER);
        setWillNotDraw(false);
        setClipToPadding(false);

        // Load fonts
        loadFonts(context);

        // Calculate max banner width once (shared across all instances)
        if (sMaxBannerWidthDp == -1) {
            sMaxBannerWidthDp = calculateMaxBannerWidth(context);
        }

        // Create sections with proportional weights
        createSections(context);
    }

    /**
     * Calculate maximum banner width to fit 5 banners in portrait
     * This becomes the size used in both portrait and landscape
     */
    private static int calculateMaxBannerWidth(Context context) {
        // Get screen width in dp
        int screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        float density = context.getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (screenWidthPx / density);

        // Account for container padding and spacing
        int containerPadding = 32;  // 16dp each side
        int totalSpacing = 16;  // 4dp × 4 gaps between 5 banners

        int availableWidth = screenWidthDp - containerPadding - totalSpacing;
        int optimalWidth = availableWidth / 5;

        // Ensure reasonable minimum (40dp) and maximum (92dp)
        return Math.max(40, Math.min(optimalWidth, DEFAULT_MAX_WIDTH_DP));
    }

    private void loadFonts(Context context) {
        try {
            robotoCondensed = ResourcesCompat.getFont(context, R.font.roboto_condensed);
            robotoCondensedBold = ResourcesCompat.getFont(context, R.font.roboto_condensed_bold);
        } catch (Exception e) {
            robotoCondensed = Typeface.DEFAULT;
            robotoCondensedBold = Typeface.DEFAULT_BOLD;
        }
    }

    /**
     * Create the 3 sections with layout weights
     *
     * Section weights are style-specific:
     * - VERTICAL: 25% top, 50% middle, 25% bottom (1:1.4 ratio)
     * - MODERN: 20% top, 60% middle, 20% bottom (1:1.2 ratio)
     */
    private void createSections(Context context) {
        // Top section (name)
        topSection = createSection(context);
        nameText = createTextView(context, false);
        topSection.addView(nameText);
        addView(topSection);

        // Middle section (value)
        middleSection = createSection(context);
        valueText = createTextView(context, false);
        middleSection.addView(valueText);
        addView(middleSection);

        // Bottom section (percent)
        bottomSection = createSection(context);
        percentText = createTextView(context, true);
        bottomSection.addView(percentText);
        addView(bottomSection);
    }

    private LinearLayout createSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(VERTICAL);
        section.setGravity(Gravity.CENTER);

        // Weight will be set when style is applied
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                0  // Height = 0 (required for weight)
        );
        section.setLayoutParams(params);

        return section;
    }

    private TextView createTextView(Context context, boolean bold) {
        TextView tv = new TextView(context);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(bold ? robotoCondensedBold : robotoCondensed);
        tv.setMaxLines(2);  // Allow 2 lines for energy (kcal + kj)

        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        tv.setLayoutParams(params);

        return tv;
    }

    // ========== PUBLIC API ==========

    /**
     * Set nutrient information (standard nutrients)
     */
    public void setNutrient(
            String name,
            double value,
            String unit,
            double percentDRV,
            NutrientLevel level,
            NutrientBannerStyle style) {

        this.currentStyle = style;

        // Apply style-specific layout
        applyStyleLayout(style);

        // Set colors and backgrounds
        applyColors(level, style);

        // Set text content
        nameText.setText(formatName(name, style));
        valueText.setText(formatValue(value, unit));
        percentText.setText(formatPercent(percentDRV));

        // Set text sizes (scale with banner size)
        applyTextSizes(style);
    }

    /**
     * Set energy information (special case with kcal + kj)
     */
    public void setEnergy(
            String label,
            double kcal,
            double kj,
            double percentDRV,
            NutrientLevel level,
            NutrientBannerStyle style) {

        this.currentStyle = style;

        applyStyleLayout(style);
        applyColors(level, style);

        nameText.setText(formatName(label, style));

        // Special formatting for energy (2 lines)
        String energyValue = Math.round(kcal) + "kcal\n" + Math.round(kj) + "KJ";
        valueText.setText(energyValue);

        percentText.setText(formatPercent(percentDRV));

        applyTextSizes(style);
    }

    // ========== STYLE APPLICATION ==========

    /**
     * Apply layout weights based on style
     *
     * VERTICAL (1:1.4 ratio):
     * - Top: 25% (name)
     * - Middle: 50% (value)
     * - Bottom: 25% (percent with rounded background)
     *
     * MODERN (1:1.2 ratio):
     * - Top: 20% (name on blue strip)
     * - Middle: 60% (value on white)
     * - Bottom: 20% (percent on colored strip)
     */
    private void applyStyleLayout(NutrientBannerStyle style) {
        LayoutParams topParams = (LayoutParams) topSection.getLayoutParams();
        LayoutParams middleParams = (LayoutParams) middleSection.getLayoutParams();
        LayoutParams bottomParams = (LayoutParams) bottomSection.getLayoutParams();

        if (style == NutrientBannerStyle.VERTICAL) {
            topParams.weight = 25;
            middleParams.weight = 50;
            bottomParams.weight = 25;
        } else {  // MODERN
            topParams.weight = 20;
            middleParams.weight = 60;
            bottomParams.weight = 20;
        }

        topSection.setLayoutParams(topParams);
        middleSection.setLayoutParams(middleParams);
        bottomSection.setLayoutParams(bottomParams);
    }

    /**
     * Apply colors and backgrounds based on style and level
     * Simple approach: container clips, sections use simple colors
     * Container border matches bottom section corners
     */
    private void applyColors(NutrientLevel level, NutrientBannerStyle style) {
        int levelColor = getLevelColor(level);

        if (style == NutrientBannerStyle.VERTICAL) {
            // VERTICAL: White top/middle + colored rounded bottom

            // Top and middle: simple white
            topSection.setBackgroundColor(Color.WHITE);
            middleSection.setBackgroundColor(Color.WHITE);

            // Bottom: colored with rounded bottom corners (12dp)
            float radius = dpToPx(12);
            GradientDrawable bottomBg = new GradientDrawable();
            bottomBg.setShape(GradientDrawable.RECTANGLE);
            bottomBg.setColor(levelColor);
            bottomBg.setCornerRadii(new float[]{
                    0, 0,           // top-left (square)
                    0, 0,           // top-right (square)
                    radius, radius, // bottom-right (12dp ROUNDED)
                    radius, radius  // bottom-left (12dp ROUNDED)
            });
            bottomSection.setBackground(bottomBg);

            // Text colors
            nameText.setTextColor(Color.BLACK);
            valueText.setTextColor(Color.BLACK);
            percentText.setTextColor(Color.WHITE);


            // Add padding so sections dont cover border
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

        } else {
            // MODERN: Square corners throughout

            topSection.setBackgroundColor(Color.parseColor("#5DADE2"));
            middleSection.setBackgroundColor(Color.WHITE);
            bottomSection.setBackgroundColor(levelColor);

            nameText.setTextColor(Color.WHITE);
            valueText.setTextColor(Color.BLACK);
            percentText.setTextColor(Color.WHITE);

            // Add padding so sections dont cover border
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

        }
    }

    /**
     * Apply text sizes based on banner width and style
     */
    private void applyTextSizes(NutrientBannerStyle style) {
        // Calculate scale factor based on actual width vs default 92dp
        float scale = currentWidthDp / 92f;

        if (style == NutrientBannerStyle.VERTICAL) {
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11 * scale);
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18 * scale);
            percentText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24 * scale);
        } else {  // MODERN
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12 * scale);
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20 * scale);
            percentText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24 * scale);
        }
    }

    // ========== FORMATTING HELPERS ==========

    private String formatName(String name, NutrientBannerStyle style) {
        if (name == null) return "";

        // MODERN style uses uppercase names
        if (style == NutrientBannerStyle.MODERN) {
            return name.toUpperCase(Locale.getDefault());
        }

        return name;
    }

    private String formatValue(double value, String unit) {
        return valueFormatter.format(value) + unit;
    }

    private String formatPercent(double percent) {
        return percentFormatter.format(Math.round(percent)) + "%";
    }

    // ========== COLOR HELPERS ==========

    private int getLevelColor(NutrientLevel level) {
        if (level == null) level = NutrientLevel.NEUTRAL;

        switch (level) {
            case EXCELLENT: return Color.parseColor("#4CAF50");  // Green
            case GOOD: return Color.parseColor("#8BC34A");  // Light green
            case AVERAGE: return Color.parseColor("#FFC107");  // Amber
            case MEDIOCRE: return Color.parseColor("#FF9800");  // Orange
            case BAD: return Color.parseColor("#F44336");  // Red
            default: return Color.parseColor("#5DADE2");  // Blue (neutral)
        }
    }

    // ========== MEASUREMENT ==========

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        // Determine width
        int width;
        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent specified exact width (from layout_weight)
            // But don't exceed max width calculated from portrait
            int parentWidthDp = pxToDp(widthSize);
            width = pxToDp(Math.min(dpToPx(sMaxBannerWidthDp), widthSize));
        } else {
            // Use max width
            width = sMaxBannerWidthDp;
        }

        // Calculate height based on aspect ratio
        double aspectRatio = currentStyle.getAspectRatio();
        int height = (int) (width * aspectRatio);

        // Store for text sizing
        currentWidthDp = width;
        currentHeightDp = height;

        // Convert to pixels
        int widthPx = dpToPx(width);
        int heightPx = dpToPx(height);

        setMeasuredDimension(widthPx, heightPx);

        // Measure children
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY)
        );
    }

    // ========== UTILITIES ==========

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    // ========== FORMATTERS ==========

    private static DecimalFormat createValueFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        DecimalFormat formatter = new DecimalFormat("0.#", symbols);
        formatter.setMaximumFractionDigits(1);
        return formatter;
    }

    private static DecimalFormat createPercentFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        return new DecimalFormat("0", symbols);
    }

    // ========== PUBLIC UTILITIES ==========

    /**
     * Get the maximum banner width (useful for layout calculations)
     */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (borderPaint == null) return;

        // Account for 2dp padding - border goes around the padded content
        float padding = dpToPx(2);
        float stroke = borderPaint.getStrokeWidth() / 2;
        RectF rect = new RectF(padding - stroke, padding - stroke,
                getWidth() - padding + stroke, getHeight() - padding + stroke);

        // Match the banner's corner style: square top, rounded bottom (12dp)
        float radius = dpToPx(12);
        Path path = new Path();
        path.moveTo(rect.left, rect.top);
        path.lineTo(rect.right, rect.top);
        path.lineTo(rect.right, rect.bottom - radius);
        path.arcTo(new RectF(rect.right - 2*radius, rect.bottom - 2*radius, rect.right, rect.bottom), 0, 90);
        path.lineTo(rect.left + radius, rect.bottom);
        path.arcTo(new RectF(rect.left, rect.bottom - 2*radius, rect.left + 2*radius, rect.bottom), 90, 90);
        path.lineTo(rect.left, rect.top);
        path.close();

        canvas.drawPath(path, borderPaint);
    }
}