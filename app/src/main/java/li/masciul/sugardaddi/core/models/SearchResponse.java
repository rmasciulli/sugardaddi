package li.masciul.sugardaddi.core.models;

import java.util.List;
import java.util.ArrayList;

/**
 * SearchResponse - Domain model for search results
 *
 * This is the clean domain model used throughout the app.
 * It's independent of any specific API or data source.
 */
public class SearchResponse {

    private int totalCount;
    private int currentPage;
    private int pageSize;
    private int totalPages;
    private List<FoodProduct> products;
    private String query;
    private String language;
    private String dataSource;
    private long searchTimestamp;

    // ========== CONSTRUCTORS ==========

    public SearchResponse() {
        this.products = new ArrayList<>();
        this.searchTimestamp = System.currentTimeMillis();
    }

    public SearchResponse(List<FoodProduct> products, int totalCount) {
        this();
        this.products = products != null ? products : new ArrayList<>();
        this.totalCount = totalCount;
    }

    // ========== GETTERS AND SETTERS ==========

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public List<FoodProduct> getProducts() {
        return products != null ? products : new ArrayList<>();
    }
    public void setProducts(List<FoodProduct> products) {
        this.products = products != null ? products : new ArrayList<>();
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public long getSearchTimestamp() { return searchTimestamp; }
    public void setSearchTimestamp(long searchTimestamp) { this.searchTimestamp = searchTimestamp; }

    // ========== HELPER METHODS ==========

    public boolean hasResults() {
        return products != null && !products.isEmpty();
    }

    public boolean hasMorePages() {
        if (totalPages > 0) {
            return currentPage < totalPages;
        }
        // Calculate if we don't have totalPages
        if (pageSize > 0 && totalCount > 0) {
            int calculatedTotalPages = (int) Math.ceil((double) totalCount / pageSize);
            return currentPage < calculatedTotalPages;
        }
        return false;
    }

    public int getResultCount() {
        return products != null ? products.size() : 0;
    }

    public void addProduct(FoodProduct product) {
        if (product != null) {
            if (products == null) {
                products = new ArrayList<>();
            }
            products.add(product);
        }
    }

    public void addProducts(List<FoodProduct> newProducts) {
        if (newProducts != null && !newProducts.isEmpty()) {
            if (products == null) {
                products = new ArrayList<>();
            }
            products.addAll(newProducts);
        }
    }

    public void clear() {
        if (products != null) {
            products.clear();
        }
        totalCount = 0;
        currentPage = 0;
    }
}