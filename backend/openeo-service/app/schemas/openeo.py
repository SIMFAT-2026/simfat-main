from datetime import date, datetime
from typing import Any, Literal

from pydantic import BaseModel, Field

from app.models.job import IndicatorType


class OpenEOCapabilitiesResponse(BaseModel):
    status: str = "ok"
    source: str = "openEO"
    cached: bool
    fetched_at: datetime = Field(alias="fetchedAt")
    data: dict[str, Any]

    model_config = {"populate_by_name": True}


class OpenEOCollectionsResponse(BaseModel):
    status: str = "ok"
    source: str = "openEO"
    cached: bool
    fetched_at: datetime = Field(alias="fetchedAt")
    limit: int
    count: int
    collections: list[dict[str, Any]]

    model_config = {"populate_by_name": True}


class OpenEOIndicatorLatestResponse(BaseModel):
    status: str = "ok"
    source: str = "openEO"
    cached: bool
    fetched_at: datetime = Field(alias="fetchedAt")
    measured_at: datetime = Field(alias="measuredAt")
    indicator_type: IndicatorType = Field(alias="indicatorType")
    region_id: str | None = Field(default=None, alias="regionId")
    period_start: date = Field(alias="periodStart")
    period_end: date = Field(alias="periodEnd")
    collection_id: str = Field(alias="collectionId")
    value: float | None = None
    backend_synced: bool = Field(default=False, alias="backendSynced")
    backend_status_code: int | None = Field(default=None, alias="backendStatusCode")
    backend_target_url: str | None = Field(default=None, alias="backendTargetUrl")

    model_config = {"populate_by_name": True}


class OpenEOIndicatorDailyUIResponse(BaseModel):
    status: str = "ok"
    source: str = "openEO"
    indicator_type: IndicatorType = Field(alias="indicatorType")
    region_id: str | None = Field(default=None, alias="regionId")
    period_start: date = Field(alias="periodStart")
    period_end: date = Field(alias="periodEnd")
    data_status: Literal[
        "ok",
        "no_data",
        "timeout",
        "auth_error",
        "upstream_error",
        "validation_error",
    ] = Field(alias="dataStatus")
    value: float | None = None
    backend_synced: bool = Field(default=False, alias="backendSynced")
    backend_status_code: int | None = Field(default=None, alias="backendStatusCode")
    fetched_at: datetime | None = Field(default=None, alias="fetchedAt")
    measured_at: datetime | None = Field(default=None, alias="measuredAt")
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")

    model_config = {"populate_by_name": True}
