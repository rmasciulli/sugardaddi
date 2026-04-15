# Assets

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
