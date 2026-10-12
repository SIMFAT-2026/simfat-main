"""Shared synthetic fixtures. Rasters/xlsx are generated in tmp dirs, never committed."""
from pathlib import Path

import numpy as np
import openpyxl
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


@pytest.fixture
def make_coverage_xlsx(tmp_path):
    """Write a tiny synthetic workbook with a COVERAGE sheet and return its path.

    ``rows`` is a list of dicts, each with ``territory_level_1/2/4``, ``class``
    and a ``years`` mapping of ``{year: hectares}``. Mirrors the shape of the
    real MapBiomas Chile Col 2 per-comuna statistics workbook (columns
    ID, territory_level_1..4, class, class_level_0..3, y1999..y2024) closely
    enough for the reader under test, without the class_level_* label columns
    the reader does not use.
    """

    def _make(rows, year_columns=("y1999", "y2024"), name="fixture.xlsx"):
        path = Path(tmp_path) / name
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "COVERAGE"
        header = [
            "ID",
            "territory_level_1",
            "territory_level_2",
            "territory_level_3",
            "territory_level_4",
            "class",
            "class_level_0",
            "class_level_1",
            "class_level_2",
            "class_level_3",
            *year_columns,
        ]
        ws.append(header)
        for i, row in enumerate(rows, start=1):
            years = row.get("years", {})
            ws.append(
                [
                    i,
                    "Chile",
                    row["territory_level_2"],
                    row.get("territory_level_3", row["territory_level_2"]),
                    row["territory_level_4"],
                    row["class"],
                    "", "", "", "",
                    *[years.get(int(col[1:])) for col in year_columns],
                ]
            )
        wb.save(path)
        return path

    return _make
