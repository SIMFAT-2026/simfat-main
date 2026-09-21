# MapBiomas pipeline

Offline pipeline that computes per-comuna MapBiomas Chile statistics (land cover
Collection 2, Fuego Collection 1) and emits a versioned seed consumed by the
SIMFAT backend. No raster is read at runtime.

Status: skeleton. Implemented so far: legend tree with disjointness check
(`legend.py`) and windowed zonal statistics with latitude-dependent pixel area
(`zonal.py`). Download, LULC join and seed generation follow in later slices.

## Layout

- `src/mb_pipeline/` pipeline modules
- `tests/` pytest suite; fixtures are synthetic and built in temporary
  directories (no binary blobs are committed)
- `fixtures/` reserved for small committed text fixtures

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
