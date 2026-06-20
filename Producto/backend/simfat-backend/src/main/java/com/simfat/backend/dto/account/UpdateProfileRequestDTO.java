package com.simfat.backend.dto.account;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(

    @Size(min = 1, max = 120, message = "El nombre no puede estar en blanco ni exceder 120 caracteres")
    String fullName,

    @Size(max = 20, message = "El telefono no puede exceder 20 caracteres")
    String phone,

    @Size(max = 20, message = "Codigo de region invalido")
    String regionCode,

    @Size(max = 20, message = "Codigo de comuna invalido")
    String comunaCode
) {
}
