package com.example.vaadin.controller;

import com.example.vaadin.service.FileStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

/**
 * REST Controller in Vaadin app for file uploads
 * 
 * This controller acts as a proxy between browser JavaScript and roles-service.
 * 
 * Flow:
 * Browser JavaScript → This Controller → FileStreamingService → Roles-Service → MinIO
 * 
 * Benefits:
 * - JavaScript calls local Vaadin endpoint (no CORS issues)
 * - All traffic goes through Vaadin server
 * - Can add business logic, authentication, validation
 * - Logs everything in Vaadin
 */
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*") // Allow calls from same-origin JavaScript
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private FileStreamingService fileStreamingService;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    /**
     * Get JWT access token for the current authenticated user
     * JavaScript can use this token in Authorization header for API calls
     */
    @GetMapping("/token")
    public ResponseEntity<?> getAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("No authenticated user found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        try {
            OAuth2AuthorizedClient client = authorizedClientService
                    .loadAuthorizedClient("keycloak", authentication.getName());
            
            if (client == null) {
                logger.warn("No OAuth2 client found for user: {}", authentication.getName());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No OAuth2 token available"));
            }

            OAuth2AccessToken accessToken = client.getAccessToken();
            String tokenValue = accessToken.getTokenValue();
            
            logger.info("🔑 JWT token retrieved for user: {}", authentication.getName());
            
            return ResponseEntity.ok(Map.of(
                    "token", tokenValue,
                    "type", "Bearer",
                    "expiresAt", accessToken.getExpiresAt() != null ? accessToken.getExpiresAt().toString() : "unknown"
            ));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve access token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve token: " + e.getMessage()));
        }
    }

    /**
     * Upload file with streaming to roles-service
     * 
     * @param file Multipart file from browser
     * @return Upload response from roles-service
     */
    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> uploadFile(@RequestParam("file") MultipartFile file) {
        
        logger.info("═══════════════════════════════════════════════════════════════════════════════");
        logger.info("📥 VAADIN CONTROLLER - Upload received");
        logger.info("   Filename: {}", file.getOriginalFilename());
        logger.info("   Size: {} bytes ({} MB)", file.getSize(), String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));
        logger.info("   Content-Type: {}", file.getContentType());
        logger.info("   Flow: Browser → Vaadin Controller → Roles-Service → MinIO");
        logger.info("═══════════════════════════════════════════════════════════════════════════════");

        try {
            // Get input stream from multipart file
            logger.info("→ Opening stream from multipart file...");
            
            // Stream to roles-service using FileStreamingService
            return fileStreamingService.uploadFileStreaming(
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getContentType()
            )
            .<ResponseEntity<?>>map(response -> {
                logger.info("═══════════════════════════════════════════════════════════════════════════════");
                logger.info("✅ VAADIN CONTROLLER - Upload completed successfully");
                logger.info("   File: {}", file.getOriginalFilename());
                logger.info("   Response from roles-service: {}", response);
                logger.info("═══════════════════════════════════════════════════════════════════════════════");
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                logger.error("═══════════════════════════════════════════════════════════════════════════════");
                logger.error("❌ VAADIN CONTROLLER - Upload failed");
                logger.error("   File: {}", file.getOriginalFilename());
                logger.error("   Error: {}", error.getMessage());
                logger.error("═══════════════════════════════════════════════════════════════════════════════", error);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Upload failed: " + error.getMessage())));
            });

        } catch (IOException e) {
            logger.error("Failed to get input stream from multipart file", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read file: " + e.getMessage())));
        }
    }

    /**
     * Upload chunk for chunked upload
     * This endpoint receives chunks and forwards them to roles-service
     */
    @PostMapping("/chunk")
    public Mono<ResponseEntity<?>> uploadChunk(
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) {
        
        logger.info("═══════════════════════════════════════════════════════════════════════════════");
        logger.info("📦 VAADIN CONTROLLER - Chunk received");
        logger.info("   Upload ID: {}", uploadId);
        logger.info("   File: {}", fileName);
        logger.info("   Chunk: {}/{}", chunkIndex + 1, totalChunks);
        logger.info("   Chunk size: {} bytes", chunk.getSize());
        logger.info("═══════════════════════════════════════════════════════════════════════════════");

        // Forward chunk to roles-service using WebClient
        return fileStreamingService.uploadChunk(uploadId, fileName, chunkIndex, totalChunks, chunk)
                .<ResponseEntity<?>>map(response -> {
                    logger.info("✓ Chunk {}/{} forwarded successfully to roles-service", chunkIndex + 1, totalChunks);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("✗ Failed to forward chunk {}/{}: {}", chunkIndex + 1, totalChunks, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Chunk upload failed: " + error.getMessage())));
                });
    }

    /**
     * Finalize chunked upload
     */
    @PostMapping("/finalize")
    public Mono<ResponseEntity<?>> finalizeUpload(@RequestBody Map<String, Object> request) {
        
        String uploadId = (String) request.get("uploadId");
        String fileName = (String) request.get("fileName");
        Integer totalChunks = (Integer) request.get("totalChunks");

        logger.info("═══════════════════════════════════════════════════════════════════════════════");
        logger.info("🔄 VAADIN CONTROLLER - Finalize upload");
        logger.info("   Upload ID: {}", uploadId);
        logger.info("   File: {}", fileName);
        logger.info("   Total chunks: {}", totalChunks);
        logger.info("═══════════════════════════════════════════════════════════════════════════════");

        return fileStreamingService.finalizeUpload(uploadId, fileName, totalChunks)
                .<ResponseEntity<?>>map(response -> {
                    logger.info("═══════════════════════════════════════════════════════════════════════════════");
                    logger.info("✅ VAADIN CONTROLLER - Upload finalized successfully");
                    logger.info("   File: {}", fileName);
                    logger.info("═══════════════════════════════════════════════════════════════════════════════");
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("✗ Failed to finalize upload: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Finalize failed: " + error.getMessage())));
                });
    }
}
