package com.simfat.backend.controller;

import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.CommunityBoardRequestDTO;
import com.simfat.backend.dto.CommunityBoardResponseDTO;
import com.simfat.backend.dto.CommunityContactRequestDTO;
import com.simfat.backend.dto.CommunityContactResponseDTO;
import com.simfat.backend.dto.CommunityResourceRequestDTO;
import com.simfat.backend.dto.CommunityResourceResponseDTO;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.CommunityBoardPost;
import com.simfat.backend.model.CommunityContact;
import com.simfat.backend.model.CommunityResource;
import com.simfat.backend.repository.CommunityBoardPostRepository;
import com.simfat.backend.repository.CommunityContactRepository;
import com.simfat.backend.repository.CommunityResourceRepository;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityBoardPostRepository boardRepository;
    private final CommunityResourceRepository resourceRepository;
    private final CommunityContactRepository contactRepository;

    public CommunityController(
        CommunityBoardPostRepository boardRepository,
        CommunityResourceRepository resourceRepository,
        CommunityContactRepository contactRepository
    ) {
        this.boardRepository = boardRepository;
        this.resourceRepository = resourceRepository;
        this.contactRepository = contactRepository;
    }

    @GetMapping("/board")
    public ResponseEntity<ApiResponse<List<CommunityBoardResponseDTO>>> getBoard(@RequestParam(required = false) String regionId) {
        List<CommunityBoardPost> items = regionId == null || regionId.isBlank()
            ? boardRepository.findAllByOrderByPublishedAtDesc()
            : boardRepository.findByRegionIdOrderByPublishedAtDesc(regionId);

        return ResponseEntity.ok(ApiResponse.ok("Mural comunitario obtenido correctamente", items.stream().map(this::toBoardResponse).toList()));
    }

    @PostMapping("/board")
    public ResponseEntity<ApiResponse<CommunityBoardResponseDTO>> createBoard(@Valid @RequestBody CommunityBoardRequestDTO request) {
        CommunityBoardPost item = new CommunityBoardPost();
        item.setTitle(request.getTitle());
        item.setMessage(request.getMessage());
        item.setPriority(request.getPriority().toUpperCase());
        item.setRegionId(request.getRegionId());
        item.setAuthor(request.getAuthor());
        item.setPublishedAt(LocalDateTime.now());

        CommunityBoardPost created = boardRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Aviso comunitario creado correctamente", toBoardResponse(created)));
    }

    @DeleteMapping("/board/{id}")
    public ResponseEntity<ApiResponse<String>> deleteBoard(@PathVariable String id) {
        CommunityBoardPost existing = boardRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Aviso comunitario no encontrado con id: " + id));

        boardRepository.delete(existing);
        return ResponseEntity.ok(ApiResponse.ok("Aviso comunitario eliminado correctamente", id));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<List<CommunityResourceResponseDTO>>> getResources(
        @RequestParam(required = false) String regionId
    ) {
        List<CommunityResource> items = regionId == null || regionId.isBlank()
            ? resourceRepository.findAllByOrderByCreatedAtDesc()
            : resourceRepository.findByRegionIdOrderByCreatedAtDesc(regionId);

        return ResponseEntity.ok(ApiResponse.ok("Recursos comunitarios obtenidos correctamente", items.stream().map(this::toResourceResponse).toList()));
    }

    @PostMapping("/resources")
    public ResponseEntity<ApiResponse<CommunityResourceResponseDTO>> createResource(
        @Valid @RequestBody CommunityResourceRequestDTO request
    ) {
        CommunityResource item = new CommunityResource();
        item.setTitle(request.getTitle());
        item.setCategory(request.getCategory().toUpperCase());
        item.setUrl(request.getUrl());
        item.setRegionId(request.getRegionId());
        item.setDescription(request.getDescription());
        item.setCreatedAt(LocalDateTime.now());

        CommunityResource created = resourceRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Recurso comunitario creado correctamente", toResourceResponse(created)));
    }

    @DeleteMapping("/resources/{id}")
    public ResponseEntity<ApiResponse<String>> deleteResource(@PathVariable String id) {
        CommunityResource existing = resourceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Recurso comunitario no encontrado con id: " + id));

        resourceRepository.delete(existing);
        return ResponseEntity.ok(ApiResponse.ok("Recurso comunitario eliminado correctamente", id));
    }

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<CommunityContactResponseDTO>>> getContacts(
        @RequestParam(required = false) String regionId
    ) {
        List<CommunityContact> items = regionId == null || regionId.isBlank()
            ? contactRepository.findAllByOrderByCreatedAtDesc()
            : contactRepository.findByRegionIdOrderByCreatedAtDesc(regionId);

        return ResponseEntity.ok(ApiResponse.ok("Contactos comunitarios obtenidos correctamente", items.stream().map(this::toContactResponse).toList()));
    }

    @PostMapping("/contacts")
    public ResponseEntity<ApiResponse<CommunityContactResponseDTO>> createContact(
        @Valid @RequestBody CommunityContactRequestDTO request
    ) {
        CommunityContact item = new CommunityContact();
        item.setName(request.getName());
        item.setOrganization(request.getOrganization());
        item.setPhone(request.getPhone());
        item.setEmail(request.getEmail());
        item.setRegionId(request.getRegionId());
        item.setProtocol(request.getProtocol());
        item.setCreatedAt(LocalDateTime.now());

        CommunityContact created = contactRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Contacto comunitario creado correctamente", toContactResponse(created)));
    }

    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<String>> deleteContact(@PathVariable String id) {
        CommunityContact existing = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contacto comunitario no encontrado con id: " + id));

        contactRepository.delete(existing);
        return ResponseEntity.ok(ApiResponse.ok("Contacto comunitario eliminado correctamente", id));
    }

    private CommunityBoardResponseDTO toBoardResponse(CommunityBoardPost item) {
        CommunityBoardResponseDTO dto = new CommunityBoardResponseDTO();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setMessage(item.getMessage());
        dto.setPriority(item.getPriority());
        dto.setRegionId(item.getRegionId());
        dto.setAuthor(item.getAuthor());
        dto.setPublishedAt(item.getPublishedAt());
        return dto;
    }

    private CommunityResourceResponseDTO toResourceResponse(CommunityResource item) {
        CommunityResourceResponseDTO dto = new CommunityResourceResponseDTO();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setCategory(item.getCategory());
        dto.setUrl(item.getUrl());
        dto.setRegionId(item.getRegionId());
        dto.setDescription(item.getDescription());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }

    private CommunityContactResponseDTO toContactResponse(CommunityContact item) {
        CommunityContactResponseDTO dto = new CommunityContactResponseDTO();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setOrganization(item.getOrganization());
        dto.setPhone(item.getPhone());
        dto.setEmail(item.getEmail());
        dto.setRegionId(item.getRegionId());
        dto.setProtocol(item.getProtocol());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
