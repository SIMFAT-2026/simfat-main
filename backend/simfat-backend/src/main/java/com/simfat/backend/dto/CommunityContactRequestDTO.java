package com.simfat.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommunityContactRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
    private String name;

    @NotBlank(message = "La organizacion es obligatoria")
    @Size(max = 120, message = "La organizacion no puede exceder 120 caracteres")
    private String organization;

    @NotBlank(message = "El telefono es obligatorio")
    @Size(max = 50, message = "El telefono no puede exceder 50 caracteres")
    private String phone;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es valido")
    private String email;

    @NotBlank(message = "La region es obligatoria")
    private String regionId;

    @NotBlank(message = "El protocolo es obligatorio")
    @Size(max = 600, message = "El protocolo no puede exceder 600 caracteres")
    private String protocol;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
