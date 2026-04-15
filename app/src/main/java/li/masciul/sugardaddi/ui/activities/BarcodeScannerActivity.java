package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import li.masciul.sugardaddi.R;

/**
 * BarcodeScannerActivity - Barcode scanning for product lookup
 *
 * Uses Google ML Kit's barcode scanner for reliable scanning
 * Extends BaseActivity for consistent theme/language handling
 */
public class BarcodeScannerActivity extends BaseActivity {

    private GmsBarcodeScanner scanner;
    private boolean isScanning = false;

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_barcode_scanner);

        setupToolbar();
        setupScanner();
        startScanning();

        logDebug("BarcodeScannerActivity initialized");
    }

    /**
     * Setup toolbar using BaseActivity helper
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setupToolbarNavigation(toolbar, R.string.scanner_title);
    }

    /**
     * Setup barcode scanner with comprehensive format support
     */
    private void setupScanner() {
        // Configure scanner options for food product barcodes
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        // Common food product formats
                        Barcode.FORMAT_EAN_13,    // Most common for food products
                        Barcode.FORMAT_EAN_8,     // Common for smaller products
                        Barcode.FORMAT_UPC_A,     // North American products
                        Barcode.FORMAT_UPC_E,     // Compressed UPC

                        // Additional formats
                        Barcode.FORMAT_CODE_128,  // Some specialty products
                        Barcode.FORMAT_CODE_39,   // Older format
                        Barcode.FORMAT_QR_CODE    // QR codes with product URLs
                )
                .enableAutoZoom()  // Improve scanning reliability
                .build();

        scanner = GmsBarcodeScanning.getClient(this, options);

        // Ensure scanner module is available
        ensureScannerModuleInstalled();

        logDebug("Barcode scanner configured with " + 7 + " formats");
    }

    /**
     * Ensure ML Kit scanner module is installed
     */
    private void ensureScannerModuleInstalled() {
        ModuleInstallRequest moduleInstallRequest = ModuleInstallRequest.newBuilder()
                .addApi(scanner)
                .build();

        ModuleInstall.getClient(this)
                .installModules(moduleInstallRequest)
                .addOnSuccessListener(response -> {
                    logDebug("Barcode scanner module installed successfully");
                })
                .addOnFailureListener(e -> {
                    logError("Failed to install barcode scanner module", e);
                    showError(getSafeString(R.string.scanner_module_failed));
                });
    }

    /**
     * Start barcode scanning process
     */
    private void startScanning() {
        if (isScanning) {
            logDebug("Scanning already in progress, ignoring duplicate start request");
            return;
        }

        isScanning = true;
        logDebug("Starting barcode scanning");

        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    isScanning = false;
                    String result = barcode.getRawValue();

                    if (result != null && !result.isEmpty()) {
                        if (isValidProductBarcode(result)) {
                            onBarcodeScanned(result);
                        } else {
                            logDebug("Invalid barcode format: " + result);
                            showError(getSafeString(R.string.barcode_invalid_format));
                        }
                    } else {
                        logError("Barcode scan returned null or empty result", null);
                        showError(getSafeString(R.string.barcode_scan_failed));
                    }
                })
                .addOnCanceledListener(() -> {
                    isScanning = false;
                    logDebug("User canceled barcode scanning");
                    finish();
                })
                .addOnFailureListener(e -> {
                    isScanning = false;
                    logError("Barcode scanning failed", e);

                    // Provide user-friendly error messages
                    String errorMessage;
                    if (e.getMessage() != null && e.getMessage().contains("camera")) {
                        errorMessage = getSafeString(R.string.scanner_camera_error);
                    } else if (e.getMessage() != null && e.getMessage().contains("permission")) {
                        errorMessage = getSafeString(R.string.scanner_permission_denied);
                    } else {
                        errorMessage = getSafeString(R.string.scanning_failed, e.getMessage());
                    }

                    showError(errorMessage);
                });
    }

    /**
     * Validate that scanned barcode is likely a valid product barcode
     */
    private boolean isValidProductBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        // Remove any whitespace
        barcode = barcode.trim();

        // Check length constraints
        int length = barcode.length();
        if (length < 8 || length > 14) {
            logDebug("Barcode length invalid: " + length + " (expected 8-14 digits)");
            return false;
        }

        // Check if it's all digits (for EAN/UPC codes)
        if (barcode.matches("\\d+")) {
            return true;
        }

        // Allow QR codes with URLs (for some products)
        if (barcode.startsWith("http")) {
            return true;
        }

        // Log and reject other formats for now
        logDebug("Barcode format not recognized as product code: " + barcode);
        return false;
    }

    /**
     * Handle successful barcode scan
     */
    private void onBarcodeScanned(String barcode) {
        logDebug("Successfully scanned barcode: " + barcode);

        // Show brief success feedback
        Toast.makeText(this, getSafeString(R.string.barcode_scanned_success), Toast.LENGTH_SHORT).show();

        // Navigate to product details with scanned barcode
        Intent intent = new Intent(this, ItemDetailsActivity.class);
        intent.putExtra(ItemDetailsActivity.EXTRA_FOOD_ITEM, barcode);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clear scanner from back stack

        startActivity(intent);
        finish();
    }

    /**
     * Show error message and close scanner
     */
    private void showError(String message) {
        logError("Scanner error: " + message, null);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Give user a moment to read the error message
        findViewById(android.R.id.content).postDelayed(this::finish, 2000);
    }

    /**
     * Retry scanning after error (if user wants to try again)
     */
    private void retryScanning() {
        if (!isScanning) {
            logDebug("Retrying barcode scanning");
            startScanning();
        }
    }

    // ========== LIFECYCLE MANAGEMENT ==========

    @Override
    protected void onResume() {
        super.onResume();

        // If we're not currently scanning and the scanner is set up, start scanning
        // This handles cases where the user comes back from settings or camera permissions
        if (!isScanning && scanner != null) {
            logDebug("Activity resumed - restarting scanner if needed");
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Cancel scanning when activity goes to background
        if (isScanning) {
            logDebug("Activity paused - stopping scanner");
            isScanning = false;
            // Note: ML Kit scanner will handle cleanup automatically
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up scanner resources
        if (scanner != null) {
            scanner = null;
            logDebug("Scanner resources cleaned up");
        }
    }

    @Override
    public void onBackPressed() {
        // Log user exit
        logDebug("User exited scanner via back button");
        super.onBackPressed();
    }

    // ========== ACTIVITY RESULT HANDLING ==========

    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();

        if (!isScanning && scanner != null) {
            logDebug("Activity resumed - restarting scanner if needed");
            startScanning();
        }
    }

    // ========== MANUAL BARCODE INPUT (Future Enhancement) ==========

    /**
     * Future enhancement: Allow manual barcode entry
     * This method shows how you could add a manual input option
     */
    private void showManualBarcodeInput() {
        // This could show a dialog for manual barcode entry
        // For now, just log that the feature is planned
        logDebug("Manual barcode input requested - feature planned for future release");
        Toast.makeText(this, getSafeString(R.string.coming_soon), Toast.LENGTH_SHORT).show();
    }

    // ========== DEBUG AND TESTING METHODS ==========

    /**
     * For testing: simulate a successful scan
     */
    private void simulateTestScan(String testBarcode) {
        if (testBarcode != null && isValidProductBarcode(testBarcode)) {
            logDebug("Simulating test scan: " + testBarcode);
            onBarcodeScanned(testBarcode);
        } else {
            logDebug("Invalid test barcode provided: " + testBarcode);
        }
    }

    /**
     * Get scanner status for debugging
     */
    public boolean isScannerActive() {
        return isScanning;
    }

    /**
     * Get supported barcode formats for debugging
     */
    public String getSupportedFormats() {
        return "EAN-13, EAN-8, UPC-A, UPC-E, Code-128, Code-39, QR Code";
    }
}