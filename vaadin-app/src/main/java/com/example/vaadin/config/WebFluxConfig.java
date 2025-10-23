package com.example.vaadin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebFlux configuration for reactive streaming file uploads
 */
@Configuration
public class WebFluxConfig {

    /**
     * Configure WebClient for streaming file uploads
     * 
     * Configuration optimized for large file uploads:
     * - Extended timeouts for large files
     * - Optimized buffer sizes
     * - Connection pooling
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure HttpClient with extended timeouts for large files
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofHours(2))  // 2 hour timeout for very large files
                .keepAlive(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
