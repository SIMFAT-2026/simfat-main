package com.simfat.backend.service.impl;

import com.simfat.backend.service.NasaFirmsService;
import org.springframework.stereotype.Service;

@Service
public class NasaFirmsServiceImpl implements NasaFirmsService {

    @Override
    public String fetchLatestHeatAlertsSnapshot() {
        return "Integracion pendiente: NASA FIRMS";
    }
}

