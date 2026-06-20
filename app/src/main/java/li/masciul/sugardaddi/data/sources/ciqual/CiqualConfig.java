package li.masciul.sugardaddi.data.sources.ciqual;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.data.network.NetworkConfig;
import li.masciul.sugardaddi.data.network.RetryStrategy;

/**
 * CiqualConfig - network configuration for the Ciqual data source (ANSES).
 *
 * Ciqual is served live through ANSES's Elasticsearch backend, reverse-engineered
 * since ANSES publishes no official API. Search, per-item lookup, and autocomplete
 * all hit that endpoint (see {@link CiqualConstants#ELASTICSEARCH_ENDPOINT}). The
 * full dataset can additionally be imported into Room from Zenodo
 * (see CiqualImportService) for offline/local-first use.
 *
 * @see NetworkConfig
 * @see CiqualDataSource
 * @see CiqualConstants
 */
public class CiqualConfig extends NetworkConfig {

    private static final String BASE_URL = "https://ciqual.anses.fr/";

    private static final String USER_AGENT =
            "SugarDaddi/1.0 (Android App - Ciqual Integration)";

    public CiqualConfig() {
        super(CiqualConstants.SOURCE_ID, Environment.PRODUCTION);
        // Ciqual search is a read-only lookup - retries add latency without benefit.
        // The aggregator latch handles timeouts; let OkHttp fail fast.
        setRetryStrategy(RetryStrategy.NONE);
    }

    @NonNull
    @Override
    protected String getBaseUrl() {
        return BASE_URL;
    }

    @NonNull
    @Override
    protected String getUserAgent() {
        return USER_AGENT;
    }
}