from fastapi.testclient import TestClient

from app.api.routes import openeo as openeo_routes
from app.main import app
from app.schemas.openeo import OpenEOCapabilitiesResponse, OpenEOCollectionsResponse

client = TestClient(app)


def test_health_returns_ok() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "openeo-service"


def test_config_check_endpoint_exists() -> None:
    response = client.get("/config/check")
    assert response.status_code == 200
    body = response.json()
    assert "configured" in body
    assert "missingFields" in body


def test_jobs_placeholder_route_is_removed() -> None:
    payload = {
        "regionId": "region-001",
        "periodStart": "2025-01-01",
        "periodEnd": "2025-01-31",
    }
    response = client.post("/jobs/ndvi", json=payload)
    assert response.status_code == 404


def test_openeo_capabilities_endpoint_exists(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    def fake_capabilities() -> OpenEOCapabilitiesResponse:
        return OpenEOCapabilitiesResponse(
            cached=False,
            fetchedAt="2026-04-10T00:00:00Z",
            data={"api_version": "1.2.0"},
        )

    monkeypatch.setattr(openeo_routes.service, "get_capabilities", fake_capabilities)
    response = client.get("/openeo/capabilities")
    assert response.status_code == 200
    assert response.json()["source"] == "openEO"


def test_openeo_collections_endpoint_exists(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    def fake_collections(limit: int) -> OpenEOCollectionsResponse:
        return OpenEOCollectionsResponse(
            cached=False,
            fetchedAt="2026-04-10T00:00:00Z",
            limit=limit,
            count=1,
            collections=[{"id": "SENTINEL2_L2A"}],
        )

    monkeypatch.setattr(openeo_routes.service, "get_collections", fake_collections)
    response = client.get("/openeo/collections?limit=5")
    assert response.status_code == 200
    body = response.json()
    assert body["limit"] == 5
    assert body["count"] == 1


def test_openeo_indicator_latest_endpoint_exists(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    def fake_indicator_latest(indicator_type, payload):  # type: ignore[no-untyped-def]
        _ = (indicator_type, payload)
        return {
            "status": "ok",
            "source": "openEO",
            "cached": False,
            "fetchedAt": "2026-04-12T00:00:00Z",
            "measuredAt": "2026-04-12T00:00:00Z",
            "indicatorType": "NDVI",
            "regionId": "region-001",
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
            "collectionId": "SENTINEL2_L2A",
            "value": 0.42,
        }

    monkeypatch.setattr(openeo_routes.service, "get_indicator_latest", fake_indicator_latest)
    response = client.post(
        "/openeo/indicators/latest/NDVI",
        json={
            "regionId": "region-001",
            "aoi": {"type": "bbox", "coordinates": [-72.6, -38.8, -72.3, -38.5]},
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["indicatorType"] == "NDVI"
    assert body["collectionId"] == "SENTINEL2_L2A"


def test_openeo_ui_daily_endpoint_exists(monkeypatch) -> None:  # type: ignore[no-untyped-def]
    def fake_indicator_daily_ui(indicator_type, payload):  # type: ignore[no-untyped-def]
        _ = (indicator_type, payload)
        return {
            "status": "ok",
            "source": "openEO",
            "indicatorType": "NDVI",
            "regionId": "region-001",
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
            "dataStatus": "ok",
            "value": 0.42,
            "backendSynced": True,
            "backendStatusCode": 201,
            "fetchedAt": "2026-04-12T00:00:00Z",
            "measuredAt": "2026-04-12T00:00:00Z",
            "errorCode": None,
            "errorMessage": None,
        }

    monkeypatch.setattr(openeo_routes.service, "get_indicator_daily_ui", fake_indicator_daily_ui)
    response = client.post(
        "/openeo/ui/daily/NDVI",
        json={
            "regionId": "region-001",
            "aoi": {"type": "bbox", "coordinates": [-72.6, -38.8, -72.3, -38.5]},
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["indicatorType"] == "NDVI"
    assert body["dataStatus"] == "ok"


def test_openeo_ui_daily_requires_aoi() -> None:
    response = client.post(
        "/openeo/ui/daily/NDVI",
        json={
            "regionId": "CL-13",
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
        },
    )
    assert response.status_code == 400
    body = response.json()
    assert body["error"]["code"] == "VALIDATION_ERROR"
