package com.simfat.backend.service;

import com.simfat.backend.dto.NotificationDTO;
import com.simfat.backend.dto.UnreadNotificationsDTO;
import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.model.ComunaRiskSnapshot;

public interface NotificationService {

    UnreadNotificationsDTO getUnread(String userId);

    NotificationDTO markRead(String notificationId, String userId);

    void triggerComunaRiskAlert(ComunaRiskSnapshot snapshot, String previousAlertLevel);

    void notifyReportValidationExpired(CitizenReport report);

    void notifyReportAutoDiscarded(CitizenReport report);
}
