package com.example.vaadin.service;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Service per gestire il download in streaming dei file dal roles-service.
 * Funziona come proxy trasparente, trasferendo i dati in chunk senza caricarli in memoria.
 */
@Service
public class FileDownloadProxyService {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadProxyService.class);
    private static final int BUFFER_SIZE = 8192; // 8KB buffer per lo streaming
    
    private final FileStreamingService fileStreamingService;
    private final RestClient restClient;

    public FileDownloadProxyService(FileStreamingService fileStreamingService) {
        this.fileStreamingService = fileStreamingService;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Scarica un file dal roles-service e lo scrive direttamente nella HttpServletResponse
     * in modalità streaming, senza caricarlo in memoria.
     * 
     * @param fileName nome del file da scaricare (già decodificato)
     * @param encodedFileName nome del file URL encoded per la richiesta HTTP
     * @param response HttpServletResponse dove scrivere lo stream
     * @throws Exception in caso di errori durante il download o lo streaming
     */
    public void streamFileDownload(String fileName, String encodedFileName, HttpServletResponse response) 
            throws Exception {
        
        logger.info("Starting proxy streaming for file: {}", fileName);
        
        // Ottieni il token JWT dalla sessione
        String jwtToken = fileStreamingService.getJwtToken();
        
        if (jwtToken == null || jwtToken.isEmpty()) {
            logger.error("JWT token not available for streaming download");
            throw new IllegalStateException("Authentication token not available");
        }
        
        // URL del roles-service
        String rolesServiceUrl = fileStreamingService.getRolesServiceUrl() + 
                                "/api/files/download/" + encodedFileName;
        
        logger.debug("Proxying streaming request to: {}", rolesServiceUrl);
        
        // Effettua la richiesta al roles-service e gestisci lo streaming
        restClient.method(HttpMethod.GET)
            .uri(rolesServiceUrl)
            .header("Authorization", "Bearer " + jwtToken)
            .exchange((clientRequest, clientResponse) -> {
                
                HttpStatus statusCode = (HttpStatus) clientResponse.getStatusCode();
                
                if (!statusCode.is2xxSuccessful()) {
                    logger.error("Error from roles-service: {}", statusCode);
                    throw new RuntimeException("Backend returned status: " + statusCode);
                }
                
                // Copia gli header importanti dalla risposta del backend
                copyResponseHeaders(clientResponse, response, fileName);
                
                // Disabilita il buffering per un vero streaming
                response.setBufferSize(BUFFER_SIZE);
                
                // Streaming dei dati dal backend al browser
                streamData(clientResponse.getBody(), response.getOutputStream(), fileName);
                
                return null;
            });
    }

    /**
     * Copia gli header rilevanti dalla risposta del backend alla risposta HTTP
     */
    private void copyResponseHeaders(
            org.springframework.http.client.ClientHttpResponse clientResponse,
            HttpServletResponse response,
            String fileName) {
        
        String contentType = clientResponse.getHeaders().getFirst("Content-Type");
        String contentLength = clientResponse.getHeaders().getFirst("Content-Length");
        String contentDisposition = clientResponse.getHeaders().getFirst("Content-Disposition");
        
        // Imposta Content-Type
        if (contentType != null) {
            response.setContentType(contentType);
        } else {
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        
        // Imposta Content-Length se disponibile
        if (contentLength != null) {
            try {
                response.setContentLengthLong(Long.parseLong(contentLength));
            } catch (NumberFormatException e) {
                logger.warn("Invalid Content-Length header: {}", contentLength);
            }
        }
        
        // Imposta Content-Disposition
        if (contentDisposition != null) {
            response.setHeader("Content-Disposition", contentDisposition);
        } else {
            // Crea un header Content-Disposition sicuro
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            response.setHeader("Content-Disposition", 
                             "attachment; filename=\"" + safeFileName + "\"");
        }
    }

    /**
     * Trasferisce i dati dallo stream di input allo stream di output in chunk,
     * implementando un vero streaming senza caricamento in memoria.
     */
    private void streamData(InputStream inputStream, OutputStream outputStream, String fileName) {
        
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
            
            logger.info("Successfully completed streaming of {} bytes for file: {}", 
                      totalBytesStreamed, fileName);
            
        } catch (Exception e) {
            logger.error("Error during streaming for file: " + fileName, e);
            throw new RuntimeException("Streaming failed for file: " + fileName, e);
        }
    }
}
