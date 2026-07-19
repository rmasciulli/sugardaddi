# Sugardaddi

A nutrition tracking app for Android that aggregates food and recipe data from multiple scientific and collaborative databases. Search products and recipes, browse detailed nutritional information, track meals in a daily journal, and consult EU-compliant nutrition labels - all with offline local data support.

**An open-source project designed to help people with diabetes.**

Today, application stores are full of nutrition apps. The problem is that nearly all of them are designed for athletes or people looking to lose weight. Few apps are truly designed for people with diabetes, and even when you manage to find one, they often run into the same limitations:
- the available information is limited
- the food or meal database isn't kept up to date for free
- the app requires an internet connection
- the information is inaccurate or approximate (especially for AI-based apps)
- usage fees require a subscription

That's why I decided to work on sugardaddi. A free, open-source app to effectively and easily track what you consume. A modular system where anyone can easily add their own data sources, whether free or paid. By default, the project already includes Ciqual (ANSES), Open Food Facts, USDA FoodData Central, TheMealDB, TheCocktailDB, and FatSecret Platform (recipes and branded/generic foods). For USDA, be sure to register and obtain an API key on their official website to take full advantage of it. FatSecret requires a self-hosted proxy server and isn't available out of the box for other contributors - see the Data Sources section below.

The project is still in alpha (core architecture complete), it's actively developed.

## Screenshots

<p align="center">
  <img width="160" alt="2026-05-25T02-53-00" src="https://github.com/user-attachments/assets/e30cd130-df7f-4f67-a674-b340ac261252" />
  <img width="160" alt="2026-05-25T03-14-00" src="https://github.com/user-attachments/assets/a340dd9d-0fe6-4704-a40e-b0b0a1e37749" />
  <img width="160" alt="2026-05-25T02-59-00" src="https://github.com/user-attachments/assets/c2a48c28-fef9-46f0-8c67-2b83e9cd68a6" />
  <img width="160" alt="2026-05-25T03-30-00" src="https://github.com/user-attachments/assets/32eb1d22-447e-4228-b6a2-1fd3c886c92b" />
  <img width="160" alt="2026-05-25T03-22-00" src="https://github.com/user-attachments/assets/b1c5e5ad-c876-491c-8d08-c9566d6b8682" />
</p>

## Key design patterns

- **Unified food model** - all food sources map to the same `FoodProduct{}` + `Nutrition{}` domain objects. All recipe sources map to the same `Recipe{}` + `RecipeStep{}` domain objects. Source-specific quirks are handled in each source's mapper, not in the UI.
- **Aggregator** - `DataSourceAggregator{}` fans out queries to all active, non-filtered sources in parallel and merges results via `SmartMergeStrategy{}`, with per-source scoring and diversity enforcement.
- **Multi-source filtering** - `SearchFilter{}` is an immutable value object carrying two filter dimensions: allowed item types (`ProductType`) and allowed source IDs. `DataSourceAggregator.searchAll()` intersects the active source list against the filter before firing any network call - excluded sources are never contacted. Each `DataSource{}` declares what it produces via `getProducedTypes()`, allowing type-based exclusion at the same level. Filter state is owned by `SearchManager{}` and survives pagination.
- **Delegate pattern** - `ItemViewDelegate{}` and `DetailRenderer{}` interfaces allow each data source to define its own search card and detail screen layouts independently. Food sources (OFF, Ciqual, USDA) and recipe sources (TheMealDB, TheCocktailDB) share the same pipeline but render through source-specific delegates, with `DefaultProductSearchDelegate{}` and `DefaultRecipeSearchDelegate{}` as catch-all fallbacks.
- **Hybrid translation** - primary content stored in the language received; `ProductTranslation{}` and `RecipeTranslation{}` maps hold other languages. Default language is English.
- **Generic settings cards** - `DataSourceCardManager{}` + `ManagementProvider{}` interface drive the settings screen. Each source declares its own credentials, local DB state, and broadcast actions. Adding a new source requires zero changes to `DataSourcesActivity{}`.
- **Multi-type sources** - most sources produce exactly one `ProductType` (food or recipe), declared statically via `getProducedTypes()`. A source producing both (currently only FatSecret, via its own recipe and food databases) instead receives a `requestedTypes` parameter on `DataSource.search()` - `DataSourceAggregator` intersects the source's own produced types with the active filter, so a "recipes only" search never triggers a wasted food-endpoint call. Kept as one `DataSource{}` registration rather than split by type, since a settings card should represent one account, not one capability.
- **Persistent image storage** - `ImageProfile{}` is the single source of truth for size/quality presets (thumbnail vs. hero). `ImageDownloader{}` uses a caller-owns-path pattern with temp-then-rename writes, so a partially-downloaded file is never mistaken for a complete one. `ImageStorageManager{}` resolves on-disk destinations (auto-cached vs. user-provided images kept separate). Favoriting an item triggers `cacheFavoriteImages{}`, which downloads and persists both a thumbnail and a full-resolution hero for guaranteed offline access - healed automatically on next open if a file is ever missing. `CacheEvictionManager{}` (Room rows) and `ImagePurgeManager{}` (orphan files, via a union of every path field that must survive eviction) keep storage bounded without ever discarding a favorite's images.
- **Full-image viewer** - `ImageViewerLauncher{}` + `ImageViewerActivity{}` (PhotoView-based pan/zoom) open a tappable full-resolution view of any hero image, whether it's a local `File` (already cached) or a remote URL (fetched on demand). Wired into every detail screen and search delegate via a single shared tap-to-expand binding, not duplicated per source.

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
│   │   ├── entities/             # Room entities with typed converters
│   │   └── relations/           # FoodProductWithNutrition, RecipeWithNutrition, MealWithNutrition…
│   ├── sources/                 # One package per data source
│   │   ├── base/                # BaseDataSource, DataSourceCallback, ManagementProvider
│   │   ├── aggregation/         # DataSourceAggregator, SmartMergeStrategy
│   │   ├── ciqual/              # Ciqual ES API + local XML import pipeline
│   │   ├── openfoodfacts/       # SearchAlicious + OFF v2 API
│   │   ├── usda/                # USDA FoodData Central REST API + optional local import
│   │   ├── themealdb/           # TheMealDB REST API - recipe search and detail
│   │   ├── thecocktaildb/       # TheCocktailDB REST API - cocktail search and detail
│   │   ├── fatsecret/           # FatSecret Platform via a private proxy - recipe + food search/detail
│   │   │   ├── api/dto/         # Response DTOs, incl. SingleOrArrayDeserializer for FatSecret's inconsistent list-wrapping
│   │   │   └── mappers/         # FatSecretMapper - per-100g normalization, %DV-vs-absolute unit handling
│   │   └── user/                # User-created content (planned)
│   ├── network/                 # OkHttp/Retrofit client, logging interceptor
│   └── repository/              # ProductRepository, RecipeRepository, MealRepository
│
├── ui/
│   ├── activities/              # MainActivity, ProductDetailsActivity, RecipeDetailsActivity, ImageViewerActivity…
│   ├── delegates/
│   │   ├── search/              # Per-source search card delegates (OFF, Ciqual, USDA, MealDB, CocktailDB, Default)
│   │   └── detail/              # Per-source detail renderers (OFF, Ciqual, USDA, MealDB, CocktailDB, Default)
│   ├── components/              # NutritionLabelManager (generic over Searchable+Nutritional), AllergenIconHelper, NutrientBannerView
│   ├── settings/                # DataSourceCardManager (generic settings card per source)
│   ├── utils/                   # ImageDisplayUtils, ImageViewerLauncher - shared tap-to-expand image binding
│   └── adapters/                # SearchResultsAdapter, TimelineAdapter, MealPortionsAdapter
│
├── managers/                    # DataSourceManager, LanguageManager, ThemeManager
└── utils/
    ├── image/                   # ImageProfile, ImageDownloader, ImageStorageManager, ImagePurgeManager
    ├── cache/                   # CacheEvictionManager
    └── CategoryCleaner, ScoreOverlayHelper, ScoreUtils, …
```

## Data sources

Data sources are ordered by relevance to the primary target audience (EU users with diabetes). Food sources come first; recipe and cocktail sources complement them.

**Ciqual 2025 (ANSES)**

- **Type:** Scientific reference - French national food composition table
- **Search:** Ciqual Elasticsearch API (live) + local Room DB (offline, after import)
- **Assets prepacked**: `alim_grp_2025_11_03.xml` (80KB, bundled), used to improve categorisation
- **Local import:** Optional - `alim_2025_11_03.xml` (1.6MB) and `compo_2025_11_03.xml` (69MB)
- **Coverage:** 3,484 foods, 65+ nutrients per food, full EU mineral/vitamin panel
- **Attribution:** [ANSES Ciqual](https://ciqual.anses.fr) - Etalab Open Licence - DOI [10.5281/zenodo.17550133](https://doi.org/10.5281/zenodo.17550133)

**Open Food Facts**

- **Type:** Collaborative, crowd-sourced
- **Search:** SearchAlicious Elasticsearch endpoint (fast, relevance-scored)
- **Detail:** Open Food Facts API v2 (`/api/v2/product/{barcode}.json`)
- **Coverage:** 3M+ products worldwide, product images, NutriScore, EcoScore, NOVA group, allergens
- **Attribution:** [OpenFoodFacts.org](https://world.openfoodfacts.org) - Open Database Licence (ODbL)

**USDA FoodData Central**

- **Type:** Scientific reference - US Department of Agriculture
- **Search:** FDC REST API v1 (`POST /foods/search`) - Foundation Foods, SR Legacy, Survey (FNDDS)
- **Detail:** FDC REST API v1 (`GET /food/{fdcId}?format=full`)
- **Local import:** Optional - Foundation Foods JSON (~467KB zipped) + SR Legacy JSON (~12MB zipped), downloaded from fdc.nal.usda.gov and imported via `USDAImportService{}` (user-initiated from Settings)
- **Coverage:** ~16,000 foods across three data types: Foundation (~1,200 raw agricultural commodities with exhaustive nutrient profiles), SR Legacy (~7,700 generic foods), Survey/FNDDS (~7,300 dietary survey foods)
- **API key:** Free key required - register at [fdc.nal.usda.gov/api-key-signup](https://fdc.nal.usda.gov/api-key-signup/) then add to `local.properties` as `USDA_API_KEY=your_key`. Falls back to `DEMO_KEY` (30 req/hour per IP).
- **Attribution:** [USDA FoodData Central](https://fdc.nal.usda.gov) - public domain (CC0 1.0)

**TheMealDB**

- **Type:** Open recipe database
- **Search:** TheMealDB API v1 (`search.php?s={query}`) - returns full meal objects
- **Detail:** TheMealDB API v1 (`lookup.php?i={id}`)
- **Coverage:** Structured recipes with ingredients, preparation steps, category, area of origin, and optional YouTube tutorial. No nutrition data provided by this source.
- **API key:** Free development key (`1`) is hardcoded and sufficient for search and lookup. A Patreon key is required for public release. Add to `local.properties` as `THEMEALDB_API_KEY=your_key`.
- **Attribution:** [TheMealDB.com](https://www.themealdb.com) - free tier for open-source projects

**TheCocktailDB**

- **Type:** Open cocktail recipe database - same developer as TheMealDB
- **Search:** TheCocktailDB API v1 (`search.php?s={query}`) - returns full drink objects
- **Detail:** TheCocktailDB API v1 (`lookup.php?i={id}`)
- **Coverage:** Structured cocktail recipes with up to 15 ingredients and measures, category, glass type, alcoholic status, and preparation instructions. No nutrition data provided by this source. Alcoholic status is stored as a searchable tag (`alcoholic`, `non_alcoholic`, `optional_alcohol`).
- **API key:** Free development key (`1`) is hardcoded. A Patreon key is required for public release. Add to `local.properties` as `THECOCKTAILDB_API_KEY=your_key`.
- **Attribution:** [TheCocktailDB.com](https://www.thecocktaildb.com) - free tier for open-source projects

**FatSecret Platform**

- **Type:** Recipe database + branded/generic food database (US)
- **Search:** `recipes/search/v3` and `foods/search/v1` - both confirmed accessible on FatSecret's free Basic tier, no paid tier required for search itself
- **Detail:** `recipe/v2` and `food/v5` - full structured nutrition, normalized to per-100g
- **Coverage:** Curated recipes with ingredients and directions; branded foods (restaurant items, packaged products) and generic foods not otherwise well covered by Ciqual/USDA/OFF
- **Access is different from every other source here:** FatSecret's own terms require OAuth2 tokens to be requested through a server-side proxy - the consumer key/secret can never ship inside a distributed app. This project uses a private proxy, `glucogate` (separate private repository, not part of this project), that holds those credentials; the app only ever talks to the proxy, authenticated with its own separate, low-stakes shared secret (`GLUCOGATE_BASE_URL` / `GLUCOGATE_PROXY_SECRET` in `local.properties`). There is no public fallback the way there is for USDA/TheMealDB/TheCocktailDB - without a proxy of your own, the app builds and runs fine, FatSecret search and recipe/food nutrition are just unavailable, same as any other optional source with no key configured.
- **Attribution:** [FatSecret Platform](https://platform.fatsecret.com) - Premier Free license, non-commercial/open-source use, attribution required per FatSecret's terms

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

Dataset already bundled in the APK (committed to git):
- `alim_grp_*.xml`: Ciqual category hierarchy (80KB), loaded at startup

## Roadmap

- [x] **Nutrition for TheMealDB and TheCocktailDB** - solved differently than originally planned: rather than fuzzy-matching their ingredient stubs against the food databases, FatSecret's recipe/food search+detail endpoints provide this directly for FatSecret's own recipes. TheMealDB/TheCocktailDB ingredients are still unresolved `FoodPortion` stubs, with no fuzzy-matching plan currently active.
- [ ] **Resolve FatSecret recipe ingredients to real FoodProduct entries** - unlike TheMealDB/TheCocktailDB, FatSecret's `recipe/v2` ingredients already carry a real `food_id` per ingredient, making per-ingredient resolution via `food/v5` genuinely feasible (not just a fuzzy match) - not yet implemented.
- [x] **Persistent image storage** - shipped: favorite-time hero/thumbnail caching with heal-on-open, `ImageProfile`/`ImageDownloader`/`ImageStorageManager`/`ImagePurgeManager`, and a full-resolution tap-to-expand viewer (`ImageViewerActivity`) on every detail screen and search card. Meal-journal photo capture and per-step recipe photos are not yet built - the underlying storage system is ready for both, just not wired to those specific UI flows yet.
- [ ] **Category comparison** - compare a product against its Ciqual category average

## Licence

MIT - see [LICENSE](LICENSE).
