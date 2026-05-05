package com.simfat.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.simfat.backend.config.OpenEoProperties;
import com.simfat.backend.dto.RegionAoiCoverageDTO;
import com.simfat.backend.dto.RegionAoiUpdateRequestDTO;
import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.model.Region;
import com.simfat.backend.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private RegionRepository regionRepository;

    private RegionServiceImpl regionService;

    @BeforeEach
    void setUp() {
        OpenEoProperties properties = new OpenEoProperties();
        properties.getAoi().setBboxMap("CL-02:-71.0,-34.0,-70.0,-33.0");
        regionService = new RegionServiceImpl(regionRepository, properties);
    }

    @Test
    void getAoiCoverage_prioritizesDbAoiOverEnv() {
        Region withDbAoi = new Region();
        withDbAoi.setId("r1");
        withDbAoi.setNombre("Region 1");
        withDbAoi.setZona("CENTRO");
        withDbAoi.setCodigo("CL-02");
        withDbAoi.setAoiBbox(List.of(-70.8, -33.9, -70.2, -33.4));

        Region withoutDbAoi = new Region();
        withoutDbAoi.setId("r2");
        withoutDbAoi.setNombre("Region 2");
        withoutDbAoi.setZona("SUR");
        withoutDbAoi.setCodigo("CL-02");

        when(regionRepository.findAll()).thenReturn(List.of(withDbAoi, withoutDbAoi));

        List<RegionAoiCoverageDTO> coverage = regionService.getAoiCoverage();

        assertEquals("aoi_db", coverage.get(0).getSource());
        assertEquals("aoi_env", coverage.get(1).getSource());
        assertEquals(true, coverage.get(0).isHasAoi());
    }

    @Test
    void updateAoi_rejectsInvalidBbox() {
        Region region = new Region();
        region.setId("r1");
        when(regionRepository.findById("r1")).thenReturn(Optional.of(region));

        RegionAoiUpdateRequestDTO request = new RegionAoiUpdateRequestDTO();
        request.setAoiBbox(List.of(-70.0, -20.0, -71.0, -19.0));

        assertThrows(BadRequestException.class, () -> regionService.updateAoi("r1", request));
    }

    @Test
    void updateAoi_persistsValidBbox() {
        Region region = new Region();
        region.setId("r1");
        when(regionRepository.findById("r1")).thenReturn(Optional.of(region));
        when(regionRepository.save(region)).thenReturn(region);

        RegionAoiUpdateRequestDTO request = new RegionAoiUpdateRequestDTO();
        request.setAoiBbox(List.of(-71.0, -34.0, -70.0, -33.0));

        regionService.updateAoi("r1", request);

        verify(regionRepository).save(region);
        assertEquals(List.of(-71.0, -34.0, -70.0, -33.0), region.getAoiBbox());
    }
}
