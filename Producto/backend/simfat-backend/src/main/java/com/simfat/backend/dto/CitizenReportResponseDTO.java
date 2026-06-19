package com.simfat.backend.dto;

import com.simfat.backend.model.CitizenReportStatus;
import java.time.LocalDateTime;
import java.util.List;

public class CitizenReportResponseDTO {

    private String id;
    private String regionId;
    private String comunaId;
    private String category;
    private String subCategory;
    private String description;
    private Double latitude;
    private Double longitude;
    private CitizenReportStatus status;
    private Integer photoCount;
    private List<String> photos;
    private LocalDateTime createdAt;
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

    public Integer getPhotoCount() {
        return photoCount;
    }

    public void setPhotoCount(Integer photoCount) {
        this.photoCount = photoCount;
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
