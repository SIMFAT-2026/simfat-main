package com.simfat.backend.web;

import com.simfat.backend.exception.BadRequestException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Date window and result caps shared by the anonymous list endpoints, so an unauthenticated caller
 * can never trigger an unbounded read.
 *
 * <p>The window is half-open: {@code [from 00:00, (to + 1 day) 00:00)}. Repository queries must use an
 * inclusive lower bound and an exclusive upper bound (Spring Data {@code Between} is exclusive on both
 * ends and would drop events at exactly 00:00:00).
 *
 * <p>Days are evaluated in server-local time, while FIRMS timestamps are stored in UTC; no zone
 * conversion is applied (known limitation).
 */
public final class PublicQueryWindow {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_SPAN_DAYS = 90;
    public static final int MAX_PUBLIC_ALERTS = 1000;
    public static final int MAX_PUBLIC_REPORTS = 500;
    public static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

    private final LocalDateTime from;
    private final LocalDateTime endExclusive;

    private PublicQueryWindow(LocalDateTime from, LocalDateTime endExclusive) {
        this.from = from;
        this.endExclusive = endExclusive;
    }

    public static PublicQueryWindow resolve(LocalDate from, LocalDate to) {
        return resolve(from, to, DEFAULT_DAYS);
    }

    /**
     * Absent bounds default to the last {@code defaultDays} days ending today; a lone {@code from}
     * runs up to today. Inverted ranges, spans above {@value #MAX_SPAN_DAYS} days and explicit dates
     * before {@link #MIN_DATE} or after tomorrow are rejected with a 400, never an unhandled error.
     */
    public static PublicQueryWindow resolve(LocalDate from, LocalDate to, int defaultDays) {
        try {
            LocalDate today = LocalDate.now();
            if (from != null && from.isBefore(MIN_DATE)) {
                throw new BadRequestException("El parametro 'from' esta fuera del rango permitido");
            }
            if (to != null && (to.isBefore(MIN_DATE) || to.isAfter(today.plusDays(1)))) {
                throw new BadRequestException("El parametro 'to' esta fuera del rango permitido");
            }
            if (from != null && from.isAfter(today.plusDays(1))) {
                throw new BadRequestException("El parametro 'from' esta fuera del rango permitido");
            }
            LocalDate end = to != null ? to : today;
            LocalDate start = from != null ? from : end.minusDays(defaultDays);
            if (start.isAfter(end)) {
                throw new BadRequestException("El parametro 'from' no puede ser posterior a 'to'");
            }
            if (ChronoUnit.DAYS.between(start, end) > MAX_SPAN_DAYS) {
                throw new BadRequestException("El rango de fechas no puede superar " + MAX_SPAN_DAYS + " dias");
            }
            return new PublicQueryWindow(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        } catch (DateTimeException | ArithmeticException ex) {
            throw new BadRequestException("El rango de fechas es invalido");
        }
    }

    /** Inclusive lower bound (start of the {@code from} day). */
    public LocalDateTime from() {
        return from;
    }

    /** Exclusive upper bound (start of the day after {@code to}). */
    public LocalDateTime endExclusive() {
        return endExclusive;
    }
}
