package com.simfat.backend.controller;

import static com.simfat.backend.service.ImageSanitizerTest.containsAscii;
import static com.simfat.backend.service.ImageSanitizerTest.jpegWithExif;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simfat.backend.model.CitizenReport;
import com.simfat.backend.service.ObjectStorageService;
import com.simfat.backend.service.WebpTestSupport;
import com.simfat.backend.service.impl.LocalObjectFallbackStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MultipartFile;

/** Storage services must only ever receive sanitized image bytes (single call site in the controller). */
@WithMockUser(authorities = "PERM_REPORT_CREATE")
class CitizenReportSanitizedUploadIntegrationTest extends MongoFreeWebTestSupport {

    private static final String PAYLOAD = """
        {"regionId":"biobio","comunaId":"c1","category":"COMBUSTIBLE_VEGETAL",
         "subCategory":"Pastizales secos sin manejo","description":"dry grass",
         "latitude":-36.82,"longitude":-73.04}
        """;

    @MockBean
    private ObjectStorageService storageService;
    @MockBean
    private LocalObjectFallbackStorageService localFallback;

    private MockMultipartFile payload() {
        return new MockMultipartFile("payload", "", "application/json", PAYLOAD.getBytes());
    }

    @Test
    void remoteStorageReceivesSanitizedBytes() throws Exception {
        when(storageService.uploadFile(any(), eq("citizen-reports"))).thenReturn("https://cdn.example/clean.jpg");
        when(citizenReportRepository.save(any(CitizenReport.class))).thenAnswer(inv -> inv.getArgument(0));
        byte[] dirty = jpegWithExif(1, true);

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", dirty)))
            .andExpect(status().isOk());

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(storageService).uploadFile(captor.capture(), eq("citizen-reports"));
        assertThat(containsAscii(dirty, "Exif")).as("precondition").isTrue();
        assertThat(containsAscii(captor.getValue().getBytes(), "Exif")).isFalse();
        assertThat(containsAscii(captor.getValue().getBytes(), "GPS")).isFalse();
    }

    @Test
    void localFallbackAlsoReceivesSanitizedBytesWhenRemoteStorageFails() throws Exception {
        when(storageService.uploadFile(any(), any())).thenThrow(new IllegalStateException("storage down"));
        when(localFallback.storeFile(any(), eq("citizen-reports"))).thenReturn("/uploads/citizen-reports/x/clean.jpg");
        when(citizenReportRepository.save(any(CitizenReport.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", jpegWithExif(1, true))))
            .andExpect(status().isOk());

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(localFallback).storeFile(captor.capture(), eq("citizen-reports"));
        assertThat(containsAscii(captor.getValue().getBytes(), "Exif")).isFalse();
    }

    @Test
    void undecodableUploadIsRejectedWith400AndNothingIsStored() throws Exception {
        byte[] notAnImage = "definitely not an image".getBytes();

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", notAnImage)))
            .andExpect(status().isBadRequest());

        verify(storageService, never()).uploadFile(any(), any());
        verify(localFallback, never()).storeFile(any(), any());
        verify(citizenReportRepository, never()).save(any());
    }

    @Test
    void webpUploadFromTheBrowserIsAcceptedAndStorageReceivesCleanBytes() throws Exception {
        when(storageService.uploadFile(any(), eq("citizen-reports"))).thenReturn("https://cdn.example/clean.webp");
        when(citizenReportRepository.save(any(CitizenReport.class))).thenAnswer(inv -> inv.getArgument(0));
        byte[] dirty = WebpTestSupport.riff(
            WebpTestSupport.chunk("VP8X", WebpTestSupport.vp8xPayload(WebpTestSupport.FLAG_EXIF | WebpTestSupport.FLAG_XMP, 48, 32)),
            WebpTestSupport.chunk("VP8L", WebpTestSupport.vp8lPayload(48, 32)),
            WebpTestSupport.chunk("EXIF", WebpTestSupport.exifPayload()),
            WebpTestSupport.chunk("XMP ", WebpTestSupport.xmpPayload()));
        assertThat(containsAscii(dirty, "Exif")).as("precondition").isTrue();

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.webp", "image/webp", dirty)))
            .andExpect(status().isOk());

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(storageService).uploadFile(captor.capture(), eq("citizen-reports"));
        MultipartFile stored = captor.getValue();
        assertThat(stored.getContentType()).isEqualTo("image/webp");
        assertThat(stored.getOriginalFilename()).isEqualTo("photo.webp");
        assertThat(WebpTestSupport.chunkTypes(stored.getBytes())).containsExactly("VP8X", "VP8L");
        assertThat(containsAscii(stored.getBytes(), "Exif")).isFalse();
        assertThat(containsAscii(stored.getBytes(), "XMP ")).isFalse();
        assertThat(containsAscii(stored.getBytes(), "GPS")).isFalse();
    }

    @Test
    void spoofedContentTypeAndExtensionAreOverriddenByTheRealMagicBytes() throws Exception {
        when(storageService.uploadFile(any(), eq("citizen-reports"))).thenReturn("https://cdn.example/clean.png");
        when(citizenReportRepository.save(any(CitizenReport.class))).thenAnswer(inv -> inv.getArgument(0));
        java.io.ByteArrayOutputStream pngOut = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pngOut);
        byte[] png = pngOut.toByteArray();

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", png)))
            .andExpect(status().isOk());

        ArgumentCaptor<MultipartFile> captor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(storageService).uploadFile(captor.capture(), eq("citizen-reports"));
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
        assertThat(captor.getValue().getOriginalFilename()).isEqualTo("photo.png");
    }

    @Test
    void unsupportedFormatsBehindAnImageContentTypeAreRejected() throws Exception {
        byte[] gif = "GIF89a....".getBytes();
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();

        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.png", "image/png", gif)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "photo.webp", "image/webp", svg)))
            .andExpect(status().isBadRequest());

        verify(storageService, never()).uploadFile(any(), any());
        verify(localFallback, never()).storeFile(any(), any());
    }

    @Test
    void oneBadFileAmongSeveralStoresNothing() throws Exception {
        mockMvc.perform(multipart("/api/citizen-reports").file(payload())
                .file(new MockMultipartFile("files", "ok.jpg", "image/jpeg", jpegWithExif(1, true)))
                .file(new MockMultipartFile("files", "bad.jpg", "image/jpeg", "nope".getBytes())))
            .andExpect(status().isBadRequest());

        verify(storageService, never()).uploadFile(any(), any());
        verify(localFallback, never()).storeFile(any(), any());
    }
}
