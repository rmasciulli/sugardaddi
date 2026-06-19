package li.masciul.sugardaddi.business.search;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import li.masciul.sugardaddi.core.enums.ProductType;

/**
 * SearchFilter - Immutable value object carrying the user's active search filters.
 *
 * OWNED BY: SearchManager - holds a single instance, updated via setFilters().
 * CONSUMED BY: DataSourceAggregator.searchAll() - intersects the active source list.
 *
 * TWO FILTER DIMENSIONS
 * =====================
 * 1. allowedTypes   - Set<ProductType> the user wants to see in results.
 *                     Empty set = no type filter active (all types shown).
 *
 * 2. allowedSources - Set<String> of source IDs the user has selected.
 *                     Empty set = no source filter active (all sources searched).
 *
 * EMPTY SET = NO FILTER (not "block everything")
 * ===============================================
 * This is the key convention: an empty set means the dimension is unrestricted,
 * not that nothing is allowed. This makes the default state trivial:
 *
 *   SearchFilter.noFilter() → both sets empty → search everything
 *
 * Use isTypeFilterActive() and isSourceFilterActive() to distinguish
 * "no filter" from an actively selected subset.
 *
 * IMMUTABILITY
 * ============
 * Defensive copies are made on construction. The caller cannot mutate a
 * SearchFilter after handing it to SearchManager. Thread-safe by design.
 *
 * USAGE EXAMPLES
 * ==============
 * // No filter - default state
 * SearchFilter f = SearchFilter.noFilter();
 *
 * // Food only, all sources
 * Set<ProductType> types = new HashSet<>();
 * types.add(ProductType.FOOD);
 * SearchFilter f = new SearchFilter(types, Collections.emptySet());
 *
 * // All types, OFF and Ciqual only
 * Set<String> sources = new HashSet<>();
 * sources.add("OPENFOODFACTS");
 * sources.add("CIQUAL");
 * SearchFilter f = new SearchFilter(Collections.emptySet(), sources);
 */
public final class SearchFilter {

    /** Shared singleton for the "no filter" state - avoids allocation on every reset. */
    private static final SearchFilter NO_FILTER =
            new SearchFilter(Collections.emptySet(), Collections.emptySet());

    // =========================================================================
    // FIELDS - immutable after construction
    // =========================================================================

    /**
     * ProductTypes the user wants to see.
     * Empty = no restriction (all types shown).
     */
    @NonNull
    private final Set<ProductType> allowedTypes;

    /**
     * Source IDs the user has selected.
     * Empty = no restriction (all active sources searched).
     */
    @NonNull
    private final Set<String> allowedSources;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * @param allowedTypes   ProductTypes to include. Pass empty set for "all types".
     * @param allowedSources Source IDs to include. Pass empty set for "all sources".
     */
    public SearchFilter(@NonNull Set<ProductType> allowedTypes,
                        @NonNull Set<String> allowedSources) {
        // Defensive copies - caller cannot mutate this object after construction
        this.allowedTypes   = Collections.unmodifiableSet(new HashSet<>(allowedTypes));
        this.allowedSources = Collections.unmodifiableSet(new HashSet<>(allowedSources));
    }

    /**
     * Returns the shared "no filter" singleton - search all types, all sources.
     */
    @NonNull
    public static SearchFilter noFilter() {
        return NO_FILTER;
    }

    // =========================================================================
    // FILTER LOGIC - used by DataSourceAggregator
    // =========================================================================

    /**
     * Returns true if a given source should be included in the search.
     *
     * A source is included when:
     * 1. Source filter is inactive OR the source ID is in allowedSources.
     * 2. Type filter is inactive OR the source produces at least one allowed type.
     *
     * Both conditions must pass. If the type filter is active and the source
     * produces none of the allowed types, it is skipped - no network call is made.
     *
     * @param sourceId      The source's stable ID (e.g. "OPENFOODFACTS")
     * @param producedTypes What this source produces (from DataSource.getProducedTypes())
     * @return True if this source should be searched
     */
    public boolean allowsSource(@NonNull String sourceId,
                                @NonNull Set<ProductType> producedTypes) {
        // Check source ID filter
        if (isSourceFilterActive() && !allowedSources.contains(sourceId)) {
            return false;
        }

        // Check type filter - source must produce at least one type the user wants
        if (isTypeFilterActive()) {
            for (ProductType type : producedTypes) {
                if (allowedTypes.contains(type)) return true;
            }
            return false;
        }

        return true;
    }

    // =========================================================================
    // STATE CHECKS
    // =========================================================================

    /** True if the user has restricted the item types shown. */
    public boolean isTypeFilterActive() {
        return !allowedTypes.isEmpty();
    }

    /** True if the user has restricted which sources are searched. */
    public boolean isSourceFilterActive() {
        return !allowedSources.isEmpty();
    }

    /** True if no filter is active - equivalent to SearchFilter.noFilter(). */
    public boolean isNoFilter() {
        return !isTypeFilterActive() && !isSourceFilterActive();
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    /** @return Immutable set of allowed ProductTypes. Empty = all types allowed. */
    @NonNull
    public Set<ProductType> getAllowedTypes() {
        return allowedTypes;
    }

    /** @return Immutable set of allowed source IDs. Empty = all sources allowed. */
    @NonNull
    public Set<String> getAllowedSources() {
        return allowedSources;
    }

    // =========================================================================
    // OBJECT IDENTITY
    // =========================================================================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SearchFilter)) return false;
        SearchFilter other = (SearchFilter) obj;
        return allowedTypes.equals(other.allowedTypes)
                && allowedSources.equals(other.allowedSources);
    }

    @Override
    public int hashCode() {
        return 31 * allowedTypes.hashCode() + allowedSources.hashCode();
    }

    @Override
    public String toString() {
        return "SearchFilter{types=" + allowedTypes + ", sources=" + allowedSources + "}";
    }
}