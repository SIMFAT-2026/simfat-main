package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.community.chat.CommunityChatActorDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageDTO;
import com.simfat.backend.dto.community.chat.CommunityChatMessageRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatModerationRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatPresenceRequestDTO;
import com.simfat.backend.dto.community.chat.CommunityChatRoomDTO;
import com.simfat.backend.exception.UnauthorizedException;
import com.simfat.backend.security.AppUserPrincipal;
import com.simfat.backend.service.CommunityChatService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/chat")
public class CommunityChatController {

    private final CommunityChatService communityChatService;

    public CommunityChatController(CommunityChatService communityChatService) {
        this.communityChatService = communityChatService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<CommunityChatRoomDTO>>> getRooms() {
        return ResponseEntity.ok(ApiResponse.ok("Salas de chat comunitario obtenidas correctamente", communityChatService.listRooms(currentActor())));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<CommunityChatMessageDTO>>> getMessages(
        @PathVariable String roomId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Mensajes de chat obtenidos correctamente", communityChatService.listMessages(roomId, after, limit, currentActor())));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<CommunityChatMessageDTO>> sendMessage(
        @PathVariable String roomId,
        @Valid @RequestBody CommunityChatMessageRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Mensaje enviado correctamente", communityChatService.sendMessage(roomId, request, currentActor())));
    }

    @PutMapping("/presence")
    public ResponseEntity<ApiResponse<Boolean>> updatePresence(@Valid @RequestBody CommunityChatPresenceRequestDTO request) {
        communityChatService.updatePresence(request, currentActor());
        return ResponseEntity.ok(ApiResponse.ok("Presencia actualizada correctamente", true));
    }

    @PostMapping("/messages/{messageId}/moderate")
    public ResponseEntity<ApiResponse<CommunityChatMessageDTO>> moderateMessage(
        @PathVariable String messageId,
        @Valid @RequestBody CommunityChatModerationRequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Mensaje moderado correctamente", communityChatService.moderateMessage(messageId, request, currentActor())));
    }

    private CommunityChatActorDTO currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new UnauthorizedException("No autorizado");
        }
        return new CommunityChatActorDTO(principal.getUserId(), principal.getFullName(), principal.getRoleCodes());
    }
}
