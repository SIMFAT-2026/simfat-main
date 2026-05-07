package com.simfat.backend.dto;

import java.util.List;

public class RegionAoiUpdateRequestDTO {

    private List<Double> aoiBbox;

    public List<Double> getAoiBbox() {
        return aoiBbox;
    }

    public void setAoiBbox(List<Double> aoiBbox) {
        this.aoiBbox = aoiBbox;
    }
}
