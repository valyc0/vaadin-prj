package com.example.vaadin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
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

    private final RestClient restClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public FileStreamingService(RestClient.Builder restClientBuilder, 
                               OAuth2AuthorizedClientService authorizedClientService) {
        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ INITIALIZING FileStreamingService");
        logger.info("║ Building RestClient instance");
        logger.info("╚════════════════════════════════════════════════════════════════════════════");
        this.restClient = restClientBuilder.build();
        this.authorizedClientService = authorizedClientService;
        logger.info("FileStreamingService initialized successfully");
    }

    /**
     * Get JWT token from current authenticated user
     */
    private String getJwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.warn("No authenticated user found");
            return null;
        }

        try {
            OAuth2AuthorizedClient client = authorizedClientService
                    .loadAuthorizedClient("keycloak", authentication.getName());
            
            if (client == null) {
                logger.warn("No OAuth2 client found for user: {}", authentication.getName());
                return null;
            }

            OAuth2AccessToken accessToken = client.getAccessToken();
            String token = accessToken.getTokenValue();
            
            logger.debug("JWT token retrieved for user: {}", authentication.getName());
            return token;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve access token", e);
            return null;
        }
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

        logger.info("→ Preparing InputStream for direct streaming...");

        logger.info("⚡ STREAMING STARTED - Data flowing directly:");
        logger.info("   ┌──────────────┐        ┌──────────────┐        ┌──────────────┐        ┌───────────┐");
        logger.info("   │ HTTP Request │  ════► │ RestTemplate │  ════► │ Roles Service│  ════► │   MinIO   │");
        logger.info("   │ (InputStream)│        │(InputStream) │        │(Octet-Stream)│        │  Storage  │");
        logger.info("   └──────────────┘        └──────────────┘        └──────────────┘        └───────────┘");
        logger.info("   Direct streaming - NO Flux conversion, NO memory buffering!");

        // Get JWT token
        String jwtToken = getJwtToken();
        if (jwtToken != null) {
            logger.info("🔑 JWT token added to request");
        }

        // Use RestClient for synchronous streaming - modern Spring 6.1+ API
        return Mono.fromCallable(() -> {
            try {
                logger.info("→ Sending streaming request to: {}/api/files/upload-stream", rolesServiceUrl);
                
                // Wrap InputStream with progress tracking (logs every 5MB)
                ProgressTrackingInputStream progressStream = new ProgressTrackingInputStream(
                    inputStream, 
                    fileName,
                    5 * 1024 * 1024  // Log every 5MB
                );
                
                // Wrap in InputStreamResource for streaming
                InputStreamResource resource = new InputStreamResource(progressStream);
                
                logger.info("⏳ Starting upload - data is now streaming...");
                
                // Send request using RestClient with fluent API
                Map<String, Object> result = restClient.post()
                    .uri(rolesServiceUrl + "/api/files/upload-stream")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Filename", fileName)
                    .header("X-Content-Type", contentType != null ? contentType : "application/octet-stream")
                    .headers(headers -> {
                        if (jwtToken != null) {
                            headers.setBearerAuth(jwtToken);
                        }
                    })
                    .body(resource)
                    .retrieve()
                    .body(Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> typedResult = (Map<String, Object>) result;
                
                return typedResult;
                
            } catch (Exception e) {
                logger.error("Error during upload", e);
                throw e;
            } finally {
                // Ensure InputStream is closed
                try {
                    inputStream.close();
                    logger.debug("InputStream closed for file: {}", fileName);
                } catch (Exception e) {
                    logger.warn("Error closing InputStream for file: {}", fileName, e);
                }
            }
        })
        .doOnSuccess(response -> {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ STREAMING UPLOAD COMPLETED");
            logger.info("║ File: {}", fileName);
            logger.info("║ Duration: {} ms ({} seconds)", duration, String.format("%.2f", duration / 1000.0));
            logger.info("║ Response: {}", response);
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("╔════════════════════════════════════════════════════════════════════════════");
            logger.error("║ STREAMING UPLOAD FAILED");
            logger.error("║ File: {}", fileName);
            logger.error("║ Duration before error: {} ms", duration);
            logger.error("║ Error: {}", error.getMessage());
            logger.error("╚════════════════════════════════════════════════════════════════════════════", error);
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
    public Mono<String[]> listFiles() {
        return Mono.fromCallable(() -> restClient.get()
                .uri(rolesServiceUrl + "/api/files/list")
                .retrieve()
                .body(String[].class));
    }

    /**
     * Get file metadata from roles-service
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getFileMetadata(String filename) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = restClient.get()
                    .uri(rolesServiceUrl + "/api/files/metadata/" + filename)
                    .retrieve()
                    .body(Map.class);
            return (Map<String, Object>) result;
        });
    }

    /**
     * Delete file from roles-service
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> deleteFile(String filename) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = restClient.delete()
                    .uri(rolesServiceUrl + "/api/files/" + filename)
                    .retrieve()
                    .body(Map.class);
            return (Map<String, Object>) result;
        });
    }

    /**
     * Upload chunk to roles-service
     * 
     * @param uploadId Unique upload session ID
     * @param fileName Original filename
     * @param chunkIndex Index of this chunk
     * @param totalChunks Total number of chunks
     * @param chunk Multipart chunk file
     * @return Response from roles-service
     */
    public Mono<Map<String, Object>> uploadChunk(String uploadId, String fileName, int chunkIndex, 
                                                   int totalChunks, org.springframework.web.multipart.MultipartFile chunk) {
        return Mono.fromCallable(() -> {
            try {
                logger.info("→ Forwarding chunk {}/{} to roles-service", chunkIndex + 1, totalChunks);
                
                // Get JWT token
                String jwtToken = getJwtToken();
                
                // For RestClient with multipart, we need to use MultiValueMap
                org.springframework.util.LinkedMultiValueMap<String, Object> parts = 
                    new org.springframework.util.LinkedMultiValueMap<>();
                parts.add("chunk", chunk.getResource());
                parts.add("chunkIndex", chunkIndex);
                parts.add("totalChunks", totalChunks);
                parts.add("uploadId", uploadId);
                parts.add("fileName", fileName);

                Map<String, Object> result = restClient.post()
                        .uri(rolesServiceUrl + "/api/files/upload-chunk")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .headers(headers -> {
                            if (jwtToken != null) {
                                headers.setBearerAuth(jwtToken);
                            }
                        })
                        .body(parts)
                        .retrieve()
                        .body(Map.class);

                logger.info("✓ Chunk forwarded successfully");
                @SuppressWarnings("unchecked")
                Map<String, Object> typedResult = (Map<String, Object>) result;
                return typedResult;

            } catch (Exception e) {
                logger.error("✗ Failed to forward chunk: {}", e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Finalize chunked upload on roles-service
     * 
     * @param uploadId Upload session ID
     * @param fileName Original filename
     * @param totalChunks Total number of chunks
     * @return Final upload response
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> finalizeUpload(String uploadId, String fileName, int totalChunks) {
        return Mono.fromCallable(() -> {
            try {
                logger.info("→ Finalizing upload on roles-service: {}", uploadId);

                Map<String, Object> requestBody = Map.of(
                        "uploadId", uploadId,
                        "fileName", fileName,
                        "totalChunks", totalChunks
                );

                // Get JWT token
                String jwtToken = getJwtToken();
                
                Map<String, Object> result = restClient.post()
                        .uri(rolesServiceUrl + "/api/files/finalize-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            if (jwtToken != null) {
                                headers.setBearerAuth(jwtToken);
                            }
                        })
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);
                
                logger.info("✓ Upload finalized successfully");
                return (Map<String, Object>) result;
            } catch (Exception error) {
                logger.error("✗ Failed to finalize upload: {}", error.getMessage());
                throw error;
            }
        });
    }
    
    /**
     * InputStream wrapper that tracks progress and logs every N bytes
     */
    private static class ProgressTrackingInputStream extends InputStream {
        private final InputStream delegate;
        private final String fileName;
        private final long logIntervalBytes;
        private long totalBytesRead = 0;
        private long lastLoggedBytes = 0;
        private final long startTime;
        
        public ProgressTrackingInputStream(InputStream delegate, String fileName, long logIntervalBytes) {
            this.delegate = delegate;
            this.fileName = fileName;
            this.logIntervalBytes = logIntervalBytes;
            this.startTime = System.currentTimeMillis();
        }
        
        @Override
        public int read() throws java.io.IOException {
            int b = delegate.read();
            if (b != -1) {
                totalBytesRead++;
                checkAndLog();
            } else {
                logFinal();
            }
            return b;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            int bytesRead = delegate.read(b, off, len);
            if (bytesRead > 0) {
                totalBytesRead += bytesRead;
                checkAndLog();
            } else if (bytesRead == -1) {
                logFinal();
            }
            return bytesRead;
        }
        
        private void checkAndLog() {
            if (totalBytesRead - lastLoggedBytes >= logIntervalBytes) {
                long duration = System.currentTimeMillis() - startTime;
                double mbUploaded = totalBytesRead / (1024.0 * 1024.0);
                double speed = (totalBytesRead / (1024.0 * 1024.0)) / (duration / 1000.0);
                
                logger.info("📊 STREAMING PROGRESS | File: {} | Uploaded: {:.2f} MB | Speed: {:.2f} MB/s | Duration: {} ms", 
                    fileName, mbUploaded, speed, duration);
                
                lastLoggedBytes = totalBytesRead;
            }
        }
        
        private void logFinal() {
            if (totalBytesRead > 0) {
                long duration = System.currentTimeMillis() - startTime;
                double mbUploaded = totalBytesRead / (1024.0 * 1024.0);
                double avgSpeed = (totalBytesRead / (1024.0 * 1024.0)) / (duration / 1000.0);
                
                logger.info("✅ STREAMING COMPLETE | File: {} | Total: {:.2f} MB | Avg Speed: {:.2f} MB/s | Total Duration: {} ms", 
                    fileName, mbUploaded, avgSpeed, duration);
            }
        }
        
        @Override
        public void close() throws java.io.IOException {
            delegate.close();
        }
        
        @Override
        public int available() throws java.io.IOException {
            return delegate.available();
        }
        
        @Override
        public long skip(long n) throws java.io.IOException {
            return delegate.skip(n);
        }
        
        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }
        
        @Override
        public synchronized void mark(int readlimit) {
            delegate.mark(readlimit);
        }
        
        @Override
        public synchronized void reset() throws java.io.IOException {
            delegate.reset();
        }
    }
}
