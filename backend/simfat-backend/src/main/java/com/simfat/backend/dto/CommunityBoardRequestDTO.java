package com.simfat.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommunityBoardRequestDTO {

    @NotBlank(message = "El titulo es obligatorio")
    @Size(max = 140, message = "El titulo no puede exceder 140 caracteres")
    private String title;

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 800, message = "El mensaje no puede exceder 800 caracteres")
    private String message;

    @NotBlank(message = "La prioridad es obligatoria")
    private String priority;

    @NotBlank(message = "La region es obligatoria")
    private String regionId;

    @NotBlank(message = "El autor es obligatorio")
    private String author;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
