package com.example.vaadin.controller;

import com.example.vaadin.service.FileDownloadProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller per gestire il download streaming dei file da MinIO
 * attraverso il roles-service, fungendo da proxy trasparente.
 */
@RestController
@RequestMapping("/api/download")
public class FileDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadController.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer per lo streaming
    
    private final FileDownloadProxyService fileDownloadProxyService;

    public FileDownloadController(FileDownloadProxyService fileDownloadProxyService) {
        this.fileDownloadProxyService = fileDownloadProxyService;
    }

    /**
     * Endpoint per il download in streaming di un file.
     * Ottiene lo stream dal service e lo scrive nella response.
     * 
     * @param fileName nome del file da scaricare (URL encoded)
     * @param request richiesta HTTP
     * @param response risposta HTTP dove scrivere lo stream
     */
    @GetMapping("/{fileName}")
    public void downloadFile(
            @PathVariable String fileName,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            // Decodifica il nome del file
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            logger.info("Received download request for file: {}", decodedFileName);
            
            // Ri-encode per la chiamata al backend
            String encodedFileName = java.net.URLEncoder.encode(decodedFileName, StandardCharsets.UTF_8);
            
            // Ottieni lo stream e i metadata dal service
            FileDownloadProxyService.DownloadResult downloadResult = 
                fileDownloadProxyService.getFileStream(decodedFileName, encodedFileName);
            
            // Configura gli header della response
            configureResponseHeaders(response, downloadResult);
            
            // Disabilita il buffering per un vero streaming
            response.setBufferSize(BUFFER_SIZE);
            
            // Streaming dei dati dal backend al browser
            streamToResponse(downloadResult.getInputStream(), response.getOutputStream(), decodedFileName);
            
            logger.info("Download completed successfully for file: {}", decodedFileName);
            
        } catch (IllegalStateException e) {
            // Errore di autenticazione
            logger.error("Authentication error for file: {}", fileName, e);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, 
                     "Authentication required: " + e.getMessage());
            
        } catch (Exception e) {
            // Errore generico
            logger.error("Error downloading file: {}", fileName, e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                     "Error downloading file: " + e.getMessage());
        }
    }

    /**
     * Configura gli header della response HTTP in base ai metadata ricevuti dal service
     */
    private void configureResponseHeaders(HttpServletResponse response, 
                                         FileDownloadProxyService.DownloadResult downloadResult) {
        
        // Content-Type
        String contentType = downloadResult.getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType != null) {
            response.setContentType(contentType);
        } else {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        
        // Content-Length
        String contentLength = downloadResult.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                response.setContentLengthLong(Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                logger.warn("Invalid Content-Length header: {}", contentLength);
            }
        }
        
        // Content-Disposition
        String contentDisposition = downloadResult.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        if (contentDisposition != null) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        }
    }

    /**
     * Trasferisce i dati dallo stream di input alla response in chunk,
     * implementando un vero streaming senza caricamento in memoria.
     */
    private void streamToResponse(InputStream inputStream, OutputStream outputStream, String fileName) 
            throws Exception {
        
        try (inputStream; outputStream) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesStreamed = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush(); // Flush immediato per streaming vero
                totalBytesStreamed += bytesRead;
                
                // Log ogni 10MB per monitorare il progresso
                if (totalBytesStreamed % (10 * 1024 * 1024) == 0) {
                    logger.debug("Streamed {} MB for file: {}", 
                               totalBytesStreamed / (1024 * 1024), fileName);
                }
            }
            
            logger.info("Successfully streamed {} bytes for file: {}", 
                      totalBytesStreamed, fileName);
            
        } catch (Exception e) {
            logger.error("Error during streaming for file: " + fileName, e);
            throw new RuntimeException("Streaming failed for file: " + fileName, e);
        }
    }

    /**
     * Invia un errore HTTP se la response non è già stata committed
     */
    private void sendError(HttpServletResponse response, int statusCode, String message) {
        try {
            if (!response.isCommitted()) {
                response.sendError(statusCode, message);
            }
        } catch (Exception ex) {
            logger.error("Error sending error response", ex);
        }
    }
}
