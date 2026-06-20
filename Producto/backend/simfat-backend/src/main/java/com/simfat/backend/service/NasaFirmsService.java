package com.simfat.backend.service;

public interface NasaFirmsService {

    void syncActiveFiresForAllRegions();

    int syncActiveFiresByRegion(String regionId, double west, double south, double east, double north);
}
