# MapBiomas pipeline

Offline pipeline that computes per-comuna MapBiomas Chile statistics (land cover
Collection 2, Fuego Collection 1) and emits a versioned seed consumed by the
SIMFAT backend. No raster is read at runtime.

Status: in progress. Implemented so far: legend tree with disjointness check
(`legend.py`), windowed zonal statistics with latitude-dependent pixel area
(`zonal.py`), resilient download with a sha256 provenance manifest
(`download.py`), and the land-cover COVERAGE reader + comuna join
(`lulc.py`). Zonal stats over the Fuego GeoTIFFs and seed generation
(`build_stats.py`) follow in later slices.

## Layout

- `src/mb_pipeline/` pipeline modules
- `tests/` pytest suite; fixtures are synthetic and built in temporary
  directories (no binary blobs are committed)
- `fixtures/` small committed text fixtures, e.g. `comuna_name_mapping.json`
- `data/manifest.json` committed provenance record (url, sha256, bytes) for
  every large source file the pipeline depends on; the files themselves
  (xlsx, GeoTIFFs) are never committed

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
a class and one of its legend ancestors were ever present together.

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
