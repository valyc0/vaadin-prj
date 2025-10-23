package com.example.vaadin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for streaming file uploads to roles-service using WebFlux
 * 
 * This service implements true streaming upload without loading files into memory.
 * 
 * Key features:
 * - Uses WebFlux reactive streams for non-blocking I/O
 * - Converts InputStream to Flux<DataBuffer> for streaming
 * - No memory buffering (constant ~8KB buffer)
 * - Direct streaming from Vaadin Upload to roles-service
 * - Progress tracking via callback
 */
@Service
public class FileStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(FileStreamingService.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming

    @Value("${roles.service.url:http://localhost:8091}")
    private String rolesServiceUrl;

    private final WebClient webClient;

    public FileStreamingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Upload file to roles-service using reactive streaming
     * 
     * Flow:
     * 1. InputStream from Vaadin Upload component
     * 2. Convert to Flux<DataBuffer> (reactive stream)
     * 3. Stream via WebFlux to roles-service
     * 4. roles-service streams to MinIO
     * 
     * Memory usage: Constant ~8KB regardless of file size
     * 
     * @param fileName Original filename
     * @param inputStream Stream from Vaadin Upload (will be consumed and closed)
     * @param contentType MIME type
     * @param progressCallback Optional callback for progress updates (bytes uploaded)
     * @return Mono with upload response
     */
    public Mono<Map<String, Object>> uploadFileStreaming(
            String fileName, 
            InputStream inputStream, 
            String contentType,
            Consumer<Long> progressCallback) {
        
        long startTime = System.currentTimeMillis();
        
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ WEBFLUX STREAMING UPLOAD STARTED");
        logger.info("║ File: {}", fileName);
        logger.info("║ Content-Type: {}", contentType);
        logger.info("║ Target: {}/api/files/upload", rolesServiceUrl);
        logger.info("╚════════════════════════════════════════════════════════════════════════════");

        logger.info("→ Converting InputStream to reactive Flux<DataBuffer>...");
        
        // Convert InputStream to Flux<DataBuffer> for reactive streaming
        // This creates a reactive stream that reads data in 8KB chunks
        Flux<DataBuffer> dataBufferFlux = DataBufferUtils.readInputStream(
                () -> inputStream,
                new DefaultDataBufferFactory(),
                BUFFER_SIZE
        );

        // Add progress tracking if callback provided
        if (progressCallback != null) {
            final long[] bytesUploaded = {0};
            dataBufferFlux = dataBufferFlux.doOnNext(buffer -> {
                bytesUploaded[0] += buffer.readableByteCount();
                progressCallback.accept(bytesUploaded[0]);
            });
        }

        logger.info("→ Creating multipart body with streaming data...");
        
        // Build multipart request with streaming body
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart("file", dataBufferFlux, DataBuffer.class)
                .filename(fileName)
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"));

        logger.info("⚡ STREAMING STARTED - Data flowing reactively:");
        logger.info("   ┌──────────────┐        ┌──────────────┐        ┌──────────────┐        ┌───────────┐");
        logger.info("   │ Vaadin Upload│  ════► │  WebFlux     │  ════► │ Roles Service│  ════► │   MinIO   │");
        logger.info("   │ (InputStream)│        │(Flux<Buffer>)│        │ (InputStream)│        │  Storage  │");
        logger.info("   └──────────────┘        └──────────────┘        └──────────────┘        └───────────┘");
        logger.info("   Reactive streaming - NO blocking, NO memory buffering!");

        // Send streaming request to roles-service
        @SuppressWarnings("unchecked")
        Mono<Map<String, Object>> resultMono = (Mono<Map<String, Object>>) (Mono<?>) webClient.post()
                .uri(rolesServiceUrl + "/api/files/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class);
        
        return resultMono
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.info("╔════════════════════════════════════════════════════════════════════════════");
                    logger.info("║ WEBFLUX STREAMING UPLOAD COMPLETED");
                    logger.info("║ File: {}", fileName);
                    logger.info("║ Duration: {} ms ({} seconds)", duration, String.format("%.2f", duration / 1000.0));
                    logger.info("║ Response: {}", response);
                    logger.info("╚════════════════════════════════════════════════════════════════════════════");
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.error("╔════════════════════════════════════════════════════════════════════════════");
                    logger.error("║ WEBFLUX STREAMING UPLOAD FAILED");
                    logger.error("║ File: {}", fileName);
                    logger.error("║ Duration before error: {} ms", duration);
                    logger.error("║ Error: {}", error.getMessage());
                    logger.error("╚════════════════════════════════════════════════════════════════════════════", error);
                })
                .doFinally(signal -> {
                    // Ensure InputStream is closed
                    try {
                        inputStream.close();
                        logger.debug("InputStream closed for file: {}", fileName);
                    } catch (Exception e) {
                        logger.warn("Error closing InputStream for file: {}", fileName, e);
                    }
                });
    }

    /**
     * Upload file without progress tracking
     */
    public Mono<Map<String, Object>> uploadFileStreaming(String fileName, InputStream inputStream, String contentType) {
        return uploadFileStreaming(fileName, inputStream, contentType, null);
    }

    /**
     * List files from roles-service
     */
    @SuppressWarnings("unchecked")
    public Mono<String[]> listFiles() {
        return webClient.get()
                .uri(rolesServiceUrl + "/api/files/list")
                .retrieve()
                .bodyToMono(String[].class);
    }

    /**
     * Get file metadata from roles-service
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getFileMetadata(String filename) {
        return (Mono<Map<String, Object>>) (Mono<?>) webClient.get()
                .uri(rolesServiceUrl + "/api/files/metadata/" + filename)
                .retrieve()
                .bodyToMono(Map.class);
    }

    /**
     * Delete file from roles-service
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> deleteFile(String filename) {
        return (Mono<Map<String, Object>>) (Mono<?>) webClient.delete()
                .uri(rolesServiceUrl + "/api/files/" + filename)
                .retrieve()
                .bodyToMono(Map.class);
    }
}
