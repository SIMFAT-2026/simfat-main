package com.simfat.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.ResultActions;

/** The anonymous REPORTS layer must only ever contain moderated (VALIDADO) reports. */
class TerritoryControllerPublicReportsIntegrationTest extends MongoFreeWebTestSupport {

    private static CitizenReport report(String id, CitizenReportStatus status) {
        CitizenReport r = new CitizenReport();
        r.setId(id);
        r.setRegionId("biobio");
        r.setCategory("IGNICION_POTENCIAL");
        r.setDescription("free text " + id);
        r.setLatitude(-36.126);
        r.setLongitude(-72.984);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    void publicLayerContainsOnlyValidatedReports() throws Exception {
        // Everything the unfiltered legacy query would return, i.e. the leak.
        when(citizenReportRepository.findByRegionIdAndCreatedAtBetween(eq("biobio"), any(), any()))
            .thenReturn(List.of(
                report("v1", CitizenReportStatus.VALIDADO),
                report("r1", CitizenReportStatus.RECIBIDO),
                report("d1", CitizenReportStatus.DESCARTADO)));
        when(citizenReportRepository.findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(CitizenReportStatus.VALIDADO), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(report("v1", CitizenReportStatus.VALIDADO)));

        mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "biobio").param("indicators", "REPORTS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.REPORTS.features.length()").value(1));

        verify(citizenReportRepository, never()).findByRegionIdAndCreatedAtBetween(any(), any(), any());
    }

    @Test
    void publicLayerFeaturesCarryNoFreeTextIdOrReporterData() throws Exception {
        when(citizenReportRepository.findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(CitizenReportStatus.VALIDADO), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(report("v1", CitizenReportStatus.VALIDADO)));

        mockMvc.perform(get("/api/territory/public/layers")
                .param("regionId", "biobio").param("indicators", "REPORTS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].id").value("public-report-0"))
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].properties.label").value("Reporte ciudadano"))
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].properties.descripcion").doesNotExist())
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].properties.phone").doesNotExist())
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].properties.email").doesNotExist())
            .andExpect(jsonPath("$.data.layers.REPORTS.features[0].geometry.coordinates[0]").value(-72.98));
    }

    private ResultActions publicReports(String from, String to) throws Exception {
        var request = get("/api/territory/public/layers").param("regionId", "biobio").param("indicators", "REPORTS");
        if (from != null) {
            request.param("from", from);
        }
        if (to != null) {
            request.param("to", to);
        }
        return mockMvc.perform(request);
    }

    @Test
    void spanAboveNinetyDaysIsRejected() throws Exception {
        publicReports("2026-01-01", "2026-04-02").andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verify(citizenReportRepository, never())
            .findByRegionIdAndStatusInWindowNewestFirst(any(), any(), any(), any(), any());
    }

    @Test
    void invertedRangeIsRejected() throws Exception {
        publicReports("2026-03-10", "2026-03-01").andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void extremeOrOutOfRangeYearsAreBadRequest() throws Exception {
        publicReports(null, "-999999999-01-01").andExpect(status().isBadRequest());
        publicReports("+999999999-12-01", "+999999999-12-31").andExpect(status().isBadRequest());
        publicReports("1999-12-31", null).andExpect(status().isBadRequest());
    }

    @Test
    void normalThreeDayRequestUsesHalfOpenWindowAndTheCap() throws Exception {
        publicReports("2026-03-01", "2026-03-03").andExpect(status().isOk());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(citizenReportRepository).findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(CitizenReportStatus.VALIDADO),
            eq(LocalDateTime.of(2026, 3, 1, 0, 0)), eq(LocalDateTime.of(2026, 3, 4, 0, 0)), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void absentBoundsKeepTheLastSevenDaysDefault() throws Exception {
        LocalDate before = LocalDate.now();

        publicReports(null, null).andExpect(status().isOk());

        LocalDate after = LocalDate.now();
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(citizenReportRepository).findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(CitizenReportStatus.VALIDADO), from.capture(), end.capture(), any(Pageable.class));
        assertThat(from.getValue().toLocalDate()).isIn(before.minusDays(7), after.minusDays(7));
        assertThat(end.getValue().toLocalDate()).isIn(before.plusDays(1), after.plusDays(1));
    }

    @Test
    void resultIsCappedAtFiveHundred() throws Exception {
        List<CitizenReport> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            many.add(report("v" + i, CitizenReportStatus.VALIDADO));
        }
        when(citizenReportRepository.findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(CitizenReportStatus.VALIDADO), any(), any(), any(Pageable.class))).thenReturn(many);

        publicReports(null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.layers.REPORTS.features.length()").value(500));
    }
}
