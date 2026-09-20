package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.model.Region;
import com.simfat.backend.model.RiskLevel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/** Anonymous read contract for GET /api/alerts/public: minimized DTO, bounded window, no Mongo required. */
class HeatAlertPublicEndpointIntegrationTest extends MongoFreeWebTestSupport {

    private static HeatAlertEvent event(String id, String comunaId, double lat, double lon) {
        HeatAlertEvent e = new HeatAlertEvent();
        e.setId(id);
        e.setRegionId("biobio");
        e.setComunaId(comunaId);
        e.setFechaEvento(LocalDateTime.of(2026, 1, 15, 13, 45));
        e.setNivelRiesgo(RiskLevel.ALTO);
        e.setLatitud(lat);
        e.setLongitud(lon);
        e.setFuente("NASA_FIRMS");
        e.setDescripcion("internal detail");
        return e;
    }

    private static ComunaInfo comuna(String id, String nombre) {
        ComunaInfo c = new ComunaInfo();
        c.setId(id);
        c.setNombre(nombre);
        c.setRegionId("biobio");
        return c;
    }

    private void stubRegion() {
        Region region = new Region();
        region.setId("biobio");
        region.setNombre("Biobio");
        when(regionRepository.findById("biobio")).thenReturn(Optional.of(region));
    }

    @Test
    void anonymousGetReturnsMinimizedPayloadWithoutForbiddenKeys() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("c1", "Tome")));
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of(event("a1", "c1", -36.123456, -72.987654)));

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").doesNotExist())
            .andExpect(jsonPath("$.data[0].fuente").doesNotExist())
            .andExpect(jsonPath("$.data[0].descripcion").doesNotExist())
            .andExpect(jsonPath("$.data[0].comunaId").doesNotExist())
            .andExpect(jsonPath("$.data[0].regionId").value("biobio"))
            .andExpect(jsonPath("$.data[0].fecha").value("2026-01-15"))
            .andExpect(jsonPath("$.data[0].nivelRiesgo").value("ALTO"));
    }

    @Test
    void coordinatesAreRoundedToTwoDecimals() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio")).thenReturn(List.of());
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of(event("a1", null, -36.126, -72.984)));

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].lat").value(-36.13))
            .andExpect(jsonPath("$.data[0].lon").value(-72.98));
    }

    @Test
    void comunaLabelComesFromPersistedComunaIdWithSingleBatchedLookup() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio"))
            .thenReturn(List.of(comuna("c1", "Tome"), comuna("c2", "Penco")));
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of(
                event("a1", "c1", -36.1, -72.9),
                event("a2", "c2", -36.2, -72.8),
                event("a3", "c1", -36.3, -72.7)));

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].comuna").value("Tome"))
            .andExpect(jsonPath("$.data[0].comunaLevel").value(true))
            .andExpect(jsonPath("$.data[1].comuna").value("Penco"))
            .andExpect(jsonPath("$.data[2].comuna").value("Tome"));

        // No N+1: one comuna lookup regardless of how many events, never per event.
        verify(comunaInfoRepository, times(1)).findNamesByRegionId("biobio");
        verify(comunaInfoRepository, never()).findById(any());
    }

    @Test
    void nullOrUnknownComunaIdFallsBackToRegionLabel() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("c1", "Tome")));
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of(event("a1", null, -36.1, -72.9), event("a2", "gone", -36.2, -72.8)));

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].comuna").value("Biobio"))
            .andExpect(jsonPath("$.data[0].comunaLevel").value(false))
            .andExpect(jsonPath("$.data[1].comuna").value("Biobio"))
            .andExpect(jsonPath("$.data[1].comunaLevel").value(false));
    }

    @Test
    void emptyResultIsOkAndPublicPathIsNotSwallowedByIdMapping() throws Exception {
        stubRegion();
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
        verify(heatAlertEventRepository, never()).findById("public");
    }

    @Test
    void defaultWindowIsTheLast30Days() throws Exception {
        stubRegion();
        LocalDate before = LocalDate.now();

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")).andExpect(status().isOk());

        LocalDate after = LocalDate.now();
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(heatAlertEventRepository).findByRegionIdInWindowNewestFirst(
            eq("biobio"), from.capture(), to.capture(), any(Pageable.class));
        assertThat(from.getValue().toLocalDate()).isIn(before.minusDays(30), after.minusDays(30));
        assertThat(to.getValue().toLocalDate()).isIn(before.plusDays(1), after.plusDays(1));
    }

    @Test
    void spanAboveNinetyDaysIsRejectedWithApiError() throws Exception {
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")
                .param("from", "2026-01-01").param("to", "2026-04-02"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400));
        verify(heatAlertEventRepository, never())
            .findByRegionIdInWindowNewestFirst(any(), any(), any(), any());
    }

    @Test
    void spanOfExactlyNinetyDaysIsAccepted() throws Exception {
        stubRegion();
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")
                .param("from", "2026-01-01").param("to", "2026-04-01"))
            .andExpect(status().isOk());
    }

    @Test
    void invertedRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")
                .param("from", "2026-03-10").param("to", "2026-03-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void resultIsCappedAtOneThousandNewestFirst() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio")).thenReturn(List.of());
        List<HeatAlertEvent> many = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            many.add(event("a" + i, null, -36.1, -72.9));
        }
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(many);

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1000));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(heatAlertEventRepository).findByRegionIdInWindowNewestFirst(
            eq("biobio"), any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(1000);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void windowIsHalfOpenFromStartOfFromDayToStartOfDayAfterTo() throws Exception {
        stubRegion();

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")
                .param("from", "2026-03-01").param("to", "2026-03-05"))
            .andExpect(status().isOk());

        verify(heatAlertEventRepository).findByRegionIdInWindowNewestFirst(
            eq("biobio"), eq(LocalDateTime.of(2026, 3, 1, 0, 0)), eq(LocalDateTime.of(2026, 3, 6, 0, 0)), any(Pageable.class));
    }

    @Test
    void noComunaLookupWhenThereAreNoEvents() throws Exception {
        stubRegion();
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        verify(comunaInfoRepository, never()).findNamesByRegionId(any());
        verify(comunaInfoRepository, never()).findByRegionId(any());
    }

    @Test
    void publicPathUsesTheProjectedLookupNeverTheFullDocumentLoad() throws Exception {
        stubRegion();
        when(comunaInfoRepository.findNamesByRegionId("biobio")).thenReturn(List.of(comuna("c1", "Tome")));
        when(heatAlertEventRepository.findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any()))
            .thenReturn(List.of(event("a1", "c1", -36.1, -72.9)));

        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].comuna").value("Tome"));

        verify(comunaInfoRepository, times(1)).findNamesByRegionId("biobio");
        verify(comunaInfoRepository, never()).findByRegionId(any());
    }

    @Test
    void blankOrWhitespaceRegionIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/alerts/public").param("regionId", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("regionId")));
        mockMvc.perform(get("/api/alerts/public").param("regionId", "   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verify(heatAlertEventRepository, never()).findByRegionIdInWindowNewestFirst(any(), any(), any(), any());
    }

    @Test
    void regionIdIsTrimmedBeforeTheQuery() throws Exception {
        stubRegion();

        mockMvc.perform(get("/api/alerts/public").param("regionId", "  biobio  ")).andExpect(status().isOk());

        verify(heatAlertEventRepository).findByRegionIdInWindowNewestFirst(eq("biobio"), any(), any(), any());
    }

    @Test
    void extremeOrOutOfRangeYearsAreBadRequestNotServerErrors() throws Exception {
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio").param("to", "-999999999-01-01"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio")
                .param("from", "+999999999-12-01").param("to", "+999999999-12-31"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/alerts/public").param("regionId", "biobio").param("from", "1999-12-31"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
