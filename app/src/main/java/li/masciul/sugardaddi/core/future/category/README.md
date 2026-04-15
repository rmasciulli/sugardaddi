# Category Intelligence System (Work in Progress)

## Vision
Create a unified internal category system that:
- Cleans and normalizes OFF's messy user-generated categories
- Loads OFF's official taxonomy (EN + FR)
- Implements LanguaL for multifaceted categorization
- Enables cross-source product comparison
- Suggests better alternatives within categories

## Current Status
- ✅ CategoryMatcher implemented (utils/category/)
- ✅ CategoryTaxonomy loaded
- ✅ CategoryCleaner working
- ✅ TaxonomyManager operational
- ⚠️ CategoryStats: Interface exists, implementation TODO
- ⚠️ CategoryComparison: Complex logic exists, integration TODO
- ⚠️ ProductCategory: Dual-representation ready, mapping incomplete

## Files in This Package
- **ProductCategory.java** - Dual representation (source-native + unified taxonomy)
- **CategoryStats.java** - Statistical analysis for category averages
- **CategoryComparison.java** - Compare products to category benchmarks

## Next Steps (TODO)
1. Complete OFF taxonomy integration
2. Implement LanguaL multifaceted system
3. Build category matching confidence scoring
4. Create category statistics computation
5. Enable product comparison to category averages
6. Build alternative product suggestions

## Integration Points
- Used by: CategoryCapable interface (data/sources/base/)
- Related utils: CategoryMatcher, CategoryCleaner, TaxonomyManager
- UI: ItemDetailsActivity has debug logging for category matching