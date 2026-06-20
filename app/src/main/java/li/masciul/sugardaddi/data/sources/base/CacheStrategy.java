package li.masciul.sugardaddi.data.sources.base;

/**
 * CacheStrategy - per-source cache freshness policy.
 *
 * Deliberately "dumb": it only knows how long a normally-fetched row from this
 * source stays fresh. Row-level overrides (localImport rows never go stale,
 * favourites get a longer floor) are applied by the repository resolvers, which
 * are the ones holding the row.
 */
public final class CacheStrategy {

    /** Default freshness window for sources that don't override (24h). */
    public static final long DEFAULT_STALE_AFTER_MS = 24L * 60 * 60 * 1000;

    private final long staleAfterMs;
    private final boolean neverStale;

    private CacheStrategy(long staleAfterMs, boolean neverStale) {
        this.staleAfterMs = staleAfterMs;
        this.neverStale = neverStale;
    }

    /** Rows from this source are stale once older than {@code millis}. */
    public static CacheStrategy staleAfter(long millis) {
        return new CacheStrategy(millis, false);
    }

    /** Rows from this source never go stale on their own (no auto-refresh). */
    public static CacheStrategy neverStale() {
        return new CacheStrategy(0L, true);
    }

    /** The global default policy (24h). */
    public static CacheStrategy defaultStrategy() {
        return staleAfter(DEFAULT_STALE_AFTER_MS);
    }

    public boolean isNeverStale() { return neverStale; }

    public long getStaleAfterMs() { return staleAfterMs; }
}