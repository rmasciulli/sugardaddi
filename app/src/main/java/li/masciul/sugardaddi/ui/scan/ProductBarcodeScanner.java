package li.masciul.sugardaddi.ui.scan;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.ui.activities.ProductDetailsActivity;

/**
 * ProductBarcodeScanner - scans a retail product barcode and opens the matching product.
 *
 * PURPOSE / SCOPE
 * ===============
 * This owns the full "scan a product barcode -> open the product" flow: it launches
 * Google's turnkey ML Kit code scanner and, on a successful scan, navigates to
 * ProductDetailsActivity for the Open Food Facts barcode lookup. It is deliberately NOT
 * a generic scanning utility - barcode lookup exists only for OFF (the sole data source
 * keyed by EAN/UPC), so this class is scoped to that single purpose and constructed per
 * host Activity.
 *
 * WHY NO DEDICATED ACTIVITY
 * =========================
 * GmsBarcodeScanning is self-contained: startScan() renders its own full-screen camera
 * UI, handles the camera permission, and downloads its ML Kit module on demand, returning
 * the result via a Task. It needs a host Activity for context but not a bespoke one, so
 * there is no scanner Activity or layout - just this small component, invoked wherever a
 * scan is offered (the main FAB, and the detail screen's "rescan" retry).
 *
 * FORMATS - EAN / UPC ONLY
 * ========================
 * Restricted to the retail formats OFF is keyed on (EAN-13/EAN-8, UPC-A/UPC-E). ML Kit
 * checksum-validates these, so no manual barcode validation is needed here, and dropping
 * CODE_128/CODE_39/QR stops the scanner locking onto non-product logistics barcodes that
 * often share a package - the cause of the "scanned too fast -> wrong code" misreads.
 *
 * MODULE INSTALL
 * ==============
 * No explicit ModuleInstall call: startScan() downloads the scanner module on demand
 * (Google's documented default). A first scan on a fresh install may therefore show a
 * brief one-time download; a failure there surfaces via onError and self-heals on retry.
 *
 * USAGE
 * =====
 * <pre>
 *   new ProductBarcodeScanner(activity).scanAndOpenProduct();
 * </pre>
 */
public class ProductBarcodeScanner {

    private static final String TAG = "SugarDaddi_Scanner";

    /** Host activity - provides the Context for the scanner UI and for navigation. */
    @NonNull
    private final Activity host;

    /**
     * @param host The Activity offering the scan (e.g. the main screen or the product
     *             detail screen). Used as the scanner's context and to launch the result.
     */
    public ProductBarcodeScanner(@NonNull Activity host) {
        this.host = host;
    }

    /**
     * Launch the scanner and, on a successful scan, open the scanned product.
     *
     * The whole flow is self-contained via Google's scanner UI; we react to its Task:
     *   - success   -> open the product (or report an unreadable value),
     *   - cancelled -> do nothing (the user backed out; not an error),
     *   - failure   -> a short error toast.
     * Safe to call once the host Activity is created.
     */
    public void scanAndOpenProduct() {
        // Retail food-barcode formats only. See the FORMATS note above.
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,   // most common on food products
                        Barcode.FORMAT_EAN_8,    // smaller products
                        Barcode.FORMAT_UPC_A,    // North American products
                        Barcode.FORMAT_UPC_E)    // compressed UPC
                .enableAutoZoom()                // improves reliability on small/distant codes
                .build();

        GmsBarcodeScanning.getClient(host, options)
                .startScan()
                .addOnSuccessListener(barcode -> openProduct(barcode.getRawValue()))
                .addOnCanceledListener(() ->
                        Log.d(TAG, "Scan cancelled by user"))   // normal - no error UI
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Scan failed", e);
                    toast(host.getString(R.string.scanning_failed, e.getMessage()));
                });
    }

    /**
     * Navigate to the product detail screen for the scanned barcode.
     *
     * The barcode is passed as the product identifier; ProductManager routes an EAN/UPC
     * value to the OFF barcode lookup. FLAG_ACTIVITY_CLEAR_TOP means that when this runs
     * from a "rescan" retry on an existing (failed) detail screen, that screen is replaced
     * rather than stacked. EXTRA_FROM_BARCODE_SCAN tells the detail screen the lookup came
     * from a scan, so a failure offers rescan instead of replaying the same query.
     */
    private void openProduct(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            // ML Kit returned no usable value - nothing to look up.
            toast(host.getString(R.string.barcode_invalid_format));
            return;
        }

        Intent intent = new Intent(host, ProductDetailsActivity.class);
        intent.putExtra(ProductDetailsActivity.EXTRA_FOOD_ITEM, barcode.trim());
        intent.putExtra(ProductDetailsActivity.EXTRA_FROM_BARCODE_SCAN, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        host.startActivity(intent);
    }

    /** Short toast on the host activity. */
    private void toast(@NonNull String message) {
        Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
    }
}