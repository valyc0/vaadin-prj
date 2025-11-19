package com.example.vaadin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service per gestire il download in streaming dei file dal roles-service.
 * Restituisce lo stream e i metadata, lasciando al controller la gestione della HttpServletResponse.
 */
@Service
public class FileDownloadProxyService {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadProxyService.class);
    
    private final FileStreamingService fileStreamingService;
    private final RestClient restClient;

    public FileDownloadProxyService(FileStreamingService fileStreamingService) {
        this.fileStreamingService = fileStreamingService;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Risultato del download contenente lo stream e i metadata HTTP
     */
    public static class DownloadResult {
        private final InputStream inputStream;
        private final Map<String, String> headers;
        
        public DownloadResult(InputStream inputStream, Map<String, String> headers) {
            this.inputStream = inputStream;
            this.headers = headers;
        }
        
        public InputStream getInputStream() {
            return inputStream;
        }
        
        public Map<String, String> getHeaders() {
            return headers;
        }
        
        public String getHeader(String name) {
            return headers.get(name);
        }
    }

    /**
     * Ottiene lo stream del file dal roles-service insieme ai suoi metadata HTTP.
     * Il controller sarà responsabile di scrivere lo stream nella response.
     * 
     * @param fileName nome del file da scaricare (già decodificato)
     * @param encodedFileName nome del file URL encoded per la richiesta HTTP
     * @return DownloadResult contenente lo stream e gli header HTTP
     * @throws Exception in caso di errori durante il download
     */
    public DownloadResult getFileStream(String fileName, String encodedFileName) throws Exception {
        
        logger.info("Requesting file stream for: {}", fileName);
        
        // Ottieni il token JWT dalla sessione
        String jwtToken = fileStreamingService.getJwtToken();
        
        if (jwtToken == null || jwtToken.isEmpty()) {
            logger.error("JWT token not available for streaming download");
            throw new IllegalStateException("Authentication token not available");
        }
        
        // URL del roles-service
        String rolesServiceUrl = fileStreamingService.getRolesServiceUrl() + 
                                "/api/files/download/" + encodedFileName;
        
        logger.debug("Requesting stream from: {}", rolesServiceUrl);
        
        // Effettua la richiesta al roles-service e restituisci lo stream
        return restClient.method(HttpMethod.GET)
            .uri(rolesServiceUrl)
            .header("Authorization", "Bearer " + jwtToken)
            .exchange((clientRequest, clientResponse) -> {
                
                HttpStatus statusCode = (HttpStatus) clientResponse.getStatusCode();
                
                if (!statusCode.is2xxSuccessful()) {
                    logger.error("Error from roles-service: {}", statusCode);
                    throw new RuntimeException("Backend returned status: " + statusCode);
                }
                
                // Estrai gli header rilevanti
                Map<String, String> headers = extractHeaders(clientResponse.getHeaders(), fileName);
                
                // Restituisci lo stream e i metadata
                InputStream inputStream = clientResponse.getBody();
                
                logger.info("Stream obtained successfully for file: {}", fileName);
                
                return new DownloadResult(inputStream, headers);
            });
    }

    /**
     * Estrae gli header HTTP rilevanti dalla risposta del backend
     */
    private Map<String, String> extractHeaders(HttpHeaders httpHeaders, String fileName) {
        Map<String, String> headers = new HashMap<>();
        
        String contentType = httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        String contentLength = httpHeaders.getFirst(HttpHeaders.CONTENT_LENGTH);
        String contentDisposition = httpHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        
        if (contentType != null) {
            headers.put(HttpHeaders.CONTENT_TYPE, contentType);
        }
        
        if (contentLength != null) {
            headers.put(HttpHeaders.CONTENT_LENGTH, contentLength);
        }
        
        if (contentDisposition != null) {
            headers.put(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        } else {
            // Crea un Content-Disposition sicuro se non presente
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            headers.put(HttpHeaders.CONTENT_DISPOSITION, 
                       "attachment; filename=\"" + safeFileName + "\"");
        }
        
        return headers;
    }
}
