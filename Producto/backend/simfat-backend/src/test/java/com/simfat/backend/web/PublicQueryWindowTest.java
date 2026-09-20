package com.simfat.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simfat.backend.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** The window is half-open: [from 00:00, (to + 1 day) 00:00). */
class PublicQueryWindowTest {

    private static boolean contains(PublicQueryWindow w, LocalDateTime instant) {
        return !instant.isBefore(w.from()) && instant.isBefore(w.endExclusive());
    }

    @Test
    void instantAtStartOfFromDayIsInside() {
        PublicQueryWindow w = PublicQueryWindow.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
        assertThat(contains(w, LocalDate.of(2026, 3, 1).atStartOfDay())).isTrue();
    }

    @Test
    void lastNanosecondOfToDayIsInside() {
        PublicQueryWindow w = PublicQueryWindow.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
        assertThat(contains(w, LocalDate.of(2026, 3, 5).atTime(LocalTime.MAX))).isTrue();
    }

    @Test
    void midnightAfterToDayIsOutside() {
        PublicQueryWindow w = PublicQueryWindow.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
        assertThat(w.endExclusive()).isEqualTo(LocalDateTime.of(2026, 3, 6, 0, 0));
        assertThat(contains(w, LocalDate.of(2026, 3, 6).atStartOfDay())).isFalse();
    }

    @Test
    void instantBeforeFromDayIsOutside() {
        PublicQueryWindow w = PublicQueryWindow.resolve(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
        assertThat(contains(w, LocalDate.of(2026, 3, 1).atStartOfDay().minusNanos(1))).isFalse();
    }

    @Test
    void customDefaultDaysApplyWhenBoundsAreAbsent() {
        PublicQueryWindow w = PublicQueryWindow.resolve(null, null, 7);
        assertThat(w.from()).isEqualTo(LocalDate.now().minusDays(7).atStartOfDay());
        assertThat(w.endExclusive()).isEqualTo(LocalDate.now().plusDays(1).atStartOfDay());
    }

    @Test
    void invertedRangeAndExcessiveSpanAreRejected() {
        assertThatThrownBy(() -> PublicQueryWindow.resolve(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1)))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicQueryWindow.resolve(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 2)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void datesOutsideTheSaneRangeAreRejectedWithoutOtherExceptions() {
        assertThatThrownBy(() -> PublicQueryWindow.resolve(LocalDate.of(1999, 12, 31), null))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicQueryWindow.resolve(null, LocalDate.of(-999999999, 1, 1)))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicQueryWindow.resolve(
                LocalDate.of(999999999, 12, 1), LocalDate.of(999999999, 12, 31)))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicQueryWindow.resolve(null, LocalDate.MAX))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PublicQueryWindow.resolve(null, LocalDate.now().plusDays(2)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tomorrowIsAcceptedAsUpperBound() {
        PublicQueryWindow w = PublicQueryWindow.resolve(null, LocalDate.now().plusDays(1));
        assertThat(w.endExclusive()).isEqualTo(LocalDate.now().plusDays(2).atStartOfDay());
    }
}
