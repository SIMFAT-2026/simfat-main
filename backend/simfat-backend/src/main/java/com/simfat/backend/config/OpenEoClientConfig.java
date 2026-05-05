package com.simfat.backend.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenEoClientConfig {

    @Bean
    public RestClient openEoRestClient(OpenEoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getService().getTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getService().getTimeoutMs()));

        return RestClient.builder()
            .baseUrl(properties.getService().getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }
}
