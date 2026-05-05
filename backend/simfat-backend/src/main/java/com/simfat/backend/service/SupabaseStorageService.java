package com.simfat.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface SupabaseStorageService {

    String uploadCitizenReportFile(MultipartFile file);
}
