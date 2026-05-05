package com.simfat.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimfatBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimfatBackendApplication.class, args);
    }
}
