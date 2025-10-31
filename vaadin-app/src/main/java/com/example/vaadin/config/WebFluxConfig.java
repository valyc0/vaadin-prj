package com.example.vaadin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.context.Context;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * WebFlux configuration for reactive streaming file uploads
 */
@Configuration
public class WebFluxConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxConfig.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Initialize Reactor hooks to propagate MDC context automatically
     */
    @PostConstruct
    public void contextOperatorHook() {
        Hooks.onEachOperator(CORRELATION_ID_KEY,
            reactor.core.publisher.Operators.lift((scannable, subscriber) -> {
                return new reactor.core.CoreSubscriber<Object>() {
                    @Override
                    public void onSubscribe(org.reactivestreams.Subscription s) {
                        // Copy MDC from current thread to Reactor context
                        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                        subscriber.onSubscribe(s);
                        if (mdcContext != null) {
                            MDC.setContextMap(mdcContext);
                        }
                    }

                    @Override
                    public void onNext(Object o) {
                        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
                        subscriber.onNext(o);
                        if (mdcContext != null) {
                            MDC.setContextMap(mdcContext);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }

                    @Override
                    public reactor.util.context.Context currentContext() {
                        return subscriber.currentContext();
                    }
                };
            })
        );
        logger.info("Reactor MDC propagation hook initialized");
    }

    /**
     * Configure WebClient for streaming file uploads
     * 
     * Configuration optimized for large file uploads:
     * - Extended timeouts for large files
     * - Optimized buffer sizes
     * - Connection pooling
     * - Request/Response logging with correlation ID
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure HttpClient with extended timeouts for large files
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofHours(2))  // 2 hour timeout for very large files
                .keepAlive(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(addCorrelationId())
                .filter(logRequest())
                .filter(logResponse());
    }

    /**
     * Filter to add or propagate correlation ID from MDC to WebClient requests
     * Reads the correlation ID set by CorrelationIdFilter and propagates it through the reactive chain
     */
    private ExchangeFilterFunction addCorrelationId() {
        return (request, next) -> {
            // Get correlation ID from MDC (set by CorrelationIdFilter)
            String correlationId = MDC.get(CORRELATION_ID_KEY);
            if (correlationId == null) {
                // Fallback: generate new UUID if MDC is not set (shouldn't happen normally)
                correlationId = UUID.randomUUID().toString();
                logger.warn("Correlation ID not found in MDC, generating new one: {}", correlationId);
            }
            
            final String finalCorrelationId = correlationId;
            
            // Add correlation ID to outgoing request header
            ClientRequest modifiedRequest = ClientRequest.from(request)
                    .header(CORRELATION_ID_HEADER, finalCorrelationId)
                    .build();
            
            // Continue with the modified request and propagate correlation ID in Reactor context
            return next.exchange(modifiedRequest)
                    .contextWrite(Context.of(CORRELATION_ID_KEY, finalCorrelationId));
        };
    }

    /**
     * Filter to log outgoing requests with correlation ID
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            return Mono.deferContextual(ctx -> {
                String correlationId = ctx.getOrDefault(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));
                if (correlationId == null) {
                    correlationId = "UNKNOWN";
                }
                
                // Ensure MDC has the correlation ID
                MDC.put(CORRELATION_ID_KEY, correlationId);
                
                logger.info("╔════════════════════════════════════════════════════════════════");
                logger.info("║ OUTGOING REQUEST");
                logger.info("╠════════════════════════════════════════════════════════════════");
                logger.info("║ Method: {}", clientRequest.method());
                logger.info("║ URL: {}", clientRequest.url());
                logger.info("║ Headers:");
                clientRequest.headers().forEach((name, values) -> {
                    values.forEach(value -> {
                        // Mask sensitive headers like Authorization
                        if (name.equalsIgnoreCase("Authorization") && value.startsWith("Bearer ")) {
                            logger.info("║   {}: Bearer {}...", name, value.substring(7, Math.min(20, value.length())));
                        } else {
                            logger.info("║   {}: {}", name, value);
                        }
                    });
                });
                logger.info("╚════════════════════════════════════════════════════════════════");
                
                return Mono.just(clientRequest);
            });
        });
    }

    /**
     * Filter to log incoming responses with correlation ID
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            return Mono.deferContextual(ctx -> {
                String correlationId = ctx.getOrDefault(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));
                if (correlationId == null) {
                    correlationId = "UNKNOWN";
                }
                
                // Ensure MDC has the correlation ID
                MDC.put(CORRELATION_ID_KEY, correlationId);
                
                logger.info("╔════════════════════════════════════════════════════════════════");
                logger.info("║ INCOMING RESPONSE");
                logger.info("╠════════════════════════════════════════════════════════════════");
                logger.info("║ Status Code: {}", clientResponse.statusCode());
                logger.info("║ Headers:");
                clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                    values.forEach(value -> logger.info("║   {}: {}", name, value));
                });
                logger.info("╚════════════════════════════════════════════════════════════════");
                
                return Mono.just(clientResponse);
            });
        });
    }
}
