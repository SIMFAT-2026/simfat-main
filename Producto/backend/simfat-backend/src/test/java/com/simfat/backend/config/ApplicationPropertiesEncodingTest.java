package com.simfat.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Spring Boot reads application.properties as ISO-8859-1, so a raw UTF-8 accent turns
 * into mojibake ("ColecciÃ³n") in public API responses. Non-ASCII values must use
 * unicode escapes.
 */
class ApplicationPropertiesEncodingTest {

    @Test
    void mapbiomasCitationKeepsItsAccentWhenLoadedAsIso88591() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }

        assertThat(properties.getProperty("mapbiomas.citation"))
                .contains("Colección 2 de Cobertura")
                .contains("Colección 1 de Fuego")
                .doesNotContain("Ã");
    }
}
