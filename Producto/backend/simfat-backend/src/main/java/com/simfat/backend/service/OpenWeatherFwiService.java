package com.simfat.backend.service;

public interface OpenWeatherFwiService {

    void syncFwiForAllRegions();

    boolean syncFwiByRegion(String regionId, double lat, double lon);
}
