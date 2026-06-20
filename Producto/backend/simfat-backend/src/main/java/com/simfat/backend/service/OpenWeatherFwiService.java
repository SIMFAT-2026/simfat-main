package com.simfat.backend.service;

// Nombre legacy mantenido por compatibilidad. Implementacion usa Open-Meteo (sin API key).
public interface OpenWeatherFwiService {

    void syncFwiForAllRegions();

    boolean syncFwiByRegion(String regionId, double lat, double lon);
}
