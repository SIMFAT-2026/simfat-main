package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.NotificationDTO;
import com.simfat.backend.dto.UnreadNotificationsDTO;
import com.simfat.backend.security.SecurityUtils;
import com.simfat.backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<UnreadNotificationsDTO>> getUnread() {
        String userId = SecurityUtils.currentUserIdOrThrow();
        return ResponseEntity.ok(ApiResponse.ok("Notificaciones no leidas", notificationService.getUnread(userId)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDTO>> markRead(@PathVariable String id) {
        String userId = SecurityUtils.currentUserIdOrThrow();
        return ResponseEntity.ok(ApiResponse.ok("Notificacion marcada como leida", notificationService.markRead(id, userId)));
    }
}
