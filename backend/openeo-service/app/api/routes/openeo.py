from fastapi import APIRouter, Query

from app.models.job import IndicatorType
from app.schemas.jobs import IndicatorJobRequest
from app.schemas.openeo import (
    OpenEOCapabilitiesResponse,
    OpenEOCollectionsResponse,
    OpenEOIndicatorDailyUIResponse,
    OpenEOIndicatorLatestResponse,
)
from app.services.openeo_probe_service import OpenEOProbeService

router = APIRouter(prefix="/openeo", tags=["openeo"])
service = OpenEOProbeService()


@router.get("/capabilities", response_model=OpenEOCapabilitiesResponse)
def get_capabilities() -> OpenEOCapabilitiesResponse:
    return service.get_capabilities()


@router.get("/collections", response_model=OpenEOCollectionsResponse)
def get_collections(limit: int = Query(default=5, ge=1, le=20)) -> OpenEOCollectionsResponse:
    return service.get_collections(limit=limit)


@router.post("/indicators/latest/{indicator}", response_model=OpenEOIndicatorLatestResponse)
def get_indicator_latest(
    indicator: IndicatorType,
    payload: IndicatorJobRequest,
) -> OpenEOIndicatorLatestResponse:
    return service.get_indicator_latest(indicator_type=indicator, payload=payload)


@router.post("/ui/daily/{indicator}", response_model=OpenEOIndicatorDailyUIResponse)
def get_indicator_daily_ui(
    indicator: IndicatorType,
    payload: IndicatorJobRequest,
) -> OpenEOIndicatorDailyUIResponse:
    return service.get_indicator_daily_ui(indicator_type=indicator, payload=payload)
