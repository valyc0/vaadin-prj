package com.example.vaadin.upload;

import com.vaadin.flow.component.upload.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * True streaming receiver for Vaadin Upload component.
 * 
 * This receiver does NOT buffer the file in memory or on disk.
 * Instead, it creates a pipe between the upload stream and the consumer,
 * allowing data to flow directly from browser to backend service.
 * 
 * Key features:
 * - Zero memory footprint (no buffering)
 * - No temporary files
 * - Data flows immediately as it arrives
 * - Suitable for files of any size (30GB+)
 * 
 * Architecture:
 * Browser → Vaadin Upload → PipedOutputStream → PipedInputStream → WebFlux → Service
 *                                    ↑________________↓
 *                                   (pipe in memory, 
 *                                    only small buffer)
 */
public class StreamingReceiver implements Receiver {

    private static final Logger logger = LoggerFactory.getLogger(StreamingReceiver.class);
    private static final int PIPE_BUFFER_SIZE = 8192; // 8KB pipe buffer

    private PipedOutputStream pipedOutputStream;
    private PipedInputStream pipedInputStream;
    private BiConsumer<String, PipedInputStream> streamConsumer;
    private String currentFileName;
    private String currentMimeType;
    private CompletableFuture<Void> uploadFuture;

    /**
     * Constructor
     * 
     * @param streamConsumer Consumer that will receive the filename and InputStream
     *                       This will be called immediately when upload starts,
     *                       allowing the consumer to start reading while data is still uploading
     */
    public StreamingReceiver(BiConsumer<String, PipedInputStream> streamConsumer) {
        this.streamConsumer = streamConsumer;
    }

    @Override
    public OutputStream receiveUpload(String fileName, String mimeType) {
        this.currentFileName = fileName;
        this.currentMimeType = mimeType;

        logger.info("╔════════════════════════════════════════════════════════════════════════════");
        logger.info("║ STREAMING RECEIVER - Upload Started");
        logger.info("║ File: {}", fileName);
        logger.info("║ MIME Type: {}", mimeType);
        logger.info("║ Mode: TRUE STREAMING (NO buffering)");
        logger.info("╚════════════════════════════════════════════════════════════════════════════");

        try {
            // Create piped streams for direct data flow
            pipedInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
            pipedOutputStream = new PipedOutputStream(pipedInputStream);

            logger.info("✓ Piped streams created (buffer: {} bytes)", PIPE_BUFFER_SIZE);
            logger.info("⚡ Data will flow IMMEDIATELY as it arrives from browser");

            // Start consuming the stream in a separate thread
            // This allows the upload to proceed while we're reading
            uploadFuture = CompletableFuture.runAsync(() -> {
                try {
                    logger.info("→ Stream consumer started for file: {}", fileName);
                    streamConsumer.accept(fileName, pipedInputStream);
                    logger.info("✓ Stream consumer completed for file: {}", fileName);
                } catch (Exception e) {
                    logger.error("✗ Error in stream consumer for file: {}", fileName, e);
                    throw new RuntimeException("Stream consumption failed", e);
                }
            });

            logger.info("→ Returning PipedOutputStream to Vaadin Upload component");
            logger.info("   Data flow: Browser → Upload → PipedOutputStream → PipedInputStream → WebFlux");

            return pipedOutputStream;

        } catch (IOException e) {
            logger.error("Failed to create piped streams", e);
            throw new RuntimeException("Failed to initialize streaming upload", e);
        }
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public String getCurrentMimeType() {
        return currentMimeType;
    }

    public CompletableFuture<Void> getUploadFuture() {
        return uploadFuture;
    }

    /**
     * Cleanup method - should be called after upload completes
     */
    public void cleanup() {
        try {
            if (pipedOutputStream != null) {
                pipedOutputStream.close();
            }
            if (pipedInputStream != null) {
                pipedInputStream.close();
            }
            logger.debug("Streams cleaned up for file: {}", currentFileName);
        } catch (IOException e) {
            logger.warn("Error cleaning up streams", e);
        }
    }
}
