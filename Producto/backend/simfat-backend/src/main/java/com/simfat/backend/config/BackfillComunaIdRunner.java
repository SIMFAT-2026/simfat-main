package com.simfat.backend.config;

import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent startup backfill of {@code HeatAlertEvent.comunaId} for
 * existing NASA_FIRMS rows persisted before geo-attribution existed.
 *
 * Runs at {@link ApplicationReadyEvent} ordered AFTER {@link MonitoredComunasConfig}'s
 * default-order seed listener, so comuna geometry + the 2dsphere index already
 * exist before the first {@code $geoIntersects} lookup (Decision 1, design.md).
 *
 * Idempotent / re-runnable: the query filter is {@code comunaId IS NULL}, so an
 * already-attributed row is never re-touched. A row that legitimately resolves
 * to {@code null} (offshore) is re-evaluated on every boot — acceptable, cheap
 * no-op write, since offshore rows are a small minority.
 *
 * This is the deploy gate for Slice B: Slice B readers must not query by
 * {@code comunaId} until this backfill has completed in the target environment
 * (verification: {@code count({fuente:'NASA_FIRMS', comunaId: {$exists:false}})} == 0).
 */
@Component
public class BackfillComunaIdRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackfillComunaIdRunner.class);
    private static final String SOURCE = "NASA_FIRMS";

    private final HeatAlertEventRepository heatAlertEventRepository;
    private final ComunaInfoRepository comunaInfoRepository;

    @Value("${firms.backfill.enabled:true}")
    private boolean backfillEnabled;

    public BackfillComunaIdRunner(
        HeatAlertEventRepository heatAlertEventRepository,
        ComunaInfoRepository comunaInfoRepository
    ) {
        this.heatAlertEventRepository = heatAlertEventRepository;
        this.comunaInfoRepository = comunaInfoRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void backfill() {
        if (!backfillEnabled) {
            LOGGER.info("firms_backfill status=skipped reason=disabled");
            return;
        }

        AtomicLong attributed = new AtomicLong();
        AtomicLong offshore = new AtomicLong();
        AtomicLong skippedNoCoords = new AtomicLong();

        try (Stream<HeatAlertEvent> rows =
                 heatAlertEventRepository.streamByFuenteAndComunaIdIsNull(SOURCE)) {
            rows.forEach(ev -> {
                if (ev.getLatitud() == null || ev.getLongitud() == null) {
                    skippedNoCoords.incrementAndGet();
                    return;
                }
                GeoJsonPoint point = new GeoJsonPoint(ev.getLongitud(), ev.getLatitud());
                comunaInfoRepository.findOneByGeometryIntersects(point)
                    .map(ComunaInfo::getId)
                    .ifPresentOrElse(
                        comunaId -> {
                            ev.setComunaId(comunaId);
                            attributed.incrementAndGet();
                        },
                        () -> {
                            ev.setComunaId(null);
                            offshore.incrementAndGet();
                        });
                heatAlertEventRepository.save(ev);
            });
        }

        LOGGER.info(
            "firms_backfill status=done attributed={} offshore={} skipped_no_coords={}",
            attributed.get(), offshore.get(), skippedNoCoords.get());
    }
}
