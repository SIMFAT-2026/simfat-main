from app.adapters.openeo_adapter import OpenEOAdapter
from app.core.exceptions import ValidationError
from app.models.job import IndicatorType, JobStatus
from app.schemas.jobs import IndicatorJobRequest, IndicatorJobResponse


class IndicatorService:
    def __init__(self, adapter: OpenEOAdapter | None = None) -> None:
        self.adapter = adapter or OpenEOAdapter()

    def create_ndvi_job(self, payload: IndicatorJobRequest) -> IndicatorJobResponse:
        return self._create_job(indicator_type=IndicatorType.NDVI, payload=payload)

    def create_ndmi_job(self, payload: IndicatorJobRequest) -> IndicatorJobResponse:
        return self._create_job(indicator_type=IndicatorType.NDMI, payload=payload)

    def _create_job(
        self, indicator_type: IndicatorType, payload: IndicatorJobRequest
    ) -> IndicatorJobResponse:
        if not payload.region_id and not payload.aoi:
            raise ValidationError("At least one of regionId or aoi is required")

        job_id = self.adapter.create_job(indicator_type=indicator_type, payload=payload)
        return IndicatorJobResponse(
            jobId=job_id,
            status=JobStatus.ACCEPTED,
            indicatorType=indicator_type,
            periodStart=payload.period_start,
            periodEnd=payload.period_end,
            value=None,
            source="openEO",
            message="Placeholder job accepted. Real openEO execution is not implemented yet.",
        )
