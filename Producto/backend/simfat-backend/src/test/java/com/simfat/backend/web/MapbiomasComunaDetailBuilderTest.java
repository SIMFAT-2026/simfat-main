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
            .containsExactly(59, 12, 27);
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
        assertThat(off.indices().scoreContribution()).isEqualTo(0.0);
        assertThat(on.indices().sMapbiomas()).isEqualTo(0.6);
        assertThat(on.indices().fuelIndex()).isEqualTo(0.7);
        assertThat(on.indices().historyIndex()).isEqualTo(0.4);
        assertThat(on.indices().enteredInScore()).isTrue();
        assertThat(on.indices().scoreContribution()).isEqualTo(0.35 * 0.6);
    }

    @Test
    void unavailableMapbiomas_neverEntersTheScoreEvenWithPositiveWeight() {
        MapbiomasComunaDetailDTO d = MapbiomasComunaDetailBuilder.build(
            stats(true), 2024,
            Optional.of(susceptibility(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE)), 0.35, CITATION);

        assertThat(d.indices().enteredInScore()).isFalse();
        assertThat(d.indices().scoreContribution()).isEqualTo(0.0);
        assertThat(d.indices().qualityFlag()).isEqualTo(MapbiomasSusceptibility.MAPBIOMAS_UNAVAILABLE);
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
