package com.simfat.backend.service.impl;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.CitizenReportStatus;
import com.simfat.backend.repository.CitizenReportRepository;
import com.simfat.backend.service.NotificationService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CitizenReportValidationExpiryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitizenReportValidationExpiryService.class);

    private final CitizenReportRepository citizenReportRepository;
    private final NotificationService notificationService;
    private final int retentionDays;

    public CitizenReportValidationExpiryService(
        CitizenReportRepository citizenReportRepository,
        NotificationService notificationService,
        @Value("${citizen-report.validation.retention-days:3}") int retentionDays
    ) {
        this.citizenReportRepository = citizenReportRepository;
        this.notificationService = notificationService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${citizen-report.validation.expiry.cron:0 30 3 * * *}")
    public void processExpirations() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        // Etapa 1: VALIDADO sin revalidar -> vuelve a RECIBIDO marcado como vencido
        for (CitizenReport report : citizenReportRepository.findByStatusAndValidatedAtBefore(CitizenReportStatus.VALIDADO, cutoff)) {
            report.setStatus(CitizenReportStatus.RECIBIDO);
            report.setValidatedAt(null);
            report.setStaleCount(1);
            report.setStaleSince(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            citizenReportRepository.save(report);
            notificationService.notifyReportValidationExpired(report);
            LOGGER.info("citizen_report_validation_expired reportId={}", report.getId());
        }

        // Etapa 2: ya vencio una vez y no se revalido -> se descarta automaticamente
        for (CitizenReport report : citizenReportRepository.findByStatusAndStaleCountAndStaleSinceBefore(
            CitizenReportStatus.RECIBIDO, 1, cutoff)) {
            report.setStatus(CitizenReportStatus.DESCARTADO);
            report.setStaleCount(2);
            report.setDiscardReason("VENCIDO");
            report.setUpdatedAt(LocalDateTime.now());
            citizenReportRepository.save(report);
            notificationService.notifyReportAutoDiscarded(report);
            LOGGER.info("citizen_report_auto_discarded reportId={}", report.getId());
        }
    }
}
