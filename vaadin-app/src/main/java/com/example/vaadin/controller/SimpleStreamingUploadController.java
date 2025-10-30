package com.example.vaadin.controller;

import com.example.vaadin.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.Map;

/**
 * Controller for simple streaming file upload
 * 
 * Flow: Browser JS → This Controller → FileStreamingService → Roles-Service → MinIO
 * 
 * Key points:
 * - Uses reactive FilePart for streaming
 * - No memory buffering
 * - Direct streaming to roles-service
 */
@RestController
@RequestMapping("/api/simple-upload")
public class SimpleStreamingUploadController {

    private static final Logger logger = LoggerFactory.getLogger(SimpleStreamingUploadController.class);
    
    private final FileStreamingService fileStreamingService;

    public SimpleStreamingUploadController(FileStreamingService fileStreamingService) {
        this.fileStreamingService = fileStreamingService;
    }

    /**
     * Handle streaming file upload with octet-stream using true streaming
     * 
     * This implementation uses HttpServletRequest.getInputStream() for true streaming.
     * The file is NOT loaded into memory - it streams directly from HTTP to roles-service.
     * 
     * @param request HttpServletRequest to get input stream
     * @param filename Filename from header
     * @param contentType Content type from header
     * @return Upload result
     */
    @PostMapping(value = "/stream", consumes = "application/octet-stream", produces = "application/json")
    public Mono<ResponseEntity<Map<String, Object>>> uploadFileStream(
            HttpServletRequest request,
            @RequestHeader("X-Filename") String filename,
            @RequestHeader(value = "X-Content-Type", defaultValue = "application/octet-stream") String contentType) {
        
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ SIMPLE STREAMING UPLOAD STARTED");
        logger.info("║ File: {}", filename);
        logger.info("║ Content-Type: {}", contentType);
        logger.info("║ Content-Length: {}", request.getContentLengthLong());
        logger.info("║ Remote Address: {}", request.getRemoteAddr());
        logger.info("╚════════════════════════════════════════════════════════════════════════════");

        try {
            // Get InputStream directly from HTTP request - TRUE STREAMING!
            // The data flows: Browser → Tomcat → This controller → FileStreamingService → roles-service → MinIO
            // NO buffering in memory at any point
            InputStream inputStream = request.getInputStream();
            
            logger.info("→ InputStream obtained from HTTP request");
            logger.info("⚡ Streaming directly to roles-service (NO memory buffering)...");
            
            // Stream to roles-service using FileStreamingService
            return fileStreamingService.uploadFileStreaming(
                filename,
                inputStream,
                contentType
            ).map(response -> {
                logger.info("✓ Upload completed successfully");
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                logger.error("✗ Upload failed", error);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", error.getMessage())));
            });
            
        } catch (Exception e) {
            logger.error("✗ Failed to get input stream from request", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to read request: " + e.getMessage())));
        }
    }
}
