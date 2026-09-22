# MapBiomas pipeline

Offline pipeline that computes per-comuna MapBiomas Chile statistics (land cover
Collection 2, Fuego Collection 1) and emits a versioned seed consumed by the
SIMFAT backend. No raster is read at runtime.

Status: in progress. Implemented so far: legend tree with disjointness check
(`legend.py`), windowed zonal statistics with latitude-dependent pixel area
(`zonal.py`), resilient download with a sha256 provenance manifest
(`download.py`), the land-cover COVERAGE reader + comuna join (`lulc.py`),
and Fuego zonal aggregation + document assembly (`fire_stats.py`,
`build_stats.py`), exercised for real against 2017 data via the committed
`scripts/generate_fire_report_2017.py` (see "Fuego Collection 1 (fire)"
below). The Java Mongo loader and the full
LULC+Fuego merged production seed (all years, all comunas) are NOT part of
this slice; see "S1b scope" below.

## Fuego Collection 1 (fire)

`fire_stats.py` computes, per comuna-year, a burned-fraction from the
public `annual_burned_v1` GeoTIFFs (no Java, no GEE -- direct GCS public
bucket download via `download.py`) and a coverage/extent gate from the
raster's own declared bounding box. `build_stats.py` assembles these into
per-comuna documents and writes `data/coverage_report.csv` (the Biobio gate
artifact) and a seed JSONL.

**Empirical finding: `annual_burned_coverage_v1` is not a mapped-area mask.**
The original plan was to use this paired dataset as a "was this pixel
classified at all this year" denominator for burned fraction. Downloading
the real 2017 raster and comparing it pixel-for-pixel against
`annual_burned_v1` for every Biobío comuna shows its nonzero pixel count is
EXACTLY EQUAL to `annual_burned_v1`'s burned-pixel count everywhere tested
(e.g. Florida: 401,472 burned pixels == 401,472 nonzero coverage pixels).
It holds the land-cover class OF PIXELS THAT BURNED that year, not a
general observed/mapped indicator. Using it as a mapped-area denominator
would make `burnedFraction == 1.0` for every comuna with any fire at all --
a degenerate, useless gate. `fire_stats.py` computes burned fraction
against `annual_burned_v1`'s own total area instead (it has no nodata), and
replaces the coverage gate with `bbox_coverage_fraction`: a real
geometry-vs-raster-bounding-box check, independent of any pixel value. See
`fire_stats.py`'s module docstring and `tests/test_fire_stats.py` for the
full evidence and the synthetic edge-case tests.

**S1b scope (what has REAL evidence vs what does not).** For the 2026-09-21
apply batch, real Fuego data was downloaded and processed for ALL 86 target
comunas, for:

- `annual_burned_v1` 2017 -- real burned hectares/fraction per comuna.
- `annual_burned_coverage_v1` 2017 -- downloaded and hashed, but only used
  to confirm the empirical finding above, not as a production denominator.
- `frequency_burned_v1` 2013-2017 -- real per-comuna frequency mean/max
  over that 5-year window (NOT the full 2013-2025 collection).
- `year_last_fire_v1` 2017 -- real per-comuna "most recent burn year", but
  **corrected, not used raw**: comparing it against `annual_burned_v1` 2017
  for the same comunas found real disagreements (Florida, `CHL.6.3.4_1`:
  the raw `year_last_fire_v1` raster's own maximum pixel value in Florida's
  bounding box is 2016, never 2017, while `annual_burned_v1` 2017 shows
  47.6% of Florida burned that same year). `build_fire_section` corrects
  for this by taking `max(raw yearLastFire, most recent processed year
  with burnedHa > 0)`, so the shipped value can never contradict the same
  document's own `burnedFractionByYear`. See `fire_stats.py`'s module
  docstring and `tests/test_fire_stats.py`'s "yearLastFire correction"
  section for the full evidence and tests (including a real-Florida-data
  regression test).

Result: `coverageFraction` (bbox extent check) is exactly 1.0 for all 86
comunas -- the Fuego Col 1 collection's spatial extent fully encloses
Biobío, Ñuble and Araucanía, so `fire.available=true` for every comuna at
the committed 0.95 threshold. 38/86 comunas show a nonzero burned area in
2017 (max 47.6%, Florida); this matches the well-documented Jan-Feb 2017
central-Chile megafires. Real numbers are in `data/coverage_report.csv`.

**Two documented, non-blocking caveats.** `coverageFraction` is a
bounding-box-overlap ratio (`bbox_coverage_fraction`), not a true
polygon-area coverage check; this is valid only because all 3 target
regions are comfortably inside the Fuego Col 1 raster bounds (every real
comuna reports 1.0) -- generalizing this gate beyond these 3 regions would
need a true polygon-intersection check instead. Separately,
`burnedFraction`'s denominator is each comuna's total polygon area with no
water mask, so a lake-heavy comuna (e.g. Villarrica, Pucón) reports burned
area relative to its total area including lake surface, not
vegetated-land-only area.

**NOT YET COVERED in this slice** (do not treat as extrapolated from the
above): fire years 2013-2016 and 2018-2025 (only 2017 was downloaded for
`annual_burned_v1`/`annual_burned_coverage_v1`); the LULC land-cover
join was NOT merged into these documents (`landCover` is `null` with a
stated `landCoverReason` in every document of
`data/fire_stats_2017_partial.jsonl` -- it is a fire-only partial
artifact, not the production seed); the Java `MapbiomasSeedLoader` /
`ComunaMapbiomasStats` Mongo model (design task 1b.3) is out of scope here
(Python-analytics-only slice; no backend changes). A future batch that
downloads more fire years, the real LULC xlsx, and writes the Java loader
is needed before `comuna-mapbiomas-stats.v1.json` can be produced.

## Layout

- `src/mb_pipeline/` pipeline modules
- `scripts/generate_fire_report_2017.py` real, runnable script that joins
  the 86 comuna GeoJSONs with the real Fuego rasters and writes
  `data/coverage_report.csv` / `data/fire_stats_<year>_partial.jsonl`
- `tests/` pytest suite; fixtures are synthetic and built in temporary
  directories (no binary blobs are committed)
- `fixtures/` small committed text fixtures, e.g. `comuna_name_mapping.json`
- `data/manifest.json` committed provenance record (url, sha256, bytes) for
  every large source file the pipeline depends on; the files themselves
  (xlsx, GeoTIFFs) are never committed. Note: the `downloadedAt` value for
  `mapbiomas_lulc_col2_coverage` was reconstructed from the commit history
  (the timestamp of the commit that added this manifest entry) because the
  original capture instant was not preserved when the entry was first
  committed -- it is not a live-captured value.

## Land cover (LULC) join

`lulc.py` reads the `COVERAGE` sheet of the MapBiomas Chile Col 2 per-comuna
statistics workbook and joins it to the SIMFAT comuna GeoJSON seeds by name
(the workbook has no CUT/INE code column). Names are normalized by
NFKD-stripping accents and keeping only ASCII letters, so GADM-style
`SanPedrodelaPaz` and the workbook's `San Pedro de la Paz` fold to the same
key; the join is scoped per region so two regions sharing a comuna name
never cross-match. `join_coverage` requires an exact 86/86 match against
Biobío + Ñuble + La Araucanía and runs `legend.assert_disjoint` per
comuna-year before returning, aborting loudly instead of double counting if
a class and one of its legend ancestors were ever present together. A blank
year cell in the COVERAGE sheet (no observation for that class-year) is
read as 0.0 hectares, not skipped or treated as missing, so aggregation
never has to special-case an absent cell.

**Class 3 ("Forest") vs 59/60/67, resolved empirically**: downloading the
real workbook and inspecting every row confirms class 3 never appears at
all in Biobío, Ñuble or La Araucanía -- only its leaf children (59 primary,
60 secondary, 67 dwarf forest) do. Nationally, class 3 does co-occur with
its children in 19 comunas outside these 3 regions, so the disjointness
check is a real dataset-scope guard, not a leftover: if the pipeline is
ever extended beyond these 3 regions, `join_coverage` will fail loudly on
first contact with that ambiguity instead of guessing how to reconcile it.
See `tests/test_lulc.py::test_real_evidence_our_regions_never_mix_class_3_with_children`
for the captured real rows and
`tests/test_lulc.py::test_join_coverage_rejects_ancestor_and_descendant_class_in_same_comuna_year`
for the synthetic case proving the guard fires.

To refresh the download manifest and the committed name mapping fixture
against a fresh copy of the real workbook (manual step; not run by the test
suite):

    cd Producto/analytics/mapbiomas
    PYTHONPATH=src python -c "
    from pathlib import Path
    from mb_pipeline import download, lulc

    result = download.download_file(
        'https://chile.mapbiomas.org/wp-content/uploads/sites/7/2026/08/statistics_lulc_chile_col2_political_level_234.xlsx',
        Path('/tmp/mapbiomas_col2.xlsx'),
    )
    entry = download.manifest_entry('mapbiomas_lulc_col2_coverage', '<url above>', result)
    download.write_manifest(Path('data/manifest.json'), entry)

    rows = lulc.read_coverage_sheet(result.path)
    region_features = lulc.load_region_features(lulc.DEFAULT_REGIONS)
    index = lulc.build_comuna_index(region_features)
    our_regions = {r.xlsx_region for r in lulc.DEFAULT_REGIONS}
    scoped_rows = [r for r in rows if r['region'] in our_regions]
    lulc.join_coverage(scoped_rows, index, expected_comuna_ids=set(index.values()))  # 86/86 + disjointness check
    mapping = lulc.build_name_mapping(scoped_rows, index)
    lulc.write_name_mapping(Path('fixtures/comuna_name_mapping.json'), mapping)
    "

To reproduce the real Fuego Collection 1 numbers (manual step; the GCS
object listing API is `https://storage.googleapis.com/storage/v1/b/mapbiomas-public/o?prefix=initiatives/chile/fire/collection1/<dataset>/`,
list it to get each year's `mediaLink`, then download with `download.py`
into a directory outside the repo, e.g. `%TEMP%\mb_fire_col1`, matching the
file names `test_build_stats_real.py` expects). With `MB_FIRE_RASTER_DIR`
set (or the files placed at `%TEMP%\mb_fire_col1`), run
`pytest tests/test_build_stats_real.py` to exercise the real path, and run
`scripts/generate_fire_report_2017.py` to (re)generate
`data/coverage_report.csv` and `data/fire_stats_2017_partial.jsonl` for
real, for all 86 target comunas -- this is the actual, committed script
that produced the files in this repo (generic over whatever fire years are
present in the raster directory, not hardcoded to 2017):

    cd Producto/analytics/mapbiomas
    MB_FIRE_RASTER_DIR=/path/to/mb_fire_col1 PYTHONPATH=src python scripts/generate_fire_report_2017.py

(or omit `MB_FIRE_RASTER_DIR` if the rasters are at the default
`%TEMP%\mb_fire_col1`).

## Pixel area

Rasters are EPSG:4326 with a constant pixel size in degrees, so ground area
depends on latitude. Area is computed per raster row as the WGS84 ellipsoidal
cell area, `(M(phi) * dlat) * (N(phi) * cos(phi) * dlon)`, where `M` and `N` are
the meridian and prime-vertical radii of curvature at the row-centre latitude.
Tests check it against an independent closed-form authalic-integral oracle to
a relative tolerance of 1e-4. `zonal_stats` accepts only north-up, unrotated,
integer-valued EPSG:4326 rasters and Polygon/MultiPolygon geometries (or a
GeoJSON Feature wrapping one); anything else raises `ValueError`.

## Usage

Run every command from `Producto/analytics/mapbiomas` (the Makefile and
`pytest.ini` live there):

    cd Producto/analytics/mapbiomas
    make install
    make test
