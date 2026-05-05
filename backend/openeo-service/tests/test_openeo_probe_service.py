from app.models.job import IndicatorType
from app.schemas.jobs import IndicatorJobRequest
from app.services.openeo_probe_service import OpenEOProbeService


class FakeOpenEOAdapter:
    def get_indicator_latest(self, indicator_type, payload):  # type: ignore[no-untyped-def]
        _ = (indicator_type, payload)
        return {
            "cached": False,
            "fetchedAt": "2026-04-15T00:00:00Z",
            "measuredAt": "2026-04-15T00:00:00Z",
            "indicatorType": "NDVI",
            "regionId": "region-001",
            "periodStart": "2026-04-01",
            "periodEnd": "2026-04-12",
            "collectionId": "SENTINEL2_L2A",
            "value": 0.47,
        }


class FakeBackendClient:
    def __init__(self) -> None:
        self.last_payload = None

    def publish_indicator_measurement(self, payload):  # type: ignore[no-untyped-def]
        self.last_payload = payload
        return {
            "synced": True,
            "statusCode": 201,
            "targetUrl": "http://localhost:8080/api/indicators/measurements",
        }


def test_indicator_latest_publishes_to_backend() -> None:
    backend_client = FakeBackendClient()
    service = OpenEOProbeService(adapter=FakeOpenEOAdapter(), backend_client=backend_client)
    service.backend_sync_enabled = True

    response = service.get_indicator_latest(
        indicator_type=IndicatorType.NDVI,
        payload=IndicatorJobRequest(
            regionId="region-001",
            aoi={"type": "bbox", "coordinates": [-72.6, -38.8, -72.3, -38.5]},
            periodStart="2026-04-01",
            periodEnd="2026-04-12",
        ),
    )

    assert backend_client.last_payload is not None
    assert backend_client.last_payload["indicatorType"] == "NDVI"
    assert backend_client.last_payload["regionId"] == "region-001"
    assert response.backend_synced is True
    assert response.backend_status_code == 201
