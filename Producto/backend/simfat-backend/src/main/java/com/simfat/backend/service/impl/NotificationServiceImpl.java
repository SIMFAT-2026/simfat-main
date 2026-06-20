package com.simfat.backend.service.impl;

import com.simfat.backend.dto.NotificationDTO;
import com.simfat.backend.dto.UnreadNotificationsDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.exception.UnauthorizedException;
import com.simfat.backend.model.AlertRule;
import com.simfat.backend.model.AppUser;
import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.ComunaRiskSnapshot;
import com.simfat.backend.model.Notification;
import com.simfat.backend.repository.AlertRuleRepository;
import com.simfat.backend.repository.AppUserRepository;
import com.simfat.backend.repository.NotificationRepository;
import com.simfat.backend.security.AuthorizationResolverService;
import com.simfat.backend.service.AlertRuleEvaluationService;
import com.simfat.backend.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int UNREAD_LIMIT = 10;

    // Orden de severidad para deduplicacion
    private static final Map<String, Integer> LEVEL_ORDER = Map.of(
        "NORMAL", 0,
        "PREVENTIVO", 1,
        "ALTO", 2,
        "CRITICO", 3
    );

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AuthorizationResolverService authorizationResolverService;
    private final AlertRuleEvaluationService alertRuleEvaluationService;

    public NotificationServiceImpl(
        NotificationRepository notificationRepository,
        AppUserRepository appUserRepository,
        AlertRuleRepository alertRuleRepository,
        AuthorizationResolverService authorizationResolverService,
        AlertRuleEvaluationService alertRuleEvaluationService
    ) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.authorizationResolverService = authorizationResolverService;
        this.alertRuleEvaluationService = alertRuleEvaluationService;
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationsDTO getUnread(String userId) {
        org.springframework.data.domain.Pageable page = PageRequest.of(0, UNREAD_LIMIT);
        List<NotificationDTO> items = notificationRepository
            .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
            .stream()
            .map(this::toDTO)
            .toList();
        long total = notificationRepository.countByUserIdAndReadFalse(userId);
        return new UnreadNotificationsDTO(items, total);
    }

    @Override
    @Transactional
    public NotificationDTO markRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion no encontrada"));
        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedException("No autorizado para marcar esta notificacion");
        }
        notification.setRead(true);
        return toDTO(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void triggerComunaRiskAlert(ComunaRiskSnapshot snapshot, String previousAlertLevel) {
        String newLevel = snapshot.getAlertLevel();

        // Solo ALTO o CRITICO disparan notificacion
        if (!"ALTO".equals(newLevel) && !"CRITICO".equals(newLevel)) {
            return;
        }

        // Deduplicacion: notificar solo si el nivel escalo
        int prevOrder = LEVEL_ORDER.getOrDefault(previousAlertLevel, 0);
        int newOrder = LEVEL_ORDER.getOrDefault(newLevel, 0);
        if (newOrder <= prevOrder) {
            return;
        }

        // Verificar que existe una AlertRule activa cuyo umbral fue superado por el snapshot
        List<AlertRule> rules = alertRuleRepository.findByRegionIdAndActivaTrue(snapshot.getRegionId());
        boolean anyRuleExceeded = rules.stream().anyMatch(rule -> alertRuleEvaluationService.isThresholdExceeded(snapshot, rule));
        if (!anyRuleExceeded) {
            return;
        }

        // Usuarios cuya comuna principal es la afectada
        List<AppUser> targets = appUserRepository.findByComunaCode(snapshot.getComunaId());
        if (targets.isEmpty()) {
            return;
        }

        String nombreComuna = snapshot.getNombreComuna() != null ? snapshot.getNombreComuna() : snapshot.getComunaId();
        String title = "Alerta " + newLevel + " — " + nombreComuna;
        String message = "Score de riesgo: " + snapshot.getScoreComposite()
            + ". La comuna presenta nivel " + newLevel + ".";

        List<Notification> notifications = targets.stream().map(user -> {
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setType("RISK_ALERT");
            n.setTitle(title);
            n.setMessage(message);
            n.setRegionId(snapshot.getRegionId());
            n.setComunaId(snapshot.getComunaId());
            n.setAlertLevel(newLevel);
            n.setRead(false);
            return n;
        }).toList();

        notificationRepository.saveAll(notifications);
        LOGGER.info("notifications_created comunaId={} alertLevel={} targets={}", snapshot.getComunaId(), newLevel, targets.size());
    }

    @Override
    @Transactional
    public void notifyReportValidationExpired(CitizenReport report) {
        String title = "Reporte vuelve a sin validar — " + report.getCategory();
        String message = "El reporte \"" + report.getCategory() + "\" no fue revalidado en el plazo y volvio a estado"
            + " sin validar. Requiere revision.";
        dispatchReportNotification(report, "REPORT_VALIDATION_EXPIRED", title, message);
    }

    @Override
    @Transactional
    public void notifyReportAutoDiscarded(CitizenReport report) {
        String title = "Reporte descartado automaticamente — " + report.getCategory();
        String message = "El reporte \"" + report.getCategory() + "\" fue descartado automaticamente por vencimiento"
            + " (no se revalido a tiempo tras volver a estado sin validar).";
        dispatchReportNotification(report, "REPORT_AUTO_DISCARDED", title, message);
    }

    private void dispatchReportNotification(CitizenReport report, String type, String title, String message) {
        List<AppUser> targets = resolveReportNotificationTargets(report.getComunaId());
        if (targets.isEmpty()) {
            LOGGER.warn("report_notification_no_targets reportId={} comunaId={}", report.getId(), report.getComunaId());
            return;
        }

        List<Notification> notifications = targets.stream().map(user -> {
            Notification n = new Notification();
            n.setUserId(user.getId());
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message);
            n.setRegionId(report.getRegionId());
            n.setComunaId(report.getComunaId());
            n.setRead(false);
            return n;
        }).toList();

        notificationRepository.saveAll(notifications);
        LOGGER.info("report_notification_created reportId={} type={} targets={}", report.getId(), type, targets.size());
    }

    private List<AppUser> resolveReportNotificationTargets(String comunaId) {
        List<AppUser> comunaModerators = appUserRepository.findByComunaCode(comunaId).stream()
            .filter(authorizationResolverService::hasModerationCapability)
            .toList();

        if (!comunaModerators.isEmpty()) {
            return comunaModerators;
        }

        // Fallback: ningun moderador asignado a esa comuna, se notifica a los admins globales
        // para que el vencimiento nunca quede sin que nadie se entere.
        return appUserRepository.findAll().stream()
            .filter(authorizationResolverService::hasGlobalAdminCapability)
            .toList();
    }

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(
            n.getId(),
            n.getType(),
            n.getTitle(),
            n.getMessage(),
            n.getRegionId(),
            n.getComunaId(),
            n.getAlertLevel(),
            n.isRead(),
            n.getCreatedAt()
        );
    }
}
