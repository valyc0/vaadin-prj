package com.example.roles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for Roles Service
 */
@SpringBootApplication
@EntityScan(basePackages = "com.example.entity.model")
@EnableJpaRepositories(basePackages = "com.example.entity.repository")
public class RolesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RolesServiceApplication.class, args);
    }
}
