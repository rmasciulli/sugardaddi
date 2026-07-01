package li.masciul.sugardaddi.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;

import li.masciul.sugardaddi.R;

/**
 * ImageViewerActivity - a self-contained, full-screen, black-background viewer
 * with pinch-to-zoom and pan (PhotoView). Viewer only: no crop/recenter/edit.
 *
 * Launched exclusively via {@link li.masciul.sugardaddi.ui.utils.ImageViewerLauncher},
 * which normalises the image source to the single String extra read here.
 *
 * Extends AppCompatActivity (not BaseActivity): the viewer carries no language /
 * theme-switching UI, its theme is pinned in the manifest
 * (Theme.SugarDaddi.ImageViewer), and its only text is content descriptions that
 * resolve from resource qualifiers - so BaseActivity's machinery buys nothing here.
 *
 * Dismiss: the close button, a tap on the black margin outside the photo
 * (PhotoView outside-tap), or the system back gesture.
 */
public class ImageViewerActivity extends AppCompatActivity {

    /** Absolute local path OR remote URL of the image to show. */
    private static final String EXTRA_SOURCE = "li.masciul.sugardaddi.extra.IMAGE_SOURCE";

    /**
     * Start the viewer for an already-normalised source string.
     * Prefer {@link li.masciul.sugardaddi.ui.utils.ImageViewerLauncher#open} at
     * call sites - it does the File/URL normalisation and null-guarding.
     *
     * @param context any Context capable of starting an Activity.
     * @param source  absolute file path or remote URL; must be non-empty.
     */
    public static void start(@NonNull Context context, @NonNull String source) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putExtra(EXTRA_SOURCE, source);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        String source = getIntent() != null ? getIntent().getStringExtra(EXTRA_SOURCE) : null;
        if (source == null || source.trim().isEmpty()) {
            finish(); // nothing to show - never present an empty viewer.
            return;
        }

        PhotoView photoView = findViewById(R.id.photoView);
        ImageButton closeButton = findViewById(R.id.closeButton);

        loadInto(photoView, source);

        // Three ways out: close button, tap outside the photo, system back gesture.
        closeButton.setOnClickListener(v -> finish());
        photoView.setOnOutsidePhotoTapListener(view -> finish());
    }

    /**
     * Resolve the source the same way the rest of the image layer does - if a
     * file exists at this path it is a local original (load as a File and key the
     * Glide cache on its mtime so an in-place overwrite isn't served stale);
     * otherwise treat it as a remote URL.
     *
     * Sizing policy: "fit if larger, native if smaller". Large images are scaled
     * down to fit the screen; images smaller than the screen are shown at their
     * true pixel size, centered, never upscaled (which looks soft/stretched).
     * Enforced on BOTH sides:
     *   - Glide centerInside: returns the source untouched when it already fits
     *     within the decode cap, downsampling only when larger (this also caps a
     *     large remote CDN image so it is not decoded at full resolution into
     *     PhotoView's single in-memory bitmap). Local heroes, bounded by
     *     ImageProfile.HERO, fall under the cap and decode at native size.
     *   - PhotoView CENTER_INSIDE: clamps the display scale to at most 1.0, so a
     *     small bitmap shows at native size rather than stretched to fill.
     * Pinch-to-zoom past native remains available in both cases.
     */
    private void loadInto(@NonNull PhotoView photoView, @NonNull String source) {
        // Never enlarge to fill the screen: fit-if-larger, native-if-smaller.
        photoView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        File file = new File(source);
        Object model = file.exists() ? file : source;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        // 1.5x the longest screen edge: headroom for zoom without blowing memory.
        int cap = Math.round(Math.max(dm.widthPixels, dm.heightPixels) * 1.5f);

        RequestBuilder<Drawable> request = Glide.with(this)
                .load(model)
                .override(cap, cap)
                .centerInside()                    // fit if larger, never upscale.
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.drawable.ic_food_error);

        if (model instanceof File) {
            request = request.signature(new ObjectKey(((File) model).lastModified()));
        }
        request.into(photoView);
    }
}