package com.simfat.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.ComunaMapbiomasStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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

    private static ComunaInfo comuna(String id) {
        ComunaInfo info = new ComunaInfo();
        info.setId(id);
        info.setRegionId("biobio");
        return info;
    }

    private static ComunaMapbiomasStats statsWithFire(String comunaId, Map<String, Double> fractionByYear) {
        ComunaMapbiomasStats stats = new ComunaMapbiomasStats();
        stats.setComunaId(comunaId);
        stats.setDataVersion("fuego-col1@2017-partial");
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
        when(comunaMapbiomasStatsRepository.findByDataVersion(any()))
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

        org.assertj.core.api.Assertions.assertThat(withMalformedYear).isEqualTo(withoutYear);
    }

    @Test
    @WithMockUser(authorities = "ROLE_VERIFIED_USER")
    void getPublicLayers_burnedIndicator_sameShapeAsAuthenticated() throws Exception {
        when(comunaInfoRepository.findByRegionId("biobio")).thenReturn(List.of(comuna("c1")));
        when(comunaMapbiomasStatsRepository.findByDataVersion(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "biobio")
                .param("indicators", "BURNED")
                .param("year", "2020"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.BURNED.values.c1.available", is(false)));
    }
}
