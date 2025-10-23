package com.example.roles.controller;

import com.example.roles.service.MinioStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for chunked file uploads
 * 
 * This controller handles large file uploads by accepting files in chunks.
 * Chunks are temporarily stored on disk, then assembled and uploaded to MinIO.
 * 
 * Benefits:
 * - Browser never loads entire file in memory
 * - Can upload files of any size (50GB+)
 * - Resume capability (future enhancement)
 * - Progress tracking per chunk
 * 
 * Process:
 * 1. Client splits file into chunks (10MB each)
 * 2. Client uploads chunks sequentially via POST /api/files/upload-chunk
 * 3. Server stores chunks temporarily in /tmp/uploads/{uploadId}/
 * 4. Client calls POST /api/files/finalize-upload
 * 5. Server assembles chunks, streams to MinIO, deletes temp files
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Chunked Upload", description = "API for uploading very large files in chunks")
public class ChunkedUploadController {

    private static final Logger logger = LoggerFactory.getLogger(ChunkedUploadController.class);
    private static final String TEMP_UPLOAD_DIR = "/tmp/uploads";

    @Autowired
    private MinioStreamingService minioService;

    /**
     * Upload a single chunk
     * 
     * @param chunk The chunk data (MultipartFile)
     * @param chunkIndex Index of this chunk (0-based)
     * @param totalChunks Total number of chunks
     * @param uploadId Unique ID for this upload session
     * @param fileName Original filename
     * @return ResponseEntity with chunk upload status
     */
    @PostMapping("/upload-chunk")
    @Operation(
        summary = "Upload file chunk",
        description = "Uploads a single chunk of a large file. Chunks are stored temporarily " +
                     "until all chunks are received and the upload is finalized."
    )
    public ResponseEntity<?> uploadChunk(
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) {
        
        try {
            logger.info("═══════════════════════════════════════════════════════════════════════════════");
            logger.info("📦 CHUNK RECEIVED");
            logger.info("   Upload ID: {}", uploadId);
            logger.info("   File: {}", fileName);
            logger.info("   Chunk: {}/{} ({})", chunkIndex + 1, totalChunks, formatFileSize(chunk.getSize()));
            logger.info("═══════════════════════════════════════════════════════════════════════════════");

            // Create upload directory
            Path uploadDir = Paths.get(TEMP_UPLOAD_DIR, uploadId);
            Files.createDirectories(uploadDir);

            // Save chunk to temp file
            Path chunkFile = uploadDir.resolve(String.format("chunk_%05d", chunkIndex));
            try (InputStream inputStream = chunk.getInputStream()) {
                Files.copy(inputStream, chunkFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("✓ Chunk saved: {}", chunkFile);

            Map<String, Object> response = new HashMap<>();
            response.put("uploadId", uploadId);
            response.put("chunkIndex", chunkIndex);
            response.put("chunkSize", chunk.getSize());
            response.put("message", String.format("Chunk %d/%d uploaded successfully", chunkIndex + 1, totalChunks));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error uploading chunk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload chunk: " + e.getMessage()));
        }
    }

    /**
     * Finalize chunked upload
     * 
     * This endpoint is called after all chunks are uploaded.
     * It assembles the chunks, streams to MinIO, and cleans up temp files.
     * 
     * @param request Request body with uploadId, fileName, totalChunks
     * @return ResponseEntity with final upload status
     */
    @PostMapping("/finalize-upload")
    @Operation(
        summary = "Finalize chunked upload",
        description = "Assembles all uploaded chunks into final file and streams to MinIO. " +
                     "Cleans up temporary chunk files after successful upload."
    )
    public ResponseEntity<?> finalizeUpload(@RequestBody Map<String, Object> request) {
        
        String uploadId = (String) request.get("uploadId");
        String fileName = (String) request.get("fileName");
        int totalChunks = (Integer) request.get("totalChunks");

        long startTime = System.currentTimeMillis();

        try {
            logger.info("═══════════════════════════════════════════════════════════════════════════════");
            logger.info("🔄 FINALIZING CHUNKED UPLOAD");
            logger.info("   Upload ID: {}", uploadId);
            logger.info("   File: {}", fileName);
            logger.info("   Total chunks: {}", totalChunks);
            logger.info("═══════════════════════════════════════════════════════════════════════════════");

            Path uploadDir = Paths.get(TEMP_UPLOAD_DIR, uploadId);
            
            // Verify all chunks are present
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = uploadDir.resolve(String.format("chunk_%05d", i));
                if (!Files.exists(chunkFile)) {
                    logger.error("Missing chunk: {}", i);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Missing chunk: " + i));
                }
            }

            logger.info("✓ All {} chunks verified", totalChunks);

            // Generate unique filename for MinIO
            String fileExtension = "";
            if (fileName.contains(".")) {
                fileExtension = fileName.substring(fileName.lastIndexOf("."));
            }
            String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

            // Create input stream that reads all chunks sequentially
            InputStream assembledStream = new SequentialChunkInputStream(uploadDir, totalChunks);

            // Calculate total size
            long totalSize = 0;
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = uploadDir.resolve(String.format("chunk_%05d", i));
                totalSize += Files.size(chunkFile);
            }

            logger.info("→ Total assembled size: {} ({})", totalSize, formatFileSize(totalSize));
            logger.info("→ Starting streaming upload to MinIO...");

            // Determine content type
            String contentType = determineContentType(fileName);

            // Stream assembled file to MinIO
            String storedFileName = minioService.uploadFileStreaming(
                    uniqueFileName,
                    assembledStream,
                    contentType,
                    totalSize
            );

            long duration = System.currentTimeMillis() - startTime;
            double throughputMBps = totalSize / (1024.0 * 1024.0) / (duration / 1000.0);

            // Clean up temp files
            logger.info("→ Cleaning up temporary chunks...");
            deleteDirectory(uploadDir);
            logger.info("✓ Cleanup completed");

            logger.info("═══════════════════════════════════════════════════════════════════════════════");
            logger.info("✅ CHUNKED UPLOAD COMPLETED");
            logger.info("   Stored as: {}", storedFileName);
            logger.info("   Total size: {} ({})", totalSize, formatFileSize(totalSize));
            logger.info("   Duration: {} ms ({} seconds)", duration, String.format("%.2f", duration / 1000.0));
            logger.info("   Throughput: {} MB/s", String.format("%.2f", throughputMBps));
            logger.info("═══════════════════════════════════════════════════════════════════════════════");

            Map<String, Object> response = new HashMap<>();
            response.put("storedFileName", storedFileName);
            response.put("originalFileName", fileName);
            response.put("size", totalSize);
            response.put("formattedSize", formatFileSize(totalSize));
            response.put("contentType", contentType);
            response.put("throughputMBps", String.format("%.2f", throughputMBps));
            response.put("durationMs", duration);
            response.put("chunksProcessed", totalChunks);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error finalizing chunked upload", e);
            
            // Try to clean up on error
            try {
                Path uploadDir = Paths.get(TEMP_UPLOAD_DIR, uploadId);
                if (Files.exists(uploadDir)) {
                    deleteDirectory(uploadDir);
                }
            } catch (Exception cleanupError) {
                logger.warn("Failed to cleanup after error", cleanupError);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to finalize upload: " + e.getMessage()));
        }
    }

    /**
     * InputStream that reads multiple chunk files sequentially
     */
    private static class SequentialChunkInputStream extends InputStream {
        private final Path uploadDir;
        private final int totalChunks;
        private int currentChunkIndex = 0;
        private InputStream currentChunkStream = null;

        public SequentialChunkInputStream(Path uploadDir, int totalChunks) {
            this.uploadDir = uploadDir;
            this.totalChunks = totalChunks;
        }

        @Override
        public int read() throws IOException {
            while (true) {
                // Open next chunk if needed
                if (currentChunkStream == null) {
                    if (currentChunkIndex >= totalChunks) {
                        return -1; // All chunks read
                    }
                    Path chunkFile = uploadDir.resolve(String.format("chunk_%05d", currentChunkIndex));
                    currentChunkStream = Files.newInputStream(chunkFile);
                    currentChunkIndex++;
                }

                // Read from current chunk
                int b = currentChunkStream.read();
                if (b == -1) {
                    // Current chunk exhausted, close it and try next
                    currentChunkStream.close();
                    currentChunkStream = null;
                    continue;
                }
                return b;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            while (true) {
                // Open next chunk if needed
                if (currentChunkStream == null) {
                    if (currentChunkIndex >= totalChunks) {
                        return -1; // All chunks read
                    }
                    Path chunkFile = uploadDir.resolve(String.format("chunk_%05d", currentChunkIndex));
                    currentChunkStream = Files.newInputStream(chunkFile);
                    currentChunkIndex++;
                }

                // Read from current chunk
                int bytesRead = currentChunkStream.read(b, off, len);
                if (bytesRead == -1) {
                    // Current chunk exhausted, close it and try next
                    currentChunkStream.close();
                    currentChunkStream = null;
                    continue;
                }
                return bytesRead;
            }
        }

        @Override
        public void close() throws IOException {
            if (currentChunkStream != null) {
                currentChunkStream.close();
            }
        }
    }

    /**
     * Recursively delete directory
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete: {}", path, e);
                        }
                    });
        }
    }

    /**
     * Determine content type from filename
     */
    private String determineContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "mp4" -> "video/mp4";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    /**
     * Format file size
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
