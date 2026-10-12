package com.simfat.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Anonymous MockMvc coverage of {@code GET /api/territory/public/mapbiomas/{comunaId}} (slice
 * S3-5). No {@code @WithMockUser} on purpose: the route must work without a token.
 */
class MapbiomasComunaDetailControllerTest extends MongoFreeWebTestSupport {

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.simfat.backend.service.mapbiomas.MapbiomasSusceptibilityService susceptibilityService;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.simfat.backend.repository.ComunaRiskSnapshotRepository snapshotRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private MapbiomasComunaDetailController controller;

    private ComunaMapbiomasStats seeded;

    private static final String ID = "CHL.6.1.1_1";
    private static final String URL = "/api/territory/public/mapbiomas/" + ID;

    @BeforeEach
    void seed() {
        int[] forest = new int[26];
        int[] grass = new int[26];
        for (int i = 0; i < 26; i++) {
            forest[i] = 6000;
            grass[i] = i == 4 ? 4000 : 3000; // 2003 differs from every other year
        }
        ComunaMapbiomasStats.SharesByYear series = new ComunaMapbiomasStats.SharesByYear();
        series.setFirstYear(1999);
        series.setLastYear(2024);
        series.setUnit("bp");
        series.setClasses(Map.of("59", forest, "12", grass));
        ComunaMapbiomasStats.LandCover landCover = new ComunaMapbiomasStats.LandCover();
        landCover.setReferenceYear(2024);
        landCover.setSharesByClass(Map.of("59", 0.6, "12", 0.4));
        landCover.setSharesByYear(series);
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(true);
        fire.setBurnedHaByYear(Map.of("2017", 10.0));
        fire.setBurnedFractionByYear(Map.of("2017", 0.01));
        fire.setBurnedFraction(0.01);
        fire.setBurnedFractionPct(0.5);
        fire.setFrequencyMean(1.0);
        fire.setYearLastFire(2017);
        fire.setYearsSinceLastFire(8);
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setId(ID + "|v");
        stats.setComunaId(ID);
        stats.setRegionId("biobio");
        stats.setNombreComuna("Concepcion");
        stats.setDataVersion("v");
        stats.setLandCover(landCover);
        stats.setFire(fire);
        ComunaMapbiomasStats.Provenance provenance = new ComunaMapbiomasStats.Provenance();
        provenance.setDownloadDate("2026-09-21");
        provenance.setSources(java.util.List.of("https://secret.example/signed"));
        stats.setProvenance(provenance);
        seeded = stats;
        controller.clearDefaultYearCache();
        when(comunaMapbiomasStatsRepository.findByDataVersion(any())).thenReturn(java.util.List.of(stats));
        when(comunaMapbiomasStatsRepository.findByComunaIdAndDataVersion(anyString(), any()))
            .thenReturn(Optional.empty());
        when(comunaMapbiomasStatsRepository.findByComunaIdAndDataVersion(
            org.mockito.ArgumentMatchers.eq(ID), any())).thenReturn(Optional.of(stats));
    }

    @Test
    void anonymousKnownComuna_returns200WithWhitelistedShape() throws Exception {
        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", is("public, max-age=300")))
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.comunaId", is(ID)))
            .andExpect(jsonPath("$.data.landCover.year", is(2024)))
            .andExpect(jsonPath("$.data.landCover.shares", hasSize(2)))
            .andExpect(jsonPath("$.data.landCover.series.classes.59", hasSize(26)))
            .andExpect(jsonPath("$.data.attribution.license", is("CC BY 4.0")))
            .andExpect(jsonPath("$.data.attribution.text", not("")))
            .andExpect(jsonPath("$.data.attribution.text", containsString("Colección 2")))
            .andExpect(jsonPath("$.data.attribution.text", containsString("Colección 1")))
            .andExpect(jsonPath("$.data.fire.burnedHaByYear.2017", is(10.0)))
            .andExpect(jsonPath("$.data.fireHistoryAvailable", is(true)))
            .andExpect(jsonPath("$.data.indices.weight", is(0.0)))
            .andExpect(jsonPath("$.data.indices.enteredInScore", is(false)))
            .andExpect(jsonPath("$.data.id").doesNotExist())
            .andExpect(jsonPath("$.data._id").doesNotExist())
            .andExpect(jsonPath("$.data.provenance").doesNotExist())
            .andExpect(jsonPath("$.data.scriptVersion").doesNotExist());
    }

    @Test
    void year_changesOnlyShares_indicesAndFireAreIdentical() throws Exception {
        String a = mockMvc.perform(get(URL).param("year", "2003")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.landCover.year", is(2003)))
            .andExpect(jsonPath("$.data.landCover.shares[1].share", is(0.4)))
            .andReturn().getResponse().getContentAsString();
        String b = mockMvc.perform(get(URL).param("year", "2024")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.landCover.shares[1].share", is(0.3)))
            .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
            com.jayway.jsonpath.JsonPath.<Object>read(a, "$.data.indices").toString())
            .isEqualTo(com.jayway.jsonpath.JsonPath.<Object>read(b, "$.data.indices").toString());
        org.assertj.core.api.Assertions.assertThat(
            com.jayway.jsonpath.JsonPath.<Object>read(a, "$.data.fire").toString())
            .isEqualTo(com.jayway.jsonpath.JsonPath.<Object>read(b, "$.data.fire").toString());
    }

    @Test
    void yearBounds_areInclusive() throws Exception {
        mockMvc.perform(get(URL).param("year", "1999")).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("year", "2024")).andExpect(status().isOk());
        for (String bad : new String[] {"1998", "2025", "abc", "2020.5", "1e3", "-1", "", "20 20", "99999999999"}) {
            mockMvc.perform(get(URL).param("year", bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
        }
    }

    @Test
    void malformedComunaId_is400_neverEchoedAndNeverReachesTheRepository() throws Exception {
        for (String bad : new String[] {"x", "chl.6.1.1_1", "CHL.6.1.1_2", "%24ne"}) {
            String body = mockMvc.perform(get("/api/territory/public/mapbiomas/" + bad))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
            // The shared error envelope reports the request path; the message must not echo input.
            if (!body.isBlank()) {
                org.assertj.core.api.Assertions.assertThat(
                    com.jayway.jsonpath.JsonPath.<String>read(body, "$.message")).doesNotContain(bad);
            }
        }
        // A NUL byte is stopped by the servlet firewall (empty 400 body); still never a 200/500.
        mockMvc.perform(get("/api/territory/public/mapbiomas/CHL.6.1.1_1%00"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(comunaMapbiomasStatsRepository);
    }

    @Test
    void unknownButWellFormedComuna_is404WithSpanishMessageAndNoEcho() throws Exception {
        mockMvc.perform(get("/api/territory/public/mapbiomas/CHL.99.9.9_1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", is("No hay datos de MapBiomas para la comuna solicitada")))
            .andExpect(jsonPath("$.message", not(containsString("CHL.99.9.9_1"))))
            .andExpect(content().string(not(containsString("at com.simfat"))));
    }

    @Test
    void omittedYear_usesTheDataDerivedDefaultLikeTheLandCoverLayer() throws Exception {
        // Latest observed year in the loaded dataVersion is 2020 (series 1999-2020).
        ComunaMapbiomasStats.SharesByYear series = seeded.getLandCover().getSharesByYear();
        series.setLastYear(2020);
        int[] forest = new int[22];
        java.util.Arrays.fill(forest, 5000);
        series.setClasses(Map.of("59", forest));

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.landCover.year", is(2020)))
            .andExpect(jsonPath("$.data.landCover.available", is(true)));
        mockMvc.perform(get(URL).param("year", "2024"))
            .andExpect(jsonPath("$.data.landCover.year", is(2024)))
            .andExpect(jsonPath("$.data.landCover.available", is(false)));
    }

    @Test
    void invalidSusceptibilityData_degradesIndicesButKeepsTheRestOf200() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad seed"))
            .when(susceptibilityService).forComuna(ID);

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.indices.qualityFlag", is("MAPBIOMAS_DATA_INVALID")))
            .andExpect(jsonPath("$.data.indices.enteredInScore", is(false)))
            .andExpect(jsonPath("$.data.indices.sMapbiomas").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.fire.available", is(true)))
            .andExpect(jsonPath("$.data.landCover.available", is(true)));
    }

    @Test
    void corruptLandCoverSeries_is200WithLandCoverUnavailable() throws Exception {
        seeded.getLandCover().getSharesByYear().setClasses(Map.of("999", new int[26]));

        mockMvc.perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.landCover.available", is(false)))
            .andExpect(jsonPath("$.data.fire.available", is(true)));
    }

    @Test
    void effectiveWeight_comesFromThePersistedSnapshotWhenOneExists() throws Exception {
        org.mockito.Mockito.doReturn(Optional.of(new com.simfat.backend.service.mapbiomas.MapbiomasSusceptibility(
            0.6, 0.7, 0.4, "v", null, 0.0))).when(susceptibilityService).forComuna(ID);
        com.simfat.backend.model.ComunaRiskSnapshot snapshot = new com.simfat.backend.model.ComunaRiskSnapshot();
        snapshot.setMapbiomasWeight(0.2);
        when(snapshotRepository.findTopByComunaIdOrderByComputedAtDesc(ID)).thenReturn(Optional.of(snapshot));

        mockMvc.perform(get(URL))
            .andExpect(jsonPath("$.data.indices.weight", is(0.2)))
            .andExpect(jsonPath("$.data.indices.enteredInScore", is(true)))
            .andExpect(jsonPath("$.data.indices.weightedComponent", is(0.12)));
    }

    @Test
    void nonGetMethods_areRejectedWith401() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
