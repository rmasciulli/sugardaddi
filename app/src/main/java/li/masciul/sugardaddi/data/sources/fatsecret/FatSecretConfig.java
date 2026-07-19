package li.masciul.sugardaddi.data.sources.fatsecret;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.BuildConfig;
import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * FatSecretConfig - NetworkConfig implementation for FatSecret, routed
 * through glucogate (our own proxy), not FatSecret's servers directly.
 *
 * WHY A PROXY
 * ===========
 * FatSecret's own OAuth2 guide requires token requests to go through a
 * server-side proxy - token issuance is IP-whitelisted and the consumer
 * secret must never ship inside a distributed mobile app. glucogate holds
 * the real FatSecret consumer key/secret; this app only ever talks to
 * glucogate, authenticated with its own, unrelated shared secret.
 *
 * NAMING NOTE
 * ===========
 * BuildConfig fields are named GLUCOGATE_* on purpose - they hold
 * glucogate's own address and shared secret, never FatSecret's real
 * consumer key/secret, which must never appear in this app's
 * local.properties or BuildConfig at all. Keeping the names visually
 * distinct guards against ever pasting the wrong credential into the
 * wrong file.
 *
 * CREDENTIALS
 * ===========
 * Unlike TheMealDB/TheCocktailDB/USDA, there is no public fallback here -
 * if GLUCOGATE_BASE_URL/GLUCOGATE_PROXY_SECRET aren't set in
 * local.properties, every call fails cleanly (see isConfigured()) rather
 * than falling back to something that works. Intentional: anyone building
 * from source without their own glucogate deployment simply doesn't get
 * this feature, instead of silently hitting a shared account they don't
 * control.
 */
public class FatSecretConfig extends NetworkConfig {

    public FatSecretConfig() {
        super("FATSECRET", Environment.PRODUCTION);
        setRetryStrategy(RetryStrategy.EXPONENTIAL);
        addHeader("Authorization", "Bearer " + BuildConfig.GLUCOGATE_PROXY_SECRET);
    }

    @NonNull
    @Override
    protected String getBaseUrl() {
        String base = BuildConfig.GLUCOGATE_BASE_URL;
        if (base == null || base.trim().isEmpty()) {
            // No proxy configured - a syntactically valid but unreachable URL,
            // so Retrofit/NetworkConfig.validate() don't crash at init time.
            // isConfigured() is the real gate; callers check that first.
            return "https://glucogate.invalid/";
        }
        return base.endsWith("/") ? base : base + "/";
    }

    @NonNull
    @Override
    protected String getUserAgent() {
        return "SugarDaddi/1.0 (Android App)";
    }

    /**
     * getBaseUrl() is protected on NetworkConfig (package data.network) -
     * FatSecretDataSource (package data.sources.fatsecret) can't call it
     * directly. This thin public wrapper is the same pattern TheMealDB
     * uses via TheMealDbConstants.buildBaseUrl() - just inlined here since
     * there's no separate constants class for a single-URL source.
     */
    @NonNull
    public String getResolvedBaseUrl() {
        return getBaseUrl();
    }

    /** True if a glucogate deployment is actually configured for this build. */
    public boolean isConfigured() {
        return BuildConfig.GLUCOGATE_BASE_URL != null
                && !BuildConfig.GLUCOGATE_BASE_URL.trim().isEmpty()
                && BuildConfig.GLUCOGATE_PROXY_SECRET != null
                && !BuildConfig.GLUCOGATE_PROXY_SECRET.trim().isEmpty();
    }
}