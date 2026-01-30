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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

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

    /**
     * Simple file upload to disk using classic request/response
     * 
     * This implementation writes the file directly to disk without using reactive streams.
     * 
     * @param request HttpServletRequest to get input stream
     * @param filename Filename from header
     * @return Upload result
     */
    @PostMapping(value = "/disk", consumes = "application/octet-stream", produces = "application/json")
    public ResponseEntity<Map<String, Object>> uploadFileToDisk(
            HttpServletRequest request,
            @RequestHeader("X-Filename") String filename,
            @RequestHeader(value = "X-Classification", defaultValue = "Non classificato") String classification) {
        
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ SIMPLE FILE UPLOAD TO DISK");
        logger.info("║ File: {}", filename);
        logger.info("║ Classification: {}", classification);
        logger.info("║ Content-Length: {}", request.getContentLengthLong());
        logger.info("╚════════════════════════════════════════════════════════════════════════════");

        try {
            // Create upload directory if it doesn't exist
            Path uploadDir = Paths.get("/tmp/uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                logger.info("→ Created upload directory: {}", uploadDir);
            }

            // Prepare file path
            Path filePath = uploadDir.resolve(filename);
            logger.info("→ Writing file to: {}", filePath);

            // Get input stream from request and write to file
            try (InputStream inputStream = request.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                logger.info("✓ File saved successfully");
                logger.info("  Total bytes written: {}", totalBytes);
                logger.info("  File path: {}", filePath);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("filename", filename);
                response.put("classification", classification);
                response.put("path", filePath.toString());
                response.put("size", totalBytes);
                
                return ResponseEntity.ok(response);
            }
            
        } catch (IOException e) {
            logger.error("✗ Failed to save file to disk", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
