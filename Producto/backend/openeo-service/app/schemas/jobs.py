from datetime import date
from typing import Literal, Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator

from app.models.job import IndicatorType, JobStatus


class AoiInput(BaseModel):
    type: Literal["bbox", "polygon"] = "bbox"
    coordinates: list[float]


class IndicatorJobRequest(BaseModel):
    region_id: Optional[str] = Field(default=None, alias="regionId")
    aoi: Optional[AoiInput] = None
    period_start: date = Field(alias="periodStart")
    period_end: date = Field(alias="periodEnd")

    @field_validator("period_end")
    @classmethod
    def validate_period(cls, value: date, info):  # type: ignore[no-untyped-def]
        period_start = info.data.get("period_start")
        if period_start and value < period_start:
            raise ValueError("periodEnd must be greater than or equal to periodStart")
        return value

    @field_validator("aoi")
    @classmethod
    def validate_aoi(cls, value: Optional[AoiInput]) -> Optional[AoiInput]:
        if value and value.type == "bbox" and len(value.coordinates) != 4:
            raise ValueError("bbox coordinates must include 4 numeric values")
        return value

    model_config = {"populate_by_name": True}


class IndicatorJobResponse(BaseModel):
    job_id: UUID = Field(alias="jobId")
    status: JobStatus
    indicator_type: IndicatorType = Field(alias="indicatorType")
    period_start: date = Field(alias="periodStart")
    period_end: date = Field(alias="periodEnd")
    value: Optional[float] = None
    source: str = "openEO"
    message: str

    model_config = {"populate_by_name": True}
