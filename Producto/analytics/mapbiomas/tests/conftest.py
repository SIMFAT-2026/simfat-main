"""Shared synthetic fixtures. Rasters are generated in tmp dirs, never committed."""
from pathlib import Path

import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin


@pytest.fixture
def make_geotiff(tmp_path):
    """Return a factory writing a single-band EPSG:4326 GeoTIFF and its path."""

    def _make(array, west, north, dlon, dlat, name="fixture.tif", nodata=None):
        path = Path(tmp_path) / name
        array = np.asarray(array)
        with rasterio.open(
            path,
            "w",
            driver="GTiff",
            height=array.shape[0],
            width=array.shape[1],
            count=1,
            dtype=array.dtype,
            crs="EPSG:4326",
            transform=from_origin(west, north, dlon, dlat),
            nodata=nodata,
        ) as dst:
            dst.write(array, 1)
        return path

    return _make
