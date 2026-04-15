# sugardaddi

An open-source Android nutrition tracking app aggregating food data from multiple scientific and collaborative databases. Search products, browse detailed nutritional information, track meals in a daily journal, and consult EU-compliant nutrition labels — all with offline-first local data support.

> **Status:** Alpha — core architecture complete, actively developed.

---

## Architecture overview

The codebase is organised around a strict separation of concerns, designed to make adding new data sources or UI components straightforward without touching existing logic.

```
app/src/main/java/li/masciul/sugardaddi/
│
├── core/                        # Domain layer — no Android dependencies
│   ├── models/                  # Unified domain models (FoodProduct, Nutrition, Meal…)
│   ├── enums/                   # DataSource, MealType, Unit, NutritionLabelMode…
│   ├── interfaces/              # Searchable, Nutritional, Categorizable, AllergenAware
│   └── utils/                   # Scoring, allergen utilities, EU dietary reference values
│
├── data/
│   ├── database/                # Room DB v7 — food_products, nutrition, meals, recipes
│   │   ├── dao/                 # FoodProductDao, NutritionDao, MealDao, RecipeDao…
│   │   ├── entities/            # Room entities with typed converters
│   │   └── relations/           # FoodProductWithNutrition, MealWithNutrition…
│   ├── sources/                 # One package per data source
│   │   ├── base/                # BaseDataSource, DataSourceCallback, DataSourceInfo
│   │   ├── aggregation/         # DataSourceAggregator, SmartMergeStrategy
│   │   ├── ciqual/              # Ciqual ES API + local XML import pipeline
│   │   └── openfoodfacts/       # SearchAlicious + OFF v2 API
│   ├── network/                 # OkHttp/Retrofit client, logging interceptor
│   └── repository/              # ProductRepository, MealRepository, SearchRepository
│
├── ui/
│   ├── activities/              # MainActivity, ItemDetailsActivity, JournalActivity…
│   ├── delegates/
│   │   ├── search/              # Per-source search card delegates (OFF, Ciqual, Default)
│   │   └── detail/              # Per-source detail renderers (OFF, Ciqual, Default)
│   ├── components/              # NutritionLabelManager, AllergenIconHelper, NutrientBannerView
│   └── adapters/                # SearchResultsAdapter, TimelineAdapter, MealPortionsAdapter
│
├── managers/                    # DataSourceManager, LanguageManager, ThemeManager
└── utils/                       # CategoryCleaner, ScoreOverlayHelper, LanguageDetector
```

### Key design patterns

- **Delegate pattern** — `ItemViewDelegate` and `DetailRenderer` interfaces allow each data source to define its own search card and detail screen layout without any switch statements in the adapter or activity.
- **Aggregator** — `DataSourceAggregator` fans out queries to all active sources in parallel and merges results via `SmartMergeStrategy`, with per-source scoring and diversity enforcement.
- **Asset-first, network fallback** — bundled XML assets are always preferred; Zenodo/FDC URLs are used only if a file is missing at runtime.
- **Unified food model** — all sources map to the same `FoodProduct` + `Nutrition` domain objects. Source-specific quirks are handled in each source's mapper, not in the UI.
- **Hybrid translation** — primary content stored in the language received; `ProductTranslation` maps hold other languages. Default language is English.

---

## Data sources

### OpenFoodFacts
- **Type:** Collaborative, crowd-sourced
- **Search:** SearchAlicious Elasticsearch endpoint (fast, relevance-scored)
- **Detail:** OFF v2 API (`/api/v2/product/{barcode}.json`)
- **Coverage:** 3M+ products worldwide, product images, NutriScore, EcoScore, NOVA group, allergens
- **Attribution:** [OpenFoodFacts.org](https://world.openfoodfacts.org) — Open Database Licence (ODbL)

### Ciqual 2025 (ANSES)
- **Type:** Scientific reference, French national food composition table
- **Search:** Ciqual Elasticsearch API (live) + local Room DB (offline, after import)
- **Local import:** `alim_grp_2025_11_03.xml` (80KB, bundled), `alim_2025_11_03.xml` (1.6MB, bundled), `compo_2025_11_03.xml` (69MB, downloaded at first launch from Zenodo)
- **Coverage:** 3,484 foods, 65+ nutrients per food, full EU mineral/vitamin panel
- **Attribution:** [ANSES Ciqual](https://ciqual.anses.fr) — Etalab Open Licence — DOI [10.5281/zenodo.17550133](https://doi.org/10.5281/zenodo.17550133)

> ⚠️ `compo_2025_11_03.xml` (69MB) is excluded from this repository. The app downloads it automatically from Zenodo on first launch. To build and run locally without waiting for the download, place the file manually in `app/src/main/assets/`.

---

## Getting started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 34+
- Java 17
- Min SDK: 26 (Android 8.0)

### Build

```bash
git clone https://github.com/YOUR_USERNAME/sugardaddi.git
cd sugardaddi
# Open in Android Studio and build, or:
./gradlew assembleDebug
```

No API keys required. All data sources are public and unauthenticated.

### Assets

Files bundled in the APK (committed to git):
- `alim_grp_*.xml` — Ciqual category hierarchy (80KB), loaded at startup
- `alim_*.xml` — Ciqual food list (1.6MB), imported on first launch
- `food.csv` — USDA food descriptions (future)
- `nutrient.csv` — USDA nutrient definitions (future)

Files NOT committed (downloaded at runtime):
- `compo_*.xml` — Ciqual composition data (69MB)
  Source: https://doi.org/10.5281/zenodo.17550133
- `food_nutrient.csv` — USDA composition data (450MB)
  Source: https://fdc.nal.usda.gov/download-foods.html

The app handles missing files automatically on first launch.

### First launch

On first launch the app triggers a Ciqual import in the background (foreground service). Search falls back to the live Elasticsearch API while the import runs. Once complete, search is fully offline for Ciqual products.

---

## Roadmap

- [ ] **USDA FoodData Central** — third data source, same asset-first architecture
- [ ] **Detail screens** — dedicated detail activity for Ciqual and Default sources
- [ ] **Favorites** — persistent favorites with dedicated screen
- [ ] **Meal photo capture** — attach photos to journal meals
- [ ] **Category comparison** — compare a product against its Ciqual category average
- [ ] **Local data sources RecyclerView** — reusable card component in Settings for each downloadable source (Ciqual, USDA…)

---

## Screenshots

*Coming soon.*

<!-- Add screenshots here once UI is stable:
![Search](docs/screenshots/search.png)
![Product detail](docs/screenshots/detail_off.png)
![Ciqual detail](docs/screenshots/detail_ciqual.png)
![Journal](docs/screenshots/journal.png)
![Settings](docs/screenshots/settings.png)
-->

---

## Licence

MIT — see [LICENSE](LICENSE).

---

## Data attribution

This app uses data from:
- **OpenFoodFacts** — [openfoodfacts.org](https://world.openfoodfacts.org) — ODbL
- **Ciqual / ANSES** — [ciqual.anses.fr](https://ciqual.anses.fr) — Etalab Open Licence
