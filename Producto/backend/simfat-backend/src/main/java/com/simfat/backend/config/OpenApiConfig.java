package com.simfat.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "SIMFAT Backend API",
        version = "v1",
        description = "Documentacion interactiva para pruebas de endpoints en entorno local/dev. "
            + "Los datos de la API se publican bajo CC BY-NC 4.0: atribucion a NoFires requerida, "
            + "uso comercial solo con acuerdo previo. Los campos derivados de MapBiomas Chile "
            + "(CC BY 4.0) tambien requieren la cita de MapBiomas.",
        contact = @Contact(name = "David Vásquez", email = "d.vasquez.ovalle@gmail.com"),
        license = @License(
            name = "CC BY-NC 4.0 (datos de la API) · código MIT",
            url = "https://creativecommons.org/licenses/by-nc/4.0/"
        )
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
