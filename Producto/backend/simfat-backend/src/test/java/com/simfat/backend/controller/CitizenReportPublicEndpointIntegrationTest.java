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

/** Anonymous read contract for GET /api/citizen-reports/public: only moderated (VALIDADO) reports inside a bounded window. */
class CitizenReportPublicEndpointIntegrationTest extends MongoFreeWebTestSupport {

    private static final CitizenReportStatus V = CitizenReportStatus.VALIDADO;

    private static CitizenReport report(String id, CitizenReportStatus status) {
        CitizenReport r = new CitizenReport();
        r.setId(id);
        r.setRegionId("biobio");
        r.setComunaId("c1");
        r.setCategory("IGNICION_POTENCIAL");
        r.setSubCategory("Quema de basura");
        r.setDescription("description of " + id);
        r.setLatitude(-36.126);
        r.setLongitude(-72.984);
        r.setStatus(status);
        r.setPhotos(List.of("https://cdn.example/" + id + ".jpg"));
        r.setCreatedAt(LocalDateTime.of(2026, 2, 3, 10, 30));
        r.setValidatedAt(LocalDateTime.of(2026, 2, 4, 9, 0));
        r.setDiscardReason("internal reason");
        return r;
    }

    private void stubRegional(List<CitizenReport> result) {
        when(citizenReportRepository.findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(V), any(), any(), any())).thenReturn(result);
    }

    private void stubNational(List<CitizenReport> result) {
        when(citizenReportRepository.findByStatusInWindowNewestFirst(
            eq(V), any(), any(), any())).thenReturn(result);
    }

    @Test
    void onlyValidatedReportsAreQueriedAndUnboundedQueriesAreNeverUsed() throws Exception {
        stubRegional(List.of(report("v1", V)));

        mockMvc.perform(get("/api/citizen-reports/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].description").value("description of v1"));

        verify(citizenReportRepository, never()).findAll();
        verify(citizenReportRepository, never()).findByStatus(any());
        verify(citizenReportRepository, never()).findByRegionIdAndStatus(any(), any());
        verify(citizenReportRepository, never())
            .findByStatusInWindowNewestFirst(any(), any(), any(), any());
    }

    @Test
    void withoutRegionFilterTheWindowedNationalQueryIsUsed() throws Exception {
        stubNational(List.of(report("v1", V)));

        mockMvc.perform(get("/api/citizen-reports/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
        verify(citizenReportRepository, never()).findByStatus(any());
    }

    @Test
    void blankRegionIdIsTreatedAsAbsentExplicitly() throws Exception {
        stubNational(List.of(report("v1", V)));

        mockMvc.perform(get("/api/citizen-reports/public").param("regionId", "   "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        verify(citizenReportRepository, never())
            .findByRegionIdAndStatusInWindowNewestFirst(any(), any(), any(), any(), any());
        verify(citizenReportRepository, never()).findByRegionIdAndStatus(any(), any());
    }

    @Test
    void payloadHasOnlyAllowedKeysAndTwoDecimalCoordinates() throws Exception {
        stubNational(List.of(report("v1", V)));

        mockMvc.perform(get("/api/citizen-reports/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].category").value("IGNICION_POTENCIAL"))
            .andExpect(jsonPath("$.data[0].subCategory").value("Quema de basura"))
            .andExpect(jsonPath("$.data[0].createdAt").value("2026-02-03"))
            .andExpect(jsonPath("$.data[0].photos[0]").value("https://cdn.example/v1.jpg"))
            .andExpect(jsonPath("$.data[0].lat").value(-36.13))
            .andExpect(jsonPath("$.data[0].lon").value(-72.98))
            .andExpect(jsonPath("$.data[0].id").doesNotExist())
            .andExpect(jsonPath("$.data[0].status").doesNotExist())
            .andExpect(jsonPath("$.data[0].discardReason").doesNotExist())
            .andExpect(jsonPath("$.data[0].comunaId").doesNotExist())
            .andExpect(jsonPath("$.data[0].validatedAt").doesNotExist())
            .andExpect(jsonPath("$.data[0].latitude").doesNotExist())
            .andExpect(jsonPath("$.data[0].longitude").doesNotExist());
    }

    @Test
    void emptyResultIsOk() throws Exception {
        stubNational(List.of());

        mockMvc.perform(get("/api/citizen-reports/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void defaultWindowIsTheLast30Days() throws Exception {
        LocalDate before = LocalDate.now();

        mockMvc.perform(get("/api/citizen-reports/public")).andExpect(status().isOk());

        LocalDate after = LocalDate.now();
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(citizenReportRepository).findByStatusInWindowNewestFirst(
            eq(V), from.capture(), to.capture(), any(Pageable.class));
        assertThat(from.getValue().toLocalDate()).isIn(before.minusDays(30), after.minusDays(30));
        assertThat(to.getValue().toLocalDate()).isIn(before.plusDays(1), after.plusDays(1));
    }

    @Test
    void spanAboveNinetyDaysAndInvertedRangeAreRejected() throws Exception {
        mockMvc.perform(get("/api/citizen-reports/public").param("from", "2026-01-01").param("to", "2026-04-02"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/citizen-reports/public").param("from", "2026-03-10").param("to", "2026-03-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/citizen-reports/public").param("from", "2026-01-01").param("to", "2026-04-01"))
            .andExpect(status().isOk());
    }

    @Test
    void resultIsCappedAtFiveHundredNewestFirst() throws Exception {
        List<CitizenReport> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            many.add(report("v" + i, V));
        }
        stubRegional(many);

        mockMvc.perform(get("/api/citizen-reports/public").param("regionId", "biobio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(500));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(citizenReportRepository).findByRegionIdAndStatusInWindowNewestFirst(
            eq("biobio"), eq(V), any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void windowIsHalfOpenFromStartOfFromDayToStartOfDayAfterTo() throws Exception {
        mockMvc.perform(get("/api/citizen-reports/public").param("from", "2026-03-01").param("to", "2026-03-05"))
            .andExpect(status().isOk());

        verify(citizenReportRepository).findByStatusInWindowNewestFirst(
            eq(V), eq(LocalDateTime.of(2026, 3, 1, 0, 0)), eq(LocalDateTime.of(2026, 3, 6, 0, 0)), any(Pageable.class));
    }

    @Test
    void extremeOrOutOfRangeYearsAreBadRequestNotServerErrors() throws Exception {
        mockMvc.perform(get("/api/citizen-reports/public").param("to", "-999999999-01-01"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/citizen-reports/public")
                .param("from", "+999999999-12-01").param("to", "+999999999-12-31"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/citizen-reports/public").param("from", "1999-12-31"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
