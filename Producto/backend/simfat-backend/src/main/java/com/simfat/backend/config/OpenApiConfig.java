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
        title = "NoFires API",
        version = "v1",
        description = "API de NoFires (ex SIMFAT), plataforma de monitoreo y alerta temprana de "
            + "incendios forestales. Los datos de la API se publican bajo CC BY-NC 4.0: se requiere "
            + "atribución a NoFires y el uso comercial necesita un acuerdo previo. Los campos "
            + "derivados de MapBiomas Chile (CC BY 4.0) también requieren la cita de MapBiomas.",
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
