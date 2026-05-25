# Sugardaddi

A nutrition tracking app for Android that aggregates food and recipe data from multiple scientific and collaborative databases. Search products and recipes, browse detailed nutritional information, track meals in a daily journal, and consult EU-compliant nutrition labels - all with offline local data support.

**An open-source project designed to help people with diabetes.**

Today, application stores are full of nutrition apps. The problem is that nearly all of them are designed for athletes or people looking to lose weight. Few apps are truly designed for people with diabetes, and even when you manage to find one, they often run into the same limitations:
- the available information is limited
- the food or meal database isn't kept up to date for free
- the app requires an internet connection
- the information is inaccurate or approximate (especially for AI-based apps)
- usage fees require a subscription

That's why I decided to work on sugardaddi. A free, open-source app to effectively and easily track what you consume. A modular system where anyone can easily add their own data sources, whether free or paid. By default, the project already includes Ciqual (ANSES), Open Food Facts, USDA FoodData Central, TheMealDB, and TheCocktailDB. For USDA, be sure to register and obtain an API key on their official website to take full advantage of it.

The project is still in alpha (core architecture complete), it's actively developed.

## Screenshots

<p align="center">
  <img width="180" alt="2026-05-25T02-53-00" src="https://github.com/user-attachments/assets/e30cd130-df7f-4f67-a674-b340ac261252" />
  <img width="180" alt="2026-05-25T03-14-00" src="https://github.com/user-attachments/assets/a340dd9d-0fe6-4704-a40e-b0b0a1e37749" />
  <img width="180" alt="2026-05-25T02-59-00" src="https://github.com/user-attachments/assets/c2a48c28-fef9-46f0-8c67-2b83e9cd68a6" />
  <img width="180" alt="2026-05-25T03-30-00" src="https://github.com/user-attachments/assets/32eb1d22-447e-4228-b6a2-1fd3c886c92b" />
  <img width="180" alt="2026-05-25T03-22-00" src="https://github.com/user-attachments/assets/b1c5e5ad-c876-491c-8d08-c9566d6b8682" />
</p>

## Key design patterns

- **Unified food model** - all food sources map to the same `FoodProduct{}` + `Nutrition{}` domain objects. All recipe sources map to the same `Recipe{}` + `RecipeStep{}` domain objects. Source-specific quirks are handled in each source's mapper, not in the UI.
- **Aggregator** - `DataSourceAggregator{}` fans out queries to all active, non-filtered sources in parallel and merges results via `SmartMergeStrategy{}`, with per-source scoring and diversity enforcement.
- **Multi-source filtering** - `SearchFilter{}` is an immutable value object carrying two filter dimensions: allowed item types (`ProductType`) and allowed source IDs. `DataSourceAggregator.searchAll()` intersects the active source list against the filter before firing any network call - excluded sources are never contacted. Each `DataSource{}` declares what it produces via `getProducedTypes()`, allowing type-based exclusion at the same level. Filter state is owned by `SearchManager{}` and survives pagination.
- **Delegate pattern** - `ItemViewDelegate{}` and `DetailRenderer{}` interfaces allow each data source to define its own search card and detail screen layouts independently. Food sources (OFF, Ciqual, USDA) and recipe sources (TheMealDB, TheCocktailDB) share the same pipeline but render through source-specific delegates, with `DefaultProductSearchDelegate{}` and `DefaultRecipeSearchDelegate{}` as catch-all fallbacks.
- **Hybrid translation** - primary content stored in the language received; `ProductTranslation{}` and `RecipeTranslation{}` maps hold other languages. Default language is English.
- **Generic settings cards** - `DataSourceCardManager{}` + `SettingsProvider{}` interface drive the settings screen. Each source declares its own credentials, local DB state, and broadcast actions. Adding a new source requires zero changes to `SettingsActivity{}`.

## Architecture overview

The codebase is organised around a strict separation of concerns. It's designed to make adding new data sources or UI components straightforward without having to touch the existing logic.

```
app/src/main/java/li/masciul/sugardaddi/
│
├── business/                    # Business logic layer
│   ├── product/                 # Product-specific business rules
│   └── search/                  # Search orchestration
│       ├── DiversityStrategy    # Diversity enforcement across sources
│       ├── ResultPipeline       # Quality gate + scoring + ranking pipeline
│       ├── SearchCache          # Session cache + Room enrichment
│       ├── SearchFilter         # Immutable filter value object (types + sources)
│       └── SearchManager        # Search lifecycle, pagination, debounce, filter state
│
├── core/                        # Domain layer - no Android dependencies
│   ├── models/                  # FoodProduct, Nutrition, Recipe, RecipeStep, Meal…
│   ├── enums/                   # DataSourceType, ProductType, MealType, Unit…
│   ├── interfaces/              # Searchable, Nutritional, Categorizable, AllergenAware
│   ├── scoring/                 # Source-specific scorers (BaseScorer, OFF, Ciqual, USDA, Recipe…)
│   └── utils/                   # AllergenUtils, EUDietaryReferenceValues, ProductUrlBuilder
│
├── data/
│   ├── database/                # Room DB - food_products, nutrition, meals, recipes
│   │   ├── dao/                 # FoodProductDao, NutritionDao, MealDao, RecipeDao
│   │   ├── entities/            # Room entities with typed converters
│   │   └── relations/           # FoodProductWithNutrition, MealWithNutrition…
│   ├── sources/                 # One package per data source
│   │   ├── base/                # BaseDataSource, DataSourceCallback, SettingsProvider
│   │   ├── aggregation/         # DataSourceAggregator, SmartMergeStrategy
│   │   ├── ciqual/              # Ciqual ES API + local XML import pipeline
│   │   ├── openfoodfacts/       # SearchAlicious + OFF v2 API
│   │   ├── usda/                # USDA FoodData Central REST API + optional local import
│   │   ├── themealdb/           # TheMealDB REST API - recipe search and detail
│   │   ├── thecocktaildb/       # TheCocktailDB REST API - cocktail search and detail
│   │   └── user/                # User-created content (planned)
│   ├── network/                 # OkHttp/Retrofit client, logging interceptor
│   └── repository/              # ProductRepository, RecipeRepository, MealRepository
│
├── ui/
│   ├── activities/              # MainActivity, ProductDetailsActivity, RecipeDetailsActivity…
│   ├── delegates/
│   │   ├── search/              # Per-source search card delegates (OFF, Ciqual, USDA, MealDB, CocktailDB, Default)
│   │   └── detail/              # Per-source detail renderers (OFF, Ciqual, USDA, MealDB, CocktailDB, Default)
│   ├── components/              # NutritionLabelManager, AllergenIconHelper, NutrientBannerView
│   ├── settings/                # DataSourceCardManager (generic settings card per source)
│   └── adapters/                # SearchResultsAdapter, TimelineAdapter, MealPortionsAdapter
│
├── managers/                    # DataSourceManager, LanguageManager, ThemeManager
└── utils/                       # CategoryCleaner, ScoreOverlayHelper, ScoreUtils
```

## Data sources

Data sources are ordered by relevance to the primary target audience (EU users with diabetes). Food sources come first; recipe and cocktail sources complement them.

---

**Ciqual 2025 (ANSES)**

- **Type:** Scientific reference - French national food composition table
- **Search:** Ciqual Elasticsearch API (live) + local Room DB (offline, after import)
- **Local import:** `alim_grp_2025_11_03.xml` (80KB, bundled), `alim_2025_11_03.xml` (1.6MB, bundled), `compo_2025_11_03.xml` (69MB, downloaded at first launch from Zenodo)
- **Coverage:** 3,484 foods, 65+ nutrients per food, full EU mineral/vitamin panel
- **Attribution:** [ANSES Ciqual](https://ciqual.anses.fr) - Etalab Open Licence - DOI [10.5281/zenodo.17550133](https://doi.org/10.5281/zenodo.17550133)

---

**Open Food Facts**

- **Type:** Collaborative, crowd-sourced
- **Search:** SearchAlicious Elasticsearch endpoint (fast, relevance-scored)
- **Detail:** Open Food Facts API v2 (`/api/v2/product/{barcode}.json`)
- **Coverage:** 3M+ products worldwide, product images, NutriScore, EcoScore, NOVA group, allergens
- **Attribution:** [OpenFoodFacts.org](https://world.openfoodfacts.org) - Open Database Licence (ODbL)

---

**USDA FoodData Central**

- **Type:** Scientific reference - US Department of Agriculture
- **Search:** FDC REST API v1 (`POST /foods/search`) - Foundation Foods, SR Legacy, Survey (FNDDS)
- **Detail:** FDC REST API v1 (`GET /food/{fdcId}?format=full`)
- **Local import:** Optional - Foundation Foods JSON (~467KB zipped) + SR Legacy JSON (~12MB zipped), downloaded from fdc.nal.usda.gov and imported via `USDAImportService{}` (user-initiated from Settings)
- **Coverage:** ~16,000 foods across three data types: Foundation (~1,200 raw agricultural commodities with exhaustive nutrient profiles), SR Legacy (~7,700 generic foods), Survey/FNDDS (~7,300 dietary survey foods)
- **API key:** Free key required - register at [fdc.nal.usda.gov/api-key-signup](https://fdc.nal.usda.gov/api-key-signup/) then add to `local.properties` as `USDA_API_KEY=your_key`. Falls back to `DEMO_KEY` (30 req/hour per IP).
- **Attribution:** [USDA FoodData Central](https://fdc.nal.usda.gov) - public domain (CC0 1.0)

---

**TheMealDB**

- **Type:** Open recipe database
- **Search:** TheMealDB API v1 (`search.php?s={query}`) - returns full meal objects
- **Detail:** TheMealDB API v1 (`lookup.php?i={id}`)
- **Coverage:** Structured recipes with ingredients, preparation steps, category, area of origin, and optional YouTube tutorial. No nutrition data provided by this source.
- **API key:** Free development key (`1`) is hardcoded and sufficient for search and lookup. A Patreon key is required for public release. Add to `local.properties` as `THEMEALDB_API_KEY=your_key`.
- **Attribution:** [TheMealDB.com](https://www.themealdb.com) - free tier for open-source projects

---

**TheCocktailDB**

- **Type:** Open cocktail recipe database - same developer as TheMealDB
- **Search:** TheCocktailDB API v1 (`search.php?s={query}`) - returns full drink objects
- **Detail:** TheCocktailDB API v1 (`lookup.php?i={id}`)
- **Coverage:** Structured cocktail recipes with up to 15 ingredients and measures, category, glass type, alcoholic status, and preparation instructions. No nutrition data provided by this source. Alcoholic status is stored as a searchable tag (`alcoholic`, `non_alcoholic`, `optional_alcohol`).
- **API key:** Free development key (`1`) is hardcoded. A Patreon key is required for public release. Add to `local.properties` as `THECOCKTAILDB_API_KEY=your_key`.
- **Attribution:** [TheCocktailDB.com](https://www.thecocktaildb.com) - free tier for open-source projects

---

## Getting started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 34+
- Java 17
- Min SDK: 26 (Android 8.0)

### Build

```bash
git clone https://github.com/rmasciulli/sugardaddi.git
cd sugardaddi
# Open in Android Studio and build, or:
./gradlew assembleDebug
```

### Assets

Datasets already bundled in the APK (committed to git):
- `alim_grp_*.xml`: Ciqual category hierarchy (80KB), loaded at startup
- `alim_*.xml`: Ciqual food list (1.6MB), imported on first launch

Datasets NOT committed (downloaded at runtime):
- `compo_*.xml`: Ciqual composition data (69MB)
  Source: https://doi.org/10.5281/zenodo.17550133
- `food_nutrient.csv`: USDA composition data (450MB)
  Source: https://fdc.nal.usda.gov/download-foods.html

The app handles missing dataset files automatically on first launch.

## Roadmap

- [ ] **Nutrition for TheMealDB and TheCocktailDB** - recipe and cocktail ingredients are currently stored as unresolved `FoodPortion` stubs. The next step is to map them to real `FoodProduct` entries via fuzzy matching against the food databases, enabling nutritional computation for recipes and cocktails. This includes a confidence threshold, manual override UX, and Room persistence of confirmed mappings.
- [ ] **Persistent image storage** - three related use cases: keep favourite item images available offline, allow users to attach photos to meal journal entries, and allow photos to be attached to individual recipe steps. Requires `FileProvider` for camera intent, a disk storage strategy, and Room schema updates to reference image paths.
- [ ] **Category comparison** - compare a product against its Ciqual category average

## Licence

MIT - see [LICENSE](LICENSE).
