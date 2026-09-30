package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Task 2b2a.2 (design D5): BURNED value map with a validated, bounded {@code year} parameter.
 *
 * <p><b>Execution note:</b> like every other {@code @SpringBootTest} class in this module, this
 * test requires a live Mongo connection to boot the application context (confirmed independently
 * of this change: {@code SecurityDefaultDenyContractTest} fails the same way in this environment
 * with no production code touched). It could NOT be executed in the apply session that wrote it;
 * it is written so a Mongo-available run (CI, or a developer machine with
 * {@code simfat-mongo-test}) exercises the wiring end to end. The underlying value-map logic is
 * independently covered, without Mongo, by {@code MapbiomasBurnedLayerBuilderTest} and the year
 * bounds by {@code MapbiomasYearTest}.
 */
class TerritoryControllerMapbiomasYearTest extends MongoFreeWebTestSupport {

    @Autowired
    private TerritoryController territoryController;

    @BeforeEach
    void isolateLayerCache() {
        // The layer cache -- and, since Q28, the cached data-derived BURNED default year --
        // lives in the singleton controller; without this, a payload (or default year) cached
        // by one test (e.g. biobio|2020 with no comunas) leaks into the next.
        territoryController.clearMapbiomasLayerCache();
    }

    private static ComunaInfo comuna(String id) {
        ComunaInfo info = new ComunaInfo();
        info.setId(id);
        info.setRegionId("biobio");
        return info;
    }

    private static ComunaMapbiomasStats statsWithFire(String comunaId, Map<String, Double> fractionByYear) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        stats.setDataVersion("lulc-col2+fuego-col1@2017-partial");
        ComunaMapbiomasStats.Fire fire = new ComunaMapbiomasStats.Fire();
        fire.setAvailable(true);
        fire.setBurnedFractionByYear(fractionByYear);
        fire.setBurnedHaByYear(fractionByYear);
        stats.setFire(fire);
        return stats;
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_burnedWithYear_returnsAnnualFractionForThatYearOnly() throws Exception {
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna("c1")));
        Map<String, Double> byYear = new LinkedHashMap<>();
        byYear.put("2017", 0.05);
        byYear.put("2019", 0.0);
        when(comunaMapbiomasStatsRepository.findByRegionIdAndDataVersion(eq("biobio"), any()))
            .thenReturn(List.of(statsWithFire("c1", byYear)));

        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2017"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.BURNED.year", is(2017)))
            .andExpect(jsonPath("$.data.layers.BURNED.minYear", is(2013)))
            .andExpect(jsonPath("$.data.layers.BURNED.maxYear", is(2025)))
            .andExpect(jsonPath("$.data.layers.BURNED.values.c1.available", is(true)))
            .andExpect(jsonPath("$.data.layers.BURNED.values.c1.burnedFraction", is(0.05)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_burnedOmittedYear_defaultsToTheLatestYearWithData() throws Exception {
        // Decision Q28: with year omitted, the default is data-derived -- the latest year that
        // actually has burned data in the loaded dataVersion (here 2017), never the fixed
        // BURNED_MAX_YEAR constant (2025), which would render an empty map against the real,
        // still-partial seed.
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna("c1")));
        Map<String, Double> byYear = new LinkedHashMap<>();
        byYear.put("2015", 0.01);
        byYear.put("2017", 0.05);
        when(comunaMapbiomasStatsRepository.findByRegionIdAndDataVersion(eq("biobio"), any()))
            .thenReturn(List.of(statsWithFire("c1", byYear)));
        when(comunaMapbiomasStatsRepository.findByDataVersion(any()))
            .thenReturn(List.of(statsWithFire("c1", byYear)));

        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.BURNED.year", is(2017)))
            .andExpect(jsonPath("$.data.layers.BURNED.minYear", is(2013)))
            .andExpect(jsonPath("$.data.layers.BURNED.maxYear", is(2025)))
            .andExpect(jsonPath("$.data.layers.BURNED.values.c1.burnedFraction", is(0.05)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_burnedYearOutOfRange_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2026"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_burnedMalformedYear_returnsBadRequestNeverServerError() throws Exception {
        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_existingIndicatorsIgnoreYear_byteIdenticalWithAndWithoutIt() throws Exception {
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of());

        String withoutYear = mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "WIND,HUMIDITY,AIR_TEMP,SOIL_TEMP"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String withMalformedYear = mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "WIND,HUMIDITY,AIR_TEMP,SOIL_TEMP")
                .param("year", "not-a-year"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // timestamp and generatedAt are per-request clocks; everything else must be identical.
        // (Assertions below remove both before comparing.)
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode expected =
            (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(withoutYear);
        com.fasterxml.jackson.databind.node.ObjectNode actual =
            (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(withMalformedYear);
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get("data")).remove("generatedAt");
        ((com.fasterxml.jackson.databind.node.ObjectNode) actual.get("data")).remove("generatedAt");
        expected.remove("timestamp");
        actual.remove("timestamp");
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getPublicLayers_burnedIndicator_sameShapeAsAuthenticated() throws Exception {
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna("c1")));
        when(comunaMapbiomasStatsRepository.findByRegionIdAndDataVersion(eq("biobio"), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2020"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.BURNED.values.c1.available", is(false)));
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getLayers_burnedIncludesConfiguredCitation() throws Exception {
        // Judgment-day finding #2 (WARNING): design D5 requires `citation` on the BURNED payload.
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of());
        when(comunaMapbiomasStatsRepository.findByRegionIdAndDataVersion(eq("biobio"), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/territory/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2020"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.BURNED.citation").exists());
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getPublicLayers_garbageRegionId_returnsTheSamePayloadAsTheCanonicalDefaultRegion() throws Exception {
        // Judgment-day finding #1 (CRITICAL): a raw, unauthenticated regionId must normalize to
        // the same default region ("biobio") as an explicit request for it -- never mint a new
        // cache key/Mongo scan per garbage value.
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna("c1")));
        when(comunaMapbiomasStatsRepository.findByRegionIdAndDataVersion(eq("biobio"), any()))
            .thenReturn(List.of());

        String garbagePayload = mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "totally-unknown-garbage-region")
                .param("indicators", "BURNED")
                .param("year", "2020"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String canonicalPayload = mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2020"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode garbage =
            (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(garbagePayload);
        com.fasterxml.jackson.databind.node.ObjectNode canonical =
            (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(canonicalPayload);
        // The envelope's data.regionId echoes the raw request value for every indicator (pre-existing
        // behaviour, unchanged here); what must match is the BURNED layer itself.
        org.assertj.core.api.Assertions.assertThat(garbage.at("/data/layers"))
            .isEqualTo(canonical.at("/data/layers"));
    }
}
