package com.example.vaadin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * HTTP Client configuration for file uploads and API calls
 */
@Configuration
public class HttpClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientConfig.class);
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private static final String REQUEST_ID_ATTR = "requestId";

    /**
     * Configure RestClient for API calls and streaming file uploads
     * 
     * Configuration optimized for large file uploads:
     * - Extended timeouts for large files
     * - Request/Response logging
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ CONFIGURING RESTCLIENT BUILDER");
        logger.info("║ Adding request/response logging interceptor");
        logger.info("╚════════════════════════════════════════════════════════════════════════════");
        
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(loggingInterceptor());
        
        logger.info("RestClient.Builder configured successfully with logging interceptor");
        return builder;
    }
    
    /**
     * Logging interceptor for RestClient
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            // Generate unique request ID
            long requestId = requestCounter.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ RESTCLIENT REQUEST #{}", requestId);
            logger.info("║ Thread: {}", Thread.currentThread().getName());
            logger.info("║ Method: {}", request.getMethod());
            logger.info("║ URL: {}", request.getURI());
            logger.info("║ Headers:");
            request.getHeaders().forEach((name, values) -> {
                // Don't log sensitive headers content, just indicate presence
                if (name.equalsIgnoreCase("Authorization")) {
                    logger.info("║   {}: [PRESENT]", name);
                } else {
                    values.forEach(value -> logger.info("║   {}: {}", name, value));
                }
            });
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
            
            // Execute request
            var response = execution.execute(request, body);
            
            // Log response
            long duration = System.currentTimeMillis() - startTime;
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ RESTCLIENT RESPONSE #{}", requestId);
            logger.info("║ Thread: {}", Thread.currentThread().getName());
            logger.info("║ Status Code: {}", response.getStatusCode().value());
            logger.info("║ Duration: {} ms", duration);
            logger.info("║ Headers:");
            response.getHeaders().forEach((name, values) -> 
                values.forEach(value -> logger.info("║   {}: {}", name, value))
            );
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
            
            return response;
        };
    }

    /**
     * Configure WebClient for streaming file uploads (deprecated, use RestClient)
     * 
     * Configuration optimized for large file uploads:
     * - Extended timeouts for large files
     * - Optimized buffer sizes
     * - Connection pooling
     * - Request/Response logging
     * 
     * @deprecated Use RestClient.Builder instead
     */
    @Deprecated
    @Bean
    public WebClient.Builder webClientBuilder() {
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ CONFIGURING WEBCLIENT BUILDER (DEPRECATED)");
        logger.info("║ Adding request/response logging filters");
        logger.info("╚════════════════════════════════════════════════════════════════════════════");
        
        // Configure HttpClient with extended timeouts and wiretap for detailed logging
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofHours(2))  // 2 hour timeout for very large files
                .keepAlive(true)
                .wiretap(true);  // Enable detailed HTTP logging

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse());
        
        logger.info("WebClient.Builder configured successfully with logging filters");
        return builder;
    }

    /**
     * Log WebClient requests with unique ID for correlation
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // Generate unique request ID
            long requestId = requestCounter.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ WEBCLIENT REQUEST #{}", requestId);
            logger.info("║ Thread: {}", Thread.currentThread().getName());
            logger.info("║ Method: {}", clientRequest.method());
            logger.info("║ URL: {}", clientRequest.url());
            logger.info("║ Headers:");
            clientRequest.headers().forEach((name, values) -> {
                // Don't log sensitive headers content, just indicate presence
                if (name.equalsIgnoreCase("Authorization")) {
                    logger.info("║   {}: [PRESENT]", name);
                } else {
                    values.forEach(value -> logger.info("║   {}: {}", name, value));
                }
            });
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
            
            // Store request ID and start time in attributes for response correlation
            return Mono.just(clientRequest)
                    .contextWrite(ctx -> ctx.put(REQUEST_ID_ATTR, requestId)
                                            .put("startTime", startTime));
        });
    }

    /**
     * Log WebClient responses with correlation to request
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            return Mono.deferContextual(ctx -> {
                // Get request ID and start time from context
                Long requestId = ctx.getOrDefault(REQUEST_ID_ATTR, 0L);
                Long startTime = ctx.getOrDefault("startTime", System.currentTimeMillis());
                long duration = System.currentTimeMillis() - startTime;
                
                logger.info("╔════════════════════════════════════════════════════════════════════════════");
                logger.info("║ WEBCLIENT RESPONSE #{}", requestId);
                logger.info("║ Thread: {}", Thread.currentThread().getName());
                logger.info("║ Status Code: {}", clientResponse.statusCode().value());
                logger.info("║ Duration: {} ms", duration);
                logger.info("║ Headers:");
                clientResponse.headers().asHttpHeaders().forEach((name, values) -> 
                    values.forEach(value -> logger.info("║   {}: {}", name, value))
                );
                logger.info("╚════════════════════════════════════════════════════════════════════════════");
                
                return Mono.just(clientResponse);
            });
        });
    }
}
