package li.masciul.sugardaddi.core.models;

/**
 * Category - Universal category model for all types
 */
public class Category {

    private String name;
    private String id;                  // Unique identifier
    private String description;
    private String iconUrl;
    private String colorHex;           // UI color
    private String parentId;           // For hierarchical categories
    private int level = 0;             // Hierarchy level (0 = root)
    private int sortOrder = 0;         // Display order

    // ========== CONSTRUCTORS ==========

    public Category() {}

    public Category(String name) {
        this.name = name;
        this.id = generateId(name);
    }

    public Category(String name, String id) {
        this.name = name;
        this.id = id;
    }

    // ========== BASIC PROPERTIES ==========

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    // ========== UTILITY METHODS ==========

    public boolean isRoot() {
        return parentId == null || parentId.trim().isEmpty();
    }

    private String generateId(String name) {
        if (name == null) return "category_unknown";
        return "category_" + name.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Category category = (Category) obj;
        return java.util.Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }

    @Override
    public String toString() {
        return name != null ? name : "Unknown Category";
    }
}