package com.simfat.backend.service.impl;

import com.simfat.backend.service.GlobalForestWatchService;
import org.springframework.stereotype.Service;

@Service
public class GlobalForestWatchServiceImpl implements GlobalForestWatchService {

    @Override
    public String fetchLatestForestLossSnapshot() {
        return "Integracion pendiente: Global Forest Watch";
    }
}

