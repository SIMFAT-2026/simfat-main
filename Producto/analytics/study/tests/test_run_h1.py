"""H1 entry point end to end on synthetic inputs: outputs, provenance, guards."""
import csv
import hashlib
import io
import json
import zipfile

import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin

import run_h1
from study import config, report

DLON = DLAT = 0.0002694945852358564
WEST, NORTH = -72.0, -37.0
H = W = 150
YEARS = list(range(2013, 2026))


def _poly(c0, r0, c1, r1):
    x0, x1 = WEST + c0 * DLON, WEST + c1 * DLON
    y0, y1 = NORTH - r1 * DLAT, NORTH - r0 * DLAT
    return [[[x0, y0], [x1, y0], [x1, y1], [x0, y1], [x0, y0]]]


@pytest.fixture
def world(tmp_path):
    geojson_dir = tmp_path / "geojson"
    geojson_dir.mkdir()
    feats = [
        {"type": "Feature", "properties": {"comunaId": cid},
         "geometry": {"type": "Polygon", "coordinates": _poly(*box)}}
        for cid, box in (("CHL.A", (10, 10, 80, 140)), ("CHL.B", (80, 10, 140, 140)))
    ]
    (geojson_dir / "comunas-test.geojson").write_text(
        json.dumps({"type": "FeatureCollection", "features": feats}), encoding="utf-8"
    )

    cache = tmp_path / "cache"
    cache.mkdir()
    manifest = []
    for year in YEARS:
        arr = np.zeros((H, W), dtype=np.uint8)
        if year == 2015:
            arr[20:90, 20:80] = 1  # 70 x 60 px ~ 300 ha, inside comuna A
        if year == 2016:
            arr[20:90, 70:90] = 1  # 70 x 20 px ~ 100 ha: below the threshold
            arr[100:135, 90:135] = 1  # 35 x 45 px ~ 113 ha: below the threshold
        if year == 2017:
            arr[20:100, 60:110] = 1  # 80 x 50 px ~ 290 ha, crosses the A|B border
        name = f"mapbiomas_fire_chile_col1_annual_burned_{year}.tif"
        path = cache / name
        with rasterio.open(path, "w", driver="GTiff", height=H, width=W, count=1, dtype="uint8",
                           crs="EPSG:4326", transform=from_origin(WEST, NORTH, DLON, DLAT)) as dst:
            dst.write(arr, 1)
        manifest.append({"fileName": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    seed = tmp_path / "seed.jsonl"
    seed.write_text(
        "\n".join(
            json.dumps({"comunaId": cid, "fire": {"burnedFractionByYear": {str(y): (i + 1) * 0.01 * (y - 2012) for y in YEARS}}})
            for i, cid in enumerate(("CHL.A", "CHL.B"))
        ) + "\n", encoding="utf-8")

    def lonlat(col, row):
        return WEST + col * DLON, NORTH - row * DLAT

    rows = []
    for col, row, date, time, conf, typ in [
        (50, 50, "2015-02-01", "0600", "n", 0),   # inside the 2015 scar
        (50, 50, "2016-02-01", "0600", "n", 0),   # wrong year for it
        (50, 50, "2015-02-01", "0600", "l", 0),   # filtered out: low confidence
        (60, 60, "2017-02-01", "0600", "h", 0),   # inside the 2017 scar
        (120, 120, "2014-02-01", "0600", "h", 0),  # B, no scar
        (5, 5, "2014-02-01", "0600", "h", 0),      # outside every comuna
    ]:
        lon, lat = lonlat(col, row)
        rows.append((lat, lon, date, time, conf, typ))
    buf = io.StringIO()
    wr = csv.writer(buf)
    wr.writerow(["latitude", "longitude", "acq_date", "acq_time", "confidence", "type"])
    wr.writerows(rows)
    firms = tmp_path / "firms.zip"
    with zipfile.ZipFile(firms, "w") as zf:
        zf.writestr("fire.csv", buf.getvalue())
    return {"geojson": geojson_dir, "cache": cache, "manifest": manifest_path, "seed": seed, "firms": firms,
            "out": tmp_path / "out"}


def _args(w, *extra):
    return ["--geojson-dir", str(w["geojson"]), "--raster-cache", str(w["cache"]), "--manifest", str(w["manifest"]),
            "--seed", str(w["seed"]), "--firms-zip", str(w["firms"]), "--out-dir", str(w["out"]),
            "--bootstrap-b", "60", "--allow-dirty", *extra]


def test_end_to_end_outputs_and_values(world, capsys):
    assert run_h1.main(_args(world)) == 0
    out = world["out"]
    scars = list(csv.DictReader(open(out / "h1_scars.csv", encoding="utf-8")))
    assert [(r["year"], r["detected"]) for r in scars] == [("2015", "True"), ("2017", "True")]
    assert scars[0]["comunas"] == "CHL.A"
    assert scars[1]["comunas"] == "CHL.A;CHL.B"
    assert set(scars[0]) >= {"year", "area_ha", "n_detections_in_buffer", "detected", "edge_flag", "comunas"}
    assert float(scars[0]["area_ha"]) >= 250.0

    summary = json.loads((out / "h1_summary.json").read_text(encoding="utf-8"))
    assert summary["primary"]["n_scars"] == 2 and summary["primary"]["n_detected"] == 2
    assert summary["decision_power"] == "none"
    assert summary["detections"]["n_filtered"] == 5
    assert summary["detections"]["n_filtered_unassigned_study_years"] == 1
    assert summary["secondary"]["n_pairs"] == 2 * 13
    assert summary["preregistration"]["sha256"] == config.verify_preregistration()["sha256"]
    assert len(summary["inputs_sha256"]["annual_rasters"]) == 13
    assert summary["parameters"]["connectivity"] == 8
    assert summary["parameters"]["bootstrap_b"] == 60
    assert "git_sha" in summary

    sec = list(csv.DictReader(open(out / "h1_secondary.csv", encoding="utf-8")))
    assert len(sec) == 26
    by = {(r["comunaId"], r["year"]): r for r in sec}
    assert by[("CHL.A", "2015")]["firms_count"] == "1"   # the 2015 detection (the "l" one is filtered)
    assert by[("CHL.A", "2016")]["firms_count"] == "1"
    assert by[("CHL.B", "2014")]["firms_count"] == "1"
    assert by[("CHL.A", "2014")]["firms_count"] == "0"
    assert float(by[("CHL.B", "2013")]["burned_fraction"]) == pytest.approx(0.02 * 1)


def test_refuses_to_overwrite_existing_outputs(world):
    assert run_h1.main(_args(world)) == 0
    assert run_h1.main(_args(world)) == 2
    assert run_h1.main(_args(world, "--allow-overwrite")) == 0
    summary = json.loads((world["out"] / "h1_summary.json").read_text(encoding="utf-8"))
    assert summary["overwrote_previous"] is True


def test_refuses_a_dirty_study_directory_without_the_override(world, monkeypatch):
    monkeypatch.setattr(report, "git_dirty", lambda *a, **k: True)
    args = [a for a in _args(world) if a != "--allow-dirty"]
    assert run_h1.main(args) == 3
    assert not (world["out"] / "h1_summary.json").exists()


def test_refuses_when_git_cannot_answer(world, monkeypatch):
    monkeypatch.setattr(report, "git_dirty", lambda *a, **k: None)
    args = [a for a in _args(world) if a != "--allow-dirty"]
    assert run_h1.main(args) == 3


def test_aborts_on_a_tampered_raster(world):
    path = next(world["cache"].glob("*2015.tif"))
    path.write_bytes(path.read_bytes() + b"\0")
    with pytest.raises(Exception, match="sha256"):
        run_h1.main(_args(world))
    assert not (world["out"] / "h1_summary.json").exists()


def test_aborts_if_the_preregistration_hash_does_not_match(world, monkeypatch):
    def boom(*a, **k):
        raise config.PreregistrationIntegrityError("changed")
    monkeypatch.setattr(config, "verify_preregistration", boom)
    with pytest.raises(config.PreregistrationIntegrityError):
        run_h1.main(_args(world))
