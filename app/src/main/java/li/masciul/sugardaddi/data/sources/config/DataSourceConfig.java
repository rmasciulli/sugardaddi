package li.masciul.sugardaddi.data.sources.config;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for data sources
 */
public class DataSourceConfig {
    private static final String PREFS_NAME = "datasource_config";
    private static final String KEY_ENABLED_SOURCES = "enabled_sources";
    private static final String KEY_SOURCE_PRIORITY = "source_priority_";

    private final SharedPreferences prefs;

    public DataSourceConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get enabled data sources
     */
    public Set<String> getEnabledSources() {
        Set<String> defaultSources = new HashSet<>();
        defaultSources.add("OPENFOODFACTS"); // OpenFoodFacts always enabled
        defaultSources.add("CIQUAL");

        return prefs.getStringSet(KEY_ENABLED_SOURCES, defaultSources);
    }

    /**
     * Enable or disable a data source
     */
    public void setSourceEnabled(String sourceId, boolean enabled) {
        Set<String> sources = new HashSet<>(getEnabledSources());
        if (enabled) {
            sources.add(sourceId);
        } else {
            sources.remove(sourceId);
        }
        prefs.edit().putStringSet(KEY_ENABLED_SOURCES, sources).apply();
    }

    /**
     * Get priority for a source (higher = preferred)
     */
    public int getSourcePriority(String sourceId) {
        return prefs.getInt(KEY_SOURCE_PRIORITY + sourceId, getDefaultPriority(sourceId));
    }

    /**
     * Set priority for a source
     */
    public void setSourcePriority(String sourceId, int priority) {
        prefs.edit().putInt(KEY_SOURCE_PRIORITY + sourceId, priority).apply();
    }

    private int getDefaultPriority(String sourceId) {
        switch (sourceId) {
            case "OPENFOODFACTS": return 100;   // OpenFoodFacts has highest priority
            case "CIQUAL": return 90;           // Ciqual is second
            default: return 50;
        }
    }
}