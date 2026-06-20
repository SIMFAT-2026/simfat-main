package com.simfat.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {

    String uploadFile(MultipartFile file, String namespace);
}
