package com.simfat.backend.integration.openeo;

import com.fasterxml.jackson.databind.JsonNode;
import com.simfat.backend.model.IndicatorType;

public interface OpenEoServiceClient {

    JsonNode getCapabilities();

    JsonNode getCollections(int limit);

    OpenEoIndicatorLatestResponse fetchLatestIndicator(IndicatorType indicator, OpenEoIndicatorLatestRequest request);

    OpenEoJobSubmissionResult createNdviJob(String regionId, String aoi, String periodStart, String periodEnd);

    OpenEoJobSubmissionResult createNdmiJob(String regionId, String aoi, String periodStart, String periodEnd);

    default OpenEoJobSubmissionResult submitJob(IndicatorType indicator, OpenEoJobRequest request) {
        String periodStart = request.getPeriodStart() != null ? request.getPeriodStart().toString() : null;
        String periodEnd = request.getPeriodEnd() != null ? request.getPeriodEnd().toString() : null;

        if (IndicatorType.NDVI.equals(indicator)) {
            return createNdviJob(request.getRegionId(), request.getAoi(), periodStart, periodEnd);
        }
        return createNdmiJob(request.getRegionId(), request.getAoi(), periodStart, periodEnd);
    }
}
