package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.simfat.backend.integration.openeo.OpenEoServiceClient;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.ComunaRiskSnapshotRepository;
import com.simfat.backend.repository.OpenEoIndicatorObservationRepository;
import com.simfat.backend.repository.TerritoryWeatherObservationRepository;
import com.simfat.backend.service.NotificationService;
import com.simfat.backend.service.OpenWeatherFwiService;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Pins WHY a comuna's alert level was raised. The driver must follow the exact rule order of
// the level resolution: FIRMS today -> FWI >= 45 -> score >= critico -> FIRMS cluster/FRP
// (CRITICO); FWI >= 20 or score >= alto (ALTO); score (PREVENTIVO / NORMAL).
class ComunaRiskServiceImplAlertDriverTest {

    private ComunaRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ComunaRiskServiceImpl(
            mock(ComunaInfoRepository.class),
            mock(ComunaRiskSnapshotRepository.class),
            mock(TerritoryWeatherObservationRepository.class),
            mock(CitizenReportRepository.class),
            mock(OpenWeatherFwiService.class),
            mock(OpenEoIndicatorObservationRepository.class),
            mock(NotificationService.class),
            mock(OpenEoServiceClient.class),
            mock(FirmsAttributionRouter.class),
            mock(MapbiomasSusceptibilityService.class)
        );
    }

    private ComunaRiskServiceImpl.AlertResolution resolve(
        double score, Double fwi, int count, double frp, boolean today
    ) {
        return service.resolveAlert(score, fwi, count, frp, today);
    }

    @Test
    void firmsToday_isCriticoWithFirmsTodayDriver() {
        var r = resolve(0.10, 5.0, 0, 0.0, true);
        assertEquals("CRITICO", r.level());
        assertEquals("FIRMS_TODAY", r.driver());
    }

    @Test
    void firmsTodayWinsOverExtremeFwiAndScore() {
        var r = resolve(0.95, 60.0, 9, 99.0, true);
        assertEquals("CRITICO", r.level());
        assertEquals("FIRMS_TODAY", r.driver());
    }

    @Test
    void extremeFwi_isCriticoWithFwiDriver_evenWithHighScore() {
        var r = resolve(0.90, 45.0, 0, 0.0, false);
        assertEquals("CRITICO", r.level());
        assertEquals("FWI", r.driver());
    }

    @Test
    void scoreAtCritico_isCriticoWithScoreDriver() {
        var r = resolve(0.85, 10.0, 0, 0.0, false);
        assertEquals("CRITICO", r.level());
        assertEquals("SCORE", r.driver());
    }

    @Test
    void scoreCriticoWinsOverFirmsCluster() {
        var r = resolve(0.90, 10.0, 9, 99.0, false);
        assertEquals("SCORE", r.driver());
    }

    @Test
    void recentFirmsCluster_isCriticoWithClusterDriver() {
        var r = resolve(0.10, null, FirmsScoringConstants.FIRMS_COUNT_CRITICO, 1.0, false);
        assertEquals("CRITICO", r.level());
        assertEquals("FIRMS_CLUSTER", r.driver());
    }

    @Test
    void clusterWinsOverFrpWhenBothMatch() {
        var r = resolve(0.10, null, FirmsScoringConstants.FIRMS_COUNT_CRITICO,
            FirmsScoringConstants.FIRMS_FRP_CRITICO, false);
        assertEquals("FIRMS_CLUSTER", r.driver());
    }

    @Test
    void intenseRecentFirms_isCriticoWithFrpDriver() {
        var r = resolve(0.10, null, 1, FirmsScoringConstants.FIRMS_FRP_CRITICO, false);
        assertEquals("CRITICO", r.level());
        assertEquals("FIRMS_FRP", r.driver());
    }

    @Test
    void fwiAtAlto_isAltoWithFwiDriver() {
        var r = resolve(0.10, 20.0, 0, 0.0, false);
        assertEquals("ALTO", r.level());
        assertEquals("FWI", r.driver());
    }

    @Test
    void fwiAndScoreBothAlto_fwiWins() {
        var r = resolve(0.75, 25.0, 0, 0.0, false);
        assertEquals("ALTO", r.level());
        assertEquals("FWI", r.driver());
    }

    @Test
    void scoreAtAlto_isAltoWithScoreDriver() {
        var r = resolve(0.70, 5.0, 0, 0.0, false);
        assertEquals("ALTO", r.level());
        assertEquals("SCORE", r.driver());
    }

    @Test
    void nullFwi_scoreAlto_usesScoreDriver() {
        var r = resolve(0.72, null, 0, 0.0, false);
        assertEquals("ALTO", r.level());
        assertEquals("SCORE", r.driver());
    }

    @Test
    void scoreAtPreventivo_isPreventivoWithScoreDriver() {
        var r = resolve(0.50, 5.0, 0, 0.0, false);
        assertEquals("PREVENTIVO", r.level());
        assertEquals("SCORE", r.driver());
    }

    @Test
    void lowScore_isNormalWithScoreDriver() {
        var r = resolve(0.20, 5.0, 0, 0.0, false);
        assertEquals("NORMAL", r.level());
        assertEquals("SCORE", r.driver());
    }

    @Test
    void levelIsUnchangedByDriverResolution() {
        assertEquals("CRITICO", resolve(0.10, 45.0, 0, 0.0, false).level());
        assertEquals("ALTO", resolve(0.10, 20.0, 0, 0.0, false).level());
    }
}
