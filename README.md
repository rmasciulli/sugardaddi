# Sugardaddi

**An open-source project designed to help people with diabetes.**

Today, app stores are full of nutrition apps. The problem is that nearly all of them are designed for athletes or people looking to lose weight. Few apps are truly designed for people with diabetes, and even when you manage to find one, they often run into the same limitations:
- the available information is limited
- the food or meal database isn’t kept up to date for free
- the app requires an internet connection
- the information is inaccurate or approximate (especially for AI-based apps)
- usage fees require a subscription

That’s why I decided to work on sugardaddi. A free, open-source app to effectively and easily track what you consume. A modular system where anyone can easily add their own data sources, whether free or paid. By default, the project already includes three: Ciqual (ANSES), Open Food Facts, and USDA FoodData Central. Regarding the latter, be sure to register and obtain an API key on their official website to take full advantage of it.

A nutrition tracking app for Android that aggregates food data from multiple scientific and collaborative databases. Search products, browse detailed nutritional information, track meals in a daily journal, and consult EU-compliant nutrition labels - all with offline local data support.

The project is still in alpha (core architecture complete), it's actively developed.

## Screenshots

*Coming soon.*

<!-- Add screenshots here once UI is stable:
![Search](docs/screenshots/search.png)
![Product detail](docs/screenshots/detail_off.png)
![Ciqual detail](docs/screenshots/detail_ciqual.png)
![Journal](docs/screenshots/journal.png)
![Settings](docs/screenshots/settings.png)
-->

## Key design patterns

- **Unified food model** - all sources map to the same *FoodProduct{}* + *Nutrition{}* domain objects. Source-specific quirks are handled in each source's mapper, not in the UI.
- **Aggregator** - *DataSourceAggregator{}* fans out queries to all active sources in parallel and merges results via *SmartMergeStrategy{}*, with per-source scoring and diversity enforcement.
- **Delegate pattern** - *ItemViewDelegate{}* and *DetailRenderer{}* interfaces allow each data source to define its own search card and detail screen layouts independently.
- **Hybrid translation** - primary content stored in the language received; *ProductTranslation{}* maps hold other languages. Default language is English.
- **Generic settings cards** - *DataSourceCardManager{}* + *SettingsProvider{}* interface drive the settings screen. Each source declares its own credentials, local DB state, and broadcast actions. Adding a new source requires zero changes to *SettingsActivity{}*.

## Architecture overview

The codebase is organised around a strict separation of concerns. It's designed to make adding new data sources or UI components straightforward without having to touch the existing logic.

```
app/src/main/java/li/masciul/sugardaddi/
│
├── core/                        # Domain layer - no Android dependencies
│   ├── models/                  # Unified domain models (FoodProduct, Nutrition, Meal…)
│   ├── enums/                   # DataSource, MealType, Unit, NutritionLabelMode…
│   ├── interfaces/              # Searchable, Nutritional, Categorizable, AllergenAware
│   ├── scoring/                 # Source-specific scorers (BaseScorer, OFF, Ciqual, USDA…)
│   └── utils/                   # SearchFilter, DiversityStrategy, AllergenUtils…
│
├── data/
│   ├── database/                # Room DB v7 - food_products, nutrition, meals, recipes
│   │   ├── dao/                 # FoodProductDao, NutritionDao, MealDao, RecipeDao…
│   │   ├── entities/            # Room entities with typed converters
│   │   └── relations/           # FoodProductWithNutrition, MealWithNutrition…
│   ├── sources/                 # One package per data source
│   │   ├── base/                # BaseDataSource, DataSourceCallback, SettingsProvider
│   │   ├── aggregation/         # DataSourceAggregator, SmartMergeStrategy
│   │   ├── ciqual/              # Ciqual ES API + local XML import pipeline
│   │   ├── openfoodfacts/       # SearchAlicious + OFF v2 API
│   │   └── usda/                # USDA FoodData Central REST API + optional local import
│   ├── network/                 # OkHttp/Retrofit client, logging interceptor
│   └── repository/              # ProductRepository, MealRepository
│
├── ui/
│   ├── activities/              # MainActivity, ItemDetailsActivity, JournalActivity…
│   ├── delegates/
│   │   ├── search/              # Per-source search card delegates (OFF, Ciqual, USDA, Default)
│   │   └── detail/              # Per-source detail renderers (OFF, Ciqual, USDA, Default)
│   ├── components/              # NutritionLabelManager, AllergenIconHelper, NutrientBannerView
│   ├── settings/                # DataSourceCardManager (generic settings card per source)
│   └── adapters/                # SearchResultsAdapter, TimelineAdapter, MealPortionsAdapter
│
├── managers/                    # DataSourceManager, LanguageManager, ThemeManager
└── utils/                       # CategoryCleaner, ScoreOverlayHelper
```

## Data sources

**Open Food Facts**

- **Type:** Collaborative, crowd-sourced
- **Search:** SearchAlicious Elasticsearch endpoint (fast, relevance-scored)
- **Detail:** Open Food Facts API v2 (`/api/v2/product/{barcode}.json`)
- **Coverage:** 3M+ products worldwide, product images, NutriScore, EcoScore, NOVA group, allergens
- **Attribution:** [OpenFoodFacts.org](https://world.openfoodfacts.org) - Open Database Licence (ODbL)

**Ciqual 2025 (ANSES)**

- **Type:** Scientific reference, French national food composition table
- **Search:** Ciqual Elasticsearch API (live) + local Room DB (offline, after import)
- **Local import:** `alim_grp_2025_11_03.xml` (80KB, bundled), `alim_2025_11_03.xml` (1.6MB, bundled), `compo_2025_11_03.xml` (69MB, downloaded at first launch from Zenodo)
- **Coverage:** 3,484 foods, 65+ nutrients per food, full EU mineral/vitamin panel
- **Attribution:** [ANSES Ciqual](https://ciqual.anses.fr) - Etalab Open Licence - DOI [10.5281/zenodo.17550133](https://doi.org/10.5281/zenodo.17550133)

**USDA FoodData Central**
- **Type:** Scientific reference, US Department of Agriculture
- **Search:** FDC REST API v1 (`POST /foods/search`) - Foundation Foods, SR Legacy, Survey (FNDDS)
- **Detail:** FDC REST API v1 (`GET /food/{fdcId}?format=full`)
- **Local import:** Optional - Foundation Foods JSON (~467KB zipped) + SR Legacy JSON (~12MB zipped), downloaded from fdc.nal.usda.gov and imported via *USDAImportService{}* (user-initiated from Settings)
- **Coverage:** ~16,000 foods across three data types: Foundation (~1,200 raw agricultural commodities with exhaustive nutrient profiles), SR Legacy (~7,700 generic foods), Survey/FNDDS (~7,300 dietary survey foods)
- **API key:** Free key required - register at [fdc.nal.usda.gov/api-key-signup](https://fdc.nal.usda.gov/api-key-signup/) then add to `local.properties` as `USDA_API_KEY=your_key`. Falls back to `DEMO_KEY` (30 req/hour per IP).
- **Attribution:** [USDA FoodData Central](https://fdc.nal.usda.gov) - public domain (CC0 1.0)

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
- `food.csv`: USDA food descriptions (future)
- `nutrient.csv`: USDA nutrient definitions (future)

Datasets NOT committed (downloaded at runtime):
- `compo_*.xml`: Ciqual composition data (69MB)
  Source: https://doi.org/10.5281/zenodo.17550133
- `food_nutrient.csv`: USDA composition data (450MB)
  Source: https://fdc.nal.usda.gov/download-foods.html

The app handles missing dataset files automatically on first launch.

## Roadmap

- [ ] **Favorites** - persistent favorites with dedicated screen
- [ ] **Meal photo capture** - attach photos to journal meals
- [ ] **Category comparison** - compare a product against its Ciqual category average

## Licence

MIT - see [LICENSE](LICENSE).
