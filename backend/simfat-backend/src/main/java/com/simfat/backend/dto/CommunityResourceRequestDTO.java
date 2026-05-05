package com.simfat.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommunityResourceRequestDTO {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 140, message = "El titulo no puede exceder 140 caracteres")
    private String title;

    @NotBlank(message = "La categoria es obligatoria")
    private String category;

    @NotBlank(message = "La URL es obligatoria")
    private String url;

    @NotBlank(message = "La region es obligatoria")
    private String regionId;

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 600, message = "La descripcion no puede exceder 600 caracteres")
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
