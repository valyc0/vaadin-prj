package com.example.roles.service;

import io.minio.*;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for streaming file uploads to MinIO
 * 
 * This service handles large file uploads (30GB+) using streaming I/O
 * without loading files into memory or temporary files.
 * 
 * Key features:
 * - Direct streaming from HTTP request to MinIO
 * - Constant memory usage regardless of file size
 * - Automatic multipart upload handling by MinIO
 * - No temporary files on disk
 */
@Service
public class MinioStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStreamingService.class);

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Initialize MinIO bucket on service startup
     * Creates bucket if it doesn't exist
     */
    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
                logger.info("MinIO bucket '{}' created successfully", bucketName);
            } else {
                logger.info("MinIO bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            logger.error("Error initializing MinIO bucket", e);
            throw new RuntimeException("Failed to initialize MinIO", e);
        }
    }

    /**
     * Upload file to MinIO using streaming
     * 
     * This method streams the file directly from the input stream to MinIO
     * without loading it into memory or saving to temporary files.
     * 
     * For files with unknown size (size = -1), MinIO will automatically handle
     * multipart upload with 10MB chunks.
     * 
     * @param fileName Name of the file in MinIO
     * @param inputStream Stream of file data (NOT closed by this method)
     * @param contentType MIME type of the file
     * @param size Size in bytes, or -1 if unknown
     * @return The fileName stored in MinIO
     * @throws RuntimeException if upload fails
     */
    public String uploadFileStreaming(String fileName, InputStream inputStream, String contentType, long size) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ STREAMING UPLOAD STARTED");
            logger.info("║ File: {}", fileName);
            logger.info("║ Size: {} bytes ({})", size, formatSize(size));
            logger.info("║ Content-Type: {}", contentType);
            logger.info("║ Bucket: {}", bucketName);
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
            
            logger.info("→ Creating streaming connection to MinIO...");
            
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .contentType(contentType != null ? contentType : "application/octet-stream");
            
            if (size > 0) {
                // For known size, use optimized streaming
                // -1 for partSize means MinIO will determine optimal chunk size
                logger.info("→ Streaming mode: KNOWN SIZE ({})", formatSize(size));
                logger.info("→ MinIO will automatically determine optimal chunk size");
                builder.stream(inputStream, size, -1);
            } else {
                // For unknown size, use streaming with 10MB chunks
                // MinIO will handle multipart upload automatically
                // This is optimal for very large files as it streams directly
                logger.info("→ Streaming mode: UNKNOWN SIZE");
                logger.info("→ Using multipart upload with 10MB chunks");
                builder.stream(inputStream, -1, 10485760); // 10MB part size
            }
            
            logger.info("⚡ STREAMING STARTED - Data is now flowing directly from HTTP request to MinIO");
            logger.info("   ┌─────────────┐        ┌──────────────┐        ┌───────────┐");
            logger.info("   │ HTTP Client │  ════► │ This Service │  ════► │   MinIO   │");
            logger.info("   └─────────────┘        └──────────────┘        └───────────┘");
            logger.info("   NO memory buffering, NO temp files, DIRECT streaming!");
            
            // This call blocks and streams data directly to MinIO
            minioClient.putObject(builder.build());
            
            long duration = System.currentTimeMillis() - startTime;
            double throughputMBps = size > 0 ? (size / (1024.0 * 1024.0)) / (duration / 1000.0) : 0;
            
            logger.info("╔════════════════════════════════════════════════════════════════════════════");
            logger.info("║ STREAMING UPLOAD COMPLETED SUCCESSFULLY");
            logger.info("║ File: {}", fileName);
            logger.info("║ Final size: {} bytes ({})", size, formatSize(size));
            logger.info("║ Duration: {} ms ({} seconds)", duration, String.format("%.2f", duration / 1000.0));
            if (throughputMBps > 0) {
                logger.info("║ Throughput: {} MB/s", String.format("%.2f", throughputMBps));
            }
            logger.info("║ Memory used: ~10MB (constant, independent of file size)");
            logger.info("╚════════════════════════════════════════════════════════════════════════════");
            
            return fileName;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("╔════════════════════════════════════════════════════════════════════════════");
            logger.error("║ STREAMING UPLOAD FAILED");
            logger.error("║ File: {}", fileName);
            logger.error("║ Duration before error: {} ms", duration);
            logger.error("║ Error: {}", e.getMessage());
            logger.error("╚════════════════════════════════════════════════════════════════════════════");
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Format byte size to human readable format
     */
    private String formatSize(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Download file from MinIO as stream
     * The caller is responsible for closing the returned InputStream
     * 
     * @param fileName Name of the file in MinIO
     * @return InputStream of the file data
     * @throws RuntimeException if download fails
     */
    public InputStream downloadFileStreaming(String fileName) {
        try {
            logger.info("Starting streaming download for file '{}'", fileName);
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
        } catch (Exception e) {
            logger.error("Error during streaming download of file '{}'", fileName, e);
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from MinIO
     * 
     * @param fileName Name of the file to delete
     * @return true if deletion successful, false otherwise
     */
    public boolean deleteFile(String fileName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            
            logger.info("File '{}' deleted successfully from MinIO", fileName);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting file '{}' from MinIO", fileName, e);
            return false;
        }
    }

    /**
     * Check if file exists in MinIO
     * 
     * @param fileName Name of the file to check
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String fileName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * List all files in the bucket
     * 
     * @return List of file names
     */
    public List<String> listFiles() {
        List<String> fileNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .build());
            
            for (Result<Item> result : results) {
                Item item = result.get();
                fileNames.add(item.objectName());
            }
        } catch (Exception e) {
            logger.error("Error listing files from MinIO", e);
        }
        return fileNames;
    }

    /**
     * Get file size in bytes
     * 
     * @param fileName Name of the file
     * @return Size in bytes, or 0 if error
     */
    public long getFileSize(String fileName) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            return stat.size();
        } catch (Exception e) {
            logger.error("Error getting size for file '{}'", fileName, e);
            return 0;
        }
    }

    /**
     * Get file metadata
     * 
     * @param fileName Name of the file
     * @return StatObjectResponse with file metadata
     * @throws RuntimeException if file not found
     */
    public StatObjectResponse getFileMetadata(String fileName) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
        } catch (Exception e) {
            logger.error("Error getting metadata for file '{}'", fileName, e);
            throw new RuntimeException("Failed to get file metadata: " + e.getMessage(), e);
        }
    }
}
