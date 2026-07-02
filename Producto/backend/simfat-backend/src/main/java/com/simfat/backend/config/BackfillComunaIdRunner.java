package com.simfat.backend.config;

import com.mongodb.bulk.BulkWriteResult;
import com.simfat.backend.model.ComunaInfo;
import com.simfat.backend.model.HeatAlertEvent;
import com.simfat.backend.repository.ComunaInfoRepository;
import com.simfat.backend.repository.HeatAlertEventRepository;
import java.util.Iterator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * One-time, idempotent backfill of HeatAlertEvent.comunaId for rows persisted before
 * geometric attribution existed (or inserted while their region was still a coverage
 * gap). Revised Decision 1 (post-incident): writes in BULK via BulkOperations instead of
 * one .save() per row, and runs ASYNCHRONOUSLY off the ComunaGeometrySeededEvent thread
 * so it never blocks app readiness. Listens for {@link ComunaGeometrySeededEvent}
 * (published by {@link MonitoredComunasConfig} after its seed completes) instead of
 * ApplicationReadyEvent — the explicit event makes the geometry-seed-before-backfill
 * ordering race-free regardless of @Order sequencing (FIX 4). The backfill is a
 * precision-improvement job, not a correctness prerequisite — Decision 6's coverage-gap
 * fallback already covers unattributed reads, so a late or incomplete backfill is a
 * staleness concern, not a correctness one.
 */
@Component
public class BackfillComunaIdRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackfillComunaIdRunner.class);
    private static final String FIRMS_SOURCE = "NASA_FIRMS";
    private static final int BATCH = 500; // tuned against Atlas latency; keeps each bulkWrite well under the 16MB op limit

    private final HeatAlertEventRepository heatAlertEventRepository;
    private final ComunaInfoRepository comunaInfoRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${firms.backfill.enabled:true}")
    private boolean backfillEnabled;

    public BackfillComunaIdRunner(
        HeatAlertEventRepository heatAlertEventRepository,
        ComunaInfoRepository comunaInfoRepository,
        MongoTemplate mongoTemplate
    ) {
        this.heatAlertEventRepository = heatAlertEventRepository;
        this.comunaInfoRepository = comunaInfoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // Test seam: @Value fields are normally only set by Spring; tests construct this
    // component directly and need to toggle the flag without a full application context.
    void setBackfillEnabled(boolean backfillEnabled) {
        this.backfillEnabled = backfillEnabled;
    }

    @Async("backfillExecutor")
    @EventListener(ComunaGeometrySeededEvent.class)
    public void backfill() {
        if (!backfillEnabled) {
            LOGGER.info("firms_backfill status=skipped reason=disabled");
            return;
        }

        long attributed = 0;
        long offshore = 0;
        long batches = 0;
        long skippedNoCoords = 0;

        try (Stream<HeatAlertEvent> rows = heatAlertEventRepository.streamByFuenteAndComunaIdIsNull(FIRMS_SOURCE)) {
            BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, HeatAlertEvent.class);
            int inBatch = 0;

            Iterator<HeatAlertEvent> it = rows.iterator();
            while (it.hasNext()) {
                HeatAlertEvent event = it.next();
                if (event.getLatitud() == null || event.getLongitud() == null) {
                    // FIX 8 (finding C10): these rows never get comunaId set, so they keep
                    // matching comunaId IS NULL and get re-scanned on every future boot
                    // indefinitely. Count them so the gap is at least visible in aggregate.
                    skippedNoCoords++;
                    continue;
                }

                GeoJsonPoint point = new GeoJsonPoint(event.getLongitud(), event.getLatitud());
                String comunaId = comunaInfoRepository.findOneByGeometryIntersects(point)
                    .map(ComunaInfo::getId)
                    .orElse(null);

                if (comunaId != null) {
                    attributed++;
                } else {
                    offshore++;
                }

                bulk.updateOne(
                    Query.query(where("_id").is(event.getId())),
                    Update.update("comunaId", comunaId)
                );
                inBatch++;

                if (inBatch == BATCH) {
                    BulkWriteResult result = bulk.execute();
                    batches++;
                    LOGGER.info(
                        "firms_backfill status=progress batches={} attributed={} offshore={} matched={} modified={}",
                        batches, attributed, offshore, result.getMatchedCount(), result.getModifiedCount()
                    );
                    bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, HeatAlertEvent.class);
                    inBatch = 0;
                }
            }

            if (inBatch > 0) {
                // FIX 9 (finding C11): capture and log the trailing partial batch's result
                // the same way every in-loop batch does, instead of discarding it.
                BulkWriteResult result = bulk.execute();
                batches++;
                LOGGER.info(
                    "firms_backfill status=progress batches={} attributed={} offshore={} matched={} modified={}",
                    batches, attributed, offshore, result.getMatchedCount(), result.getModifiedCount()
                );
            }
        } catch (Exception ex) {
            // FIX 3 (finding C3): @Async has no try/catch anywhere and no
            // AsyncUncaughtExceptionHandler registered, so a mid-run exception (e.g.
            // MongoBulkWriteException) previously aborted the job with only a generic
            // Spring-default log line — no status=failed, no operator signal, no visible
            // partial progress. Make the failure observable, matching the existing
            // status=progress/status=done convention. Out of scope: retry logic.
            LOGGER.error(
                "firms_backfill status=failed batches={} attributed={} offshore={} skippedNoCoords={} error={}",
                batches, attributed, offshore, skippedNoCoords, ex.getMessage(), ex
            );
            return;
        }

        LOGGER.info(
            "firms_backfill status=done batches={} attributed={} offshore={} skippedNoCoords={}",
            batches, attributed, offshore, skippedNoCoords
        );
    }
}
