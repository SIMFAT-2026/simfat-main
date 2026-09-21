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

## Usage

    make install
    make test
