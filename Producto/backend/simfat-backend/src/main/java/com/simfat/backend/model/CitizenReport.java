package com.simfat.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "citizen_reports")
public class CitizenReport {

    @Id
    private String id;

    private String regionId;
    private String comunaId;
    private String category;
    private String subCategory;
    private String description;
    private Double latitude;
    private Double longitude;
    private CitizenReportStatus status;
    private List<String> photos = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Vencimiento de validacion: validatedAt se setea al pasar a VALIDADO.
    // staleCount: 0 = nunca vencido, 1 = ya volvio una vez a RECIBIDO por
    // vencimiento, 2 = descartado automaticamente. staleSince marca el inicio
    // de la cuenta de la segunda etapa (RECIBIDO vencido -> DESCARTADO).
    private LocalDateTime validatedAt;
    private int staleCount;
    private LocalDateTime staleSince;
    private String discardReason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getComunaId() {
        return comunaId;
    }

    public void setComunaId(String comunaId) {
        this.comunaId = comunaId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public CitizenReportStatus getStatus() {
        return status;
    }

    public void setStatus(CitizenReportStatus status) {
        this.status = status;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public int getStaleCount() {
        return staleCount;
    }

    public void setStaleCount(int staleCount) {
        this.staleCount = staleCount;
    }

    public LocalDateTime getStaleSince() {
        return staleSince;
    }

    public void setStaleSince(LocalDateTime staleSince) {
        this.staleSince = staleSince;
    }

    public String getDiscardReason() {
        return discardReason;
    }

    public void setDiscardReason(String discardReason) {
        this.discardReason = discardReason;
    }
}
