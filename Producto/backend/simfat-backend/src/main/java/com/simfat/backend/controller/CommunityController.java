package com.simfat.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simfat.backend.dto.ApiResponse;
import com.simfat.backend.dto.CommunityBoardRequestDTO;
import com.simfat.backend.dto.CommunityBoardResponseDTO;
import com.simfat.backend.dto.CommunityContactRequestDTO;
import com.simfat.backend.dto.CommunityContactResponseDTO;
import com.simfat.backend.dto.CommunityResourceRequestDTO;
import com.simfat.backend.dto.CommunityResourceResponseDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.exception.ResourceNotFoundException;
import com.simfat.backend.model.CommunityBoardPost;
import com.simfat.backend.model.CommunityContact;
import com.simfat.backend.model.CommunityResource;
import com.simfat.backend.repository.CommunityBoardPostRepository;
import com.simfat.backend.repository.CommunityContactRepository;
import com.simfat.backend.repository.CommunityResourceRepository;
import com.simfat.backend.service.ObjectStorageService;
import com.simfat.backend.service.impl.LocalObjectFallbackStorageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private static final String BOARD_STORAGE_NAMESPACE = "community-board";
    private static final String RESOURCE_STORAGE_NAMESPACE = "community-resources";

    private final CommunityBoardPostRepository boardRepository;
    private final CommunityResourceRepository resourceRepository;
    private final CommunityContactRepository contactRepository;
    private final ObjectMapper objectMapper;
    private final ObjectStorageService storageService;
    private final LocalObjectFallbackStorageService localObjectFallbackStorageService;

    public CommunityController(
        CommunityBoardPostRepository boardRepository,
        CommunityResourceRepository resourceRepository,
        CommunityContactRepository contactRepository,
        ObjectMapper objectMapper,
        ObjectStorageService storageService,
        LocalObjectFallbackStorageService localObjectFallbackStorageService
    ) {
        this.boardRepository = boardRepository;
        this.resourceRepository = resourceRepository;
        this.contactRepository = contactRepository;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
        this.localObjectFallbackStorageService = localObjectFallbackStorageService;
    }

    @GetMapping("/board")
    public ResponseEntity<ApiResponse<List<CommunityBoardResponseDTO>>> getBoard(@RequestParam(required = false) String regionId) {
        List<CommunityBoardPost> items = regionId == null || regionId.isBlank()
            ? boardRepository.findAllByOrderByPublishedAtDesc()
            : boardRepository.findByRegionIdOrderByPublishedAtDesc(regionId);

        return ResponseEntity.ok(ApiResponse.ok("Mural comunitario obtenido correctamente", items.stream().map(this::toBoardResponse).toList()));
    }

    @PostMapping(value = "/board", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_BOARD_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
    public ResponseEntity<ApiResponse<CommunityBoardResponseDTO>> createBoard(@Valid @RequestBody CommunityBoardRequestDTO request) {
        CommunityBoardPost item = buildBoardPost(request);

        CommunityBoardPost created = boardRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Aviso comunitario creado correctamente", toBoardResponse(created)));
    }

    @PostMapping(value = "/board", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_BOARD_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
    public ResponseEntity<ApiResponse<CommunityBoardResponseDTO>> createBoardMultipart(
        @RequestPart("payload") String payload,
        @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        CommunityBoardRequestDTO request = parseBoardPayload(payload);
        CommunityBoardPost item = buildBoardPost(request);

        if (file != null && !file.isEmpty()) {
            item.setAttachmentUrl(resolveUploadReference(file, BOARD_STORAGE_NAMESPACE));
            item.setAttachmentName(file.getOriginalFilename());
            item.setAttachmentContentType(file.getContentType());
            item.setAttachmentSize(file.getSize());
            item.setAttachmentImage(file.getContentType() != null && file.getContentType().startsWith("image/"));
        }

        CommunityBoardPost created = boardRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Aviso comunitario creado correctamente", toBoardResponse(created)));
    }

    @DeleteMapping("/board/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_BOARD_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
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

    @PostMapping(value = "/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_RESOURCE_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
    public ResponseEntity<ApiResponse<CommunityResourceResponseDTO>> createResource(
        @Valid @RequestBody CommunityResourceRequestDTO request
    ) {
        CommunityResource item = buildResource(request);

        CommunityResource created = resourceRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Recurso comunitario creado correctamente", toResourceResponse(created)));
    }

    @PostMapping(value = "/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_RESOURCE_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
    public ResponseEntity<ApiResponse<CommunityResourceResponseDTO>> createResourceMultipart(
        @RequestPart("payload") String payload,
        @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo del recurso es obligatorio");
        }
        validateResourceFile(file);

        CommunityResourceRequestDTO request = parseResourcePayload(payload);
        CommunityResource item = buildResource(request);
        String fileReference = resolveUploadReference(file, RESOURCE_STORAGE_NAMESPACE);
        item.setUrl(fileReference);
        item.setFileUrl(fileReference);
        item.setFileName(file.getOriginalFilename());
        item.setFileContentType(file.getContentType());
        item.setFileSize(file.getSize());

        CommunityResource created = resourceRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Recurso comunitario creado correctamente", toResourceResponse(created)));
    }

    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_RESOURCE_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_MODERATOR')")
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
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_RESOURCE_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CommunityContactResponseDTO>> createContact(
        @Valid @RequestBody CommunityContactRequestDTO request
    ) {
        CommunityContact item = new CommunityContact();
        item.setName(request.getName());
        item.setOrganization(request.getOrganization());
        item.setPhone(request.getPhone());
        item.setEmail(request.getEmail());
        item.setRegionId(request.getRegionId());
        item.setComunaId(request.getComunaId());
        item.setProtocol(request.getProtocol());
        item.setCreatedAt(LocalDateTime.now());

        CommunityContact created = contactRepository.save(item);
        return ResponseEntity.ok(ApiResponse.ok("Contacto comunitario creado correctamente", toContactResponse(created)));
    }

    @DeleteMapping("/contacts/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_COMMUNITY_RESOURCE_MANAGE','ROLE_ADMIN','ROLE_SUPER_ADMIN')")
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
        dto.setAttachmentUrl(item.getAttachmentUrl());
        dto.setAttachmentName(item.getAttachmentName());
        dto.setAttachmentContentType(item.getAttachmentContentType());
        dto.setAttachmentSize(item.getAttachmentSize());
        dto.setAttachmentImage(item.isAttachmentImage());
        dto.setPublishedAt(item.getPublishedAt());
        return dto;
    }

    private CommunityBoardPost buildBoardPost(CommunityBoardRequestDTO request) {
        CommunityBoardPost item = new CommunityBoardPost();
        item.setTitle(request.getTitle());
        item.setMessage(request.getMessage());
        item.setPriority(request.getPriority().toUpperCase());
        item.setRegionId(request.getRegionId());
        item.setAuthor(request.getAuthor());
        item.setPublishedAt(LocalDateTime.now());
        return item;
    }

    private CommunityBoardRequestDTO parseBoardPayload(String rawPayload) {
        try {
            String normalized = rawPayload == null ? "" : rawPayload.trim();
            try {
                return objectMapper.readValue(normalized, CommunityBoardRequestDTO.class);
            } catch (IOException first) {
                String unescaped = objectMapper.readValue(normalized, String.class);
                return objectMapper.readValue(unescaped, CommunityBoardRequestDTO.class);
            }
        } catch (IOException ex) {
            throw new BadRequestException("Payload de mural comunitario invalido");
        }
    }

    private CommunityResource buildResource(CommunityResourceRequestDTO request) {
        CommunityResource item = new CommunityResource();
        item.setTitle(request.getTitle());
        item.setCategory(request.getCategory().toUpperCase());
        item.setUrl(request.getUrl());
        item.setRegionId(request.getRegionId());
        item.setComunaId(request.getComunaId());
        item.setDescription(request.getDescription());
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }

    private CommunityResourceRequestDTO parseResourcePayload(String rawPayload) {
        try {
            String normalized = rawPayload == null ? "" : rawPayload.trim();
            try {
                return objectMapper.readValue(normalized, CommunityResourceRequestDTO.class);
            } catch (IOException first) {
                String unescaped = objectMapper.readValue(normalized, String.class);
                return objectMapper.readValue(unescaped, CommunityResourceRequestDTO.class);
            }
        } catch (IOException ex) {
            throw new BadRequestException("Payload de recurso comunitario invalido");
        }
    }

    private void validateResourceFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean pdf = name.endsWith(".pdf") || contentType.equals("application/pdf");
        boolean docx = name.endsWith(".docx") || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        if (!pdf && !docx) {
            throw new BadRequestException("Solo se permiten recursos PDF o DOCX");
        }
    }

    private String resolveUploadReference(MultipartFile file, String namespace) {
        try {
            String reference = storageService.uploadFile(file, namespace);
            if (reference != null && !reference.isBlank()) {
                return reference;
            }
        } catch (RuntimeException ex) {
            // En modo demo priorizamos continuidad y visibilidad local de adjuntos.
        }
        String localReference = localObjectFallbackStorageService.storeFile(file, namespace);
        String absoluteReference = toAbsoluteReference(localReference);
        if (absoluteReference == null || absoluteReference.isBlank()) {
            throw new BadRequestException("No fue posible persistir el archivo adjunto");
        }
        return absoluteReference;
    }

    private String toAbsoluteReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return reference;
        }
        if (reference.startsWith("http://") || reference.startsWith("https://") || reference.startsWith("data:") || reference.startsWith("blob:")) {
            return reference;
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        if (reference.startsWith("/")) {
            return baseUrl + reference;
        }
        return baseUrl + "/" + reference;
    }

    private CommunityResourceResponseDTO toResourceResponse(CommunityResource item) {
        CommunityResourceResponseDTO dto = new CommunityResourceResponseDTO();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setCategory(item.getCategory());
        dto.setUrl(item.getUrl());
        dto.setRegionId(item.getRegionId());
        dto.setComunaId(item.getComunaId());
        dto.setDescription(item.getDescription());
        dto.setFileUrl(item.getFileUrl());
        dto.setFileName(item.getFileName());
        dto.setFileContentType(item.getFileContentType());
        dto.setFileSize(item.getFileSize());
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
        dto.setComunaId(item.getComunaId());
        dto.setProtocol(item.getProtocol());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }
}
