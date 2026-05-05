from uuid import UUID
from typing import Any

from app.clients.openeo_client import OpenEOClient
from app.core.config import get_settings
from app.models.job import IndicatorType
from app.schemas.jobs import IndicatorJobRequest


class OpenEOAdapter:
    def __init__(self) -> None:
        settings = get_settings()
        self.client = OpenEOClient(
            base_url=settings.openeo_base_url,
            client_id=settings.openeo_client_id,
            client_secret=settings.openeo_client_secret,
            access_token=settings.openeo_access_token,
            refresh_token=settings.openeo_refresh_token,
            refresh_client_id=settings.openeo_refresh_client_id,
            refresh_client_secret=settings.openeo_refresh_client_secret,
        )

    def create_job(self, indicator_type: IndicatorType, payload: IndicatorJobRequest) -> UUID:
        return self.client.submit_indicator_job(indicator_type=indicator_type, payload=payload)

    def get_capabilities(self) -> dict[str, Any]:
        return self.client.fetch_capabilities()

    def get_collections(self, limit: int) -> dict[str, Any]:
        return self.client.fetch_collections(limit=limit)

    def get_indicator_latest(
        self,
        indicator_type: IndicatorType,
        payload: IndicatorJobRequest,
    ) -> dict[str, Any]:
        return self.client.fetch_indicator_latest(indicator_type=indicator_type, payload=payload)
