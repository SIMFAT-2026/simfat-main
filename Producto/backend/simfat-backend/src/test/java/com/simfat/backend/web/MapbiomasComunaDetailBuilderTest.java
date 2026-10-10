package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.simfat.backend.dto.MapbiomasComunaDetailDTO;
import com.simfat.backend.model.ComunaMapbiomasStats;
import com.simfat.backend.service.mapbiomas.MapbiomasSusceptibility;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Pure builder for the public comuna MapBiomas detail (slice S3-5, design D5). */
class MapbiomasComunaDetailBuilderTest {

    private static final String CITATION = "test-citation";

    /** 1999-2024 series: class 59 constant 6000bp; class 12 is 3000bp except 2024 (4000bp); 27 = rest. */
    private static ComunaMapbiomasStats stats(boolean fireAvailable) {
        int[] forest = new int[26];
        int[] grass = new int[26];
        int[] unobserved = new int[26];
        for (int i = 0; i < 26; i++) {
            forest[i] = 6000;
            grass[i] = i == 25 ? 4000 : 3000;
            unobserved[i] = 10000 - forest[i] - grass[i];
        }
        ComunaMapbiomasStats.SharesByYear series = new ComunaMapbiomasStats.SharesByYear();
        series.setFirstYear(1999);
        series.setLastYear(2024);
        series.setUnit("bp");
        Map<String, int[]> classes = new LinkedHashMap<>();
        classes.put("59", forest);
        classes.put("12", grass);
        classes.put("27", unobserved);
        series.setClasses(classes);
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setReferenceYear(2024);
        landCover.setSharesByYear(series);

        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(fireAvailable);
        if (fireAvailable) {
            fire.setCoverageFraction(0.99);
            fire.setBurnedHaByYear(Map.of("2017", 120.5));
            fire.setBurnedFractionByYear(Map.of("2017", 0.05));
            fire.setBurnedFraction(0.08);
            fire.setYearLastFire(2023);
            fire.setYearsSinceLastFire(3);
            fire.setFrequencyMean(1.5);
        }
        ComunaMapbiomasStats.Provenance provenance = new ComunaMapbiomasStats.Provenance();
        provenance.setDownloadDate("2026-09-21");
        provenance.setSources(List.of("https://signed.example/secret"));

        ComunaMapbiomasStats s = new ComunaMapbiomasStats();
        s.setId("CHL.6.1.1_1|v1");
        s.setComunaId("CHL.6.1.1_1");
        s.setRegionId("biobio");
        s.setNombreComuna("Concepcion");
        s.setDataVersion("v1");
        s.setLandCover(landCover);
        s.setFire(fire);
        s.setProvenance(provenance);
        return s;
    }

    private static MapbiomasSusceptibility susceptibility(String flag) {
        return new MapbiomasSusceptibility(0.6, 0.7, 0.4, "v1", flag, 0.0);
    }

    @Test
    void year_changesOnlyLandCoverYearAndShares_notSeriesFireOrIndices() {
        ComunaMapbiomasStats s = stats(true);
        Optional<MapbiomasSusceptibility> susc = Optional.of(susceptibility(null));

        MapbiomasComunaDetailDTO d2003 = MapbiomasComunaDetailBuilder.build(s, 2003, susc, 0.35, CITATION);
        MapbiomasComunaDetailDTO d2024 = MapbiomasComunaDetailBuilder.build(s, 2024, susc, 0.35, CITATION);

        assertThat(d2003.landCover().year()).isEqualTo(2003);
        assertThat(d2003.landCover().shares().get(1).share()).isEqualTo(0.3);
        assertThat(d2024.landCover().shares().get(1).share()).isEqualTo(0.4);
        assertThat(d2003.landCover().series().classes().get("59")).hasSize(26);
        assertThat(d2003.landCover().series()).isEqualTo(d2024.landCover().series());
        assertThat(d2003.fire()).isEqualTo(d2024.fire());
        assertThat(d2003.indices()).isEqualTo(d2024.indices());
    }

    @Test
    void shares_areOrderedDescendingWithOfficialLabelsAndSumAtMostOne() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2003, Optional.of(susceptibility(null)), 0.0, CITATION);

        assertThat(d.landCover().shares()).extracting(MapbiomasComunaDetailDTO.ClassShare::code)
            .containsExactly(59, 12);
        assertThat(d.landCover().unobservedShare()).isEqualTo(0.1);
        assertThat(d.landCover().shares().get(0).label()).isEqualTo("Bosque Primario");
        double sum = d.landCover().shares().stream().mapToDouble(MapbiomasComunaDetailDTO.ClassShare::share).sum();
        assertThat(sum).isLessThanOrEqualTo(1.0 + 1e-9);
        assertThat(d.landCover().series().labels().get("12")).isEqualTo("Pastizal");
    }

    @Test
    void indices_reportConfiguredWeightHonestly_wmZeroMeansNotInScore() {
        Optional<MapbiomasSusceptibility> susc = Optional.of(susceptibility(null));

        MapbiomasComunaDetailDTO off = MapbiomasComunaDetailBuilder.build(stats(true), 2024, susc, 0.0, CITATION);
        MapbiomasComunaDetailDTO on = MapbiomasComunaDetailBuilder.build(stats(true), 2024, susc, 0.35, CITATION);

        assertThat(off.indices().weight()).isEqualTo(0.0);
        assertThat(off.indices().enteredInScore()).isFalse();
        assertThat(off.indices().weightedComponent()).isEqualTo(0.0);
        assertThat(on.indices().sMapbiomas()).isEqualTo(0.6);
        assertThat(on.indices().fuelIndex()).isEqualTo(0.7);
        assertThat(on.indices().historyIndex()).isEqualTo(0.4);
        assertThat(on.indices().enteredInScore()).isTrue();
        assertThat(on.indices().weightedComponent()).isEqualTo(0.21);
    }

    @Test
    void unavailableMapbiomas_neverEntersTheScoreEvenWithPositiveWeight() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024,
            Optional.of(susceptibility(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE)), 0.35, CITATION);

        assertThat(d.indices().enteredInScore()).isFalse();
        assertThat(d.indices().weightedComponent()).isEqualTo(0.0);
        assertThat(d.indices().qualityFlag()).isEqualTo(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE);
        // The 0.0 score is a placeholder, never a claim: it must not be reported as a real S_mb.
        assertThat(d.indices().sMapbiomas()).isNull();
        assertThat(d.indices().fuelIndex()).isNull();
        assertThat(d.indices().historyIndex()).isNull();
    }

    @Test
    void partialFlag_withPositiveWeight_stillEntersTheScore() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024,
            Optional.of(new MapbiomasSusceptibility(0.4, null, 0.4, "v1",
                MapbiomasSusceptibility.MAPBIOMAS_FUEL_UNAVAILABLE, null)), 0.35, CITATION);

        assertThat(d.indices().enteredInScore()).isTrue();
        assertThat(d.indices().sMapbiomas()).isEqualTo(0.4);
        assertThat(d.indices().weightedComponent()).isEqualTo(0.14);
    }

    @Test
    void weightedComponent_isRoundedToFourDecimalsLikeTheRiskService() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024,
            Optional.of(new MapbiomasSusceptibility(0.123456, 0.1, 0.1, "v1", null, 0.0)), 0.35, CITATION);

        assertThat(d.indices().weightedComponent()).isEqualTo(0.0432);
    }

    @Test
    void invalidIndices_degradeWithoutDroppingTheRestOfTheResponse() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024, Optional.empty(), 0.35, CITATION, true);

        assertThat(d.indices().qualityFlag()).isEqualTo(MapbiomasComunaDetailBuilder.DATA_INVALID_FLAG);
        assertThat(d.indices().enteredInScore()).isFalse();
        assertThat(d.indices().sMapbiomas()).isNull();
        assertThat(d.indices().fuelIndex()).isNull();
        assertThat(d.indices().historyIndex()).isNull();
        assertThat(d.indices().weightedComponent()).isEqualTo(0.0);
        assertThat(d.fire().available()).isTrue();
        assertThat(d.landCover().available()).isTrue();
    }

    @Test
    void corruptLandCoverSeries_degradeToUnavailableInsteadOfThrowing() {
        for (String badKey : new String[] {"999", "abc"}) {
            ComunaMapbiomasStats s = stats(true);
            s.getLandCover().getSharesByYear().getClasses().put(badKey, new int[26]);
            MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(s, 2024, Optional.empty(), 0.0, CITATION);
            assertThat(d.landCover().available()).as(badKey).isFalse();
            assertThat(d.landCover().shares()).isNull();
            assertThat(d.landCover().series()).isNull();
            assertThat(d.fire().available()).isTrue();
        }
        ComunaMapbiomasStats shortSeries = stats(true);
        shortSeries.getLandCover().getSharesByYear().getClasses().put("12", new int[3]);
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(shortSeries, 2024, Optional.empty(), 0.0, CITATION);
        assertThat(d.landCover().available()).isFalse();
        assertThat(d.fire().available()).isTrue();
    }

    @Test
    void yearOutsideStoredSeries_isUnavailableButKeepsTheSeriesForCharting() {
        ComunaMapbiomasStats s = stats(true);
        s.getLandCover().getSharesByYear().setFirstYear(2000);
        s.getLandCover().getSharesByYear().setLastYear(2025);

        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(s, 1999, Optional.empty(), 0.0, CITATION);

        assertThat(d.landCover().available()).isFalse();
        assertThat(d.landCover().year()).isEqualTo(1999);
        assertThat(d.landCover().shares()).isNull();
        assertThat(d.landCover().unobservedShare()).isNull();
        assertThat(d.landCover().series()).isNotNull();
        assertThat(d.landCover().series().classes().get("59")).hasSize(26);
    }

    @Test
    void yearWithNoObservedClass_isUnavailableAndReportsOnlyUnobservedShare() {
        ComunaMapbiomasStats s = stats(true);
        Map<String, int[]> classes = s.getLandCover().getSharesByYear().getClasses();
        classes.get("59")[4] = 0;
        classes.get("12")[4] = 0;
        classes.get("27")[4] = 10000;

        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(s, 2003, Optional.empty(), 0.0, CITATION);

        assertThat(d.landCover().available()).isFalse();
        assertThat(d.landCover().shares()).isNull();
        assertThat(d.landCover().unobservedShare()).isEqualTo(1.0);
        assertThat(d.landCover().series()).isNotNull();
    }

    @Test
    void fireUnavailable_keepsKeysAsNullAndFlagsHistoryUnavailable() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(false), 2024, Optional.of(susceptibility(null)), 0.0, CITATION);

        assertThat(d.fireHistoryAvailable()).isFalse();
        assertThat(d.fire().available()).isFalse();
        assertThat(d.fire().burnedHaByYear()).isNull();
        assertThat(d.fire().burnedFraction()).isNull();
        assertThat(d.fire().yearLastFire()).isNull();
    }

    @Test
    void attribution_isPresentAndExposesNoRawSources() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024, Optional.empty(), 0.0, CITATION);

        assertThat(d.attribution().text()).isEqualTo(CITATION);
        assertThat(d.attribution().license()).isEqualTo("CC BY 4.0");
        assertThat(d.attribution().sourceUrl()).isNotBlank();
        assertThat(d.attribution().dataVersion()).isEqualTo("v1");
        assertThat(d.attribution().downloadDate()).isEqualTo("2026-09-21");
        assertThat(d.indices().sMapbiomas()).isNull();
    }

    @Test
    void dtoExposesOnlyTheDocumentedWhitelist_noIdOrInternalFields() {
        assertThat(Arrays.stream(MapbiomasComunaDetailDTO.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactlyInAnyOrder("comunaId", "nombreComuna", "regionId", "dataVersion", "attribution",
                "landCover", "fire", "fireHistoryAvailable", "indices");
    }

    @Test
    void comunaIdValidation_acceptsOnlyGid3Format() {
        assertThat(MapbiomasComunaId.isValid("CHL.6.1.1_1")).isTrue();
        assertThat(MapbiomasComunaId.isValid("CHL.13.3.5_1")).isTrue();
        for (String bad : new String[] {"", " ", "x", "chl.6.1.1_1", "CHL.6.1.1_1 ", "CHL.6.1.1_1\n",
            "CHL.6.1.1_2", "CHL.6.1_1", "CHL.600.1.1_1", "CHL.6.1.1_1/../x", "CHL.6.1.1_1%00", "$ne"}) {
            assertThat(MapbiomasComunaId.isValid(bad)).as(bad).isFalse();
        }
        assertThat(MapbiomasComunaId.isValid(null)).isFalse();
    }
}
