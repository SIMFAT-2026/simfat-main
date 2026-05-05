from app.core.exceptions import ExternalServiceError, ValidationError
from app.models.job import IndicatorType
from app.adapters.openeo_adapter import OpenEOAdapter
from app.clients.simfat_backend_client import SimfatBackendClient
from app.core.config import get_settings
from app.schemas.jobs import IndicatorJobRequest
from app.schemas.openeo import (
    OpenEOCapabilitiesResponse,
    OpenEOCollectionsResponse,
    OpenEOIndicatorDailyUIResponse,
    OpenEOIndicatorLatestResponse,
)


class OpenEOProbeService:
    def __init__(
        self,
        adapter: OpenEOAdapter | None = None,
        backend_client: SimfatBackendClient | None = None,
    ) -> None:
        settings = get_settings()
        self.backend_sync_enabled = settings.simfat_backend_sync_enabled
        self.adapter = adapter or OpenEOAdapter()
        self.backend_client = backend_client or SimfatBackendClient(
            base_url=settings.simfat_backend_url,
            ingest_path=settings.simfat_backend_indicator_ingest_path,
            timeout_seconds=settings.simfat_backend_timeout_seconds,
            auth_token=settings.simfat_backend_auth_token,
        )

    def get_capabilities(self) -> OpenEOCapabilitiesResponse:
        payload = self.adapter.get_capabilities()
        return OpenEOCapabilitiesResponse(**payload)

    def get_collections(self, limit: int) -> OpenEOCollectionsResponse:
        payload = self.adapter.get_collections(limit=limit)
        return OpenEOCollectionsResponse(**payload)

    def get_indicator_latest(
        self,
        indicator_type: IndicatorType,
        payload: IndicatorJobRequest,
    ) -> OpenEOIndicatorLatestResponse:
        if not payload.aoi:
            raise ValidationError("aoi is required to compute indicator values")
        if payload.aoi.type != "bbox":
            raise ValidationError("Only bbox aoi is supported for now")
        if len(payload.aoi.coordinates) != 4:
            raise ValidationError("bbox coordinates must include exactly 4 values")

        data = self.adapter.get_indicator_latest(indicator_type=indicator_type, payload=payload)
        response_model = OpenEOIndicatorLatestResponse(**data)

        if not self.backend_sync_enabled:
            response_model.backend_synced = False
            response_model.backend_status_code = None
            response_model.backend_target_url = None
            return response_model

        sync_payload = {
            "source": response_model.source,
            "indicatorType": response_model.indicator_type.value,
            "regionId": response_model.region_id,
            "periodStart": response_model.period_start.isoformat(),
            "periodEnd": response_model.period_end.isoformat(),
            "fetchedAt": response_model.fetched_at.isoformat(),
            "measuredAt": response_model.measured_at.isoformat(),
            "collectionId": response_model.collection_id,
            "value": response_model.value,
            "cached": response_model.cached,
        }
        backend_result = self.backend_client.publish_indicator_measurement(sync_payload)

        response_model.backend_synced = bool(backend_result.get("synced"))
        response_model.backend_status_code = backend_result.get("statusCode")
        response_model.backend_target_url = backend_result.get("targetUrl")
        return response_model

    def get_indicator_daily_ui(
        self,
        indicator_type: IndicatorType,
        payload: IndicatorJobRequest,
    ) -> OpenEOIndicatorDailyUIResponse:
        try:
            latest = self.get_indicator_latest(indicator_type=indicator_type, payload=payload)
            return OpenEOIndicatorDailyUIResponse(
                indicatorType=latest.indicator_type,
                regionId=latest.region_id,
                periodStart=latest.period_start,
                periodEnd=latest.period_end,
                dataStatus="ok",
                value=latest.value,
                backendSynced=latest.backend_synced,
                backendStatusCode=latest.backend_status_code,
                fetchedAt=latest.fetched_at,
                measuredAt=latest.measured_at,
            )
        except ValidationError:
            raise
        except ExternalServiceError as exc:
            detail = (exc.message or "").lower()
            data_status = "upstream_error"
            if exc.status_code == 400 and "no data available" in detail:
                data_status = "no_data"
            elif exc.status_code == 401:
                data_status = "auth_error"
            elif exc.status_code == 502 and "timed out" in detail:
                data_status = "timeout"

            return OpenEOIndicatorDailyUIResponse(
                indicatorType=indicator_type,
                regionId=payload.region_id,
                periodStart=payload.period_start,
                periodEnd=payload.period_end,
                dataStatus=data_status,
                value=None,
                errorCode=exc.code,
                errorMessage=exc.message,
            )
