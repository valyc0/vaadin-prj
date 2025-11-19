package com.example.vaadin.controller;

import com.example.vaadin.service.FileDownloadProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller per gestire il download streaming dei file da MinIO
 * attraverso il roles-service, fungendo da proxy trasparente.
 * Delega la logica di streaming al FileDownloadProxyService.
 */
@RestController
@RequestMapping("/api/download")
public class FileDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadController.class);
    
    private final FileDownloadProxyService fileDownloadProxyService;

    public FileDownloadController(FileDownloadProxyService fileDownloadProxyService) {
        this.fileDownloadProxyService = fileDownloadProxyService;
    }

    /**
     * Endpoint per il download in streaming di un file.
     * Fa da proxy tra il browser e il roles-service, delegando
     * la logica di streaming al FileDownloadProxyService.
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
            
            // Delega al service la gestione dello streaming
            fileDownloadProxyService.streamFileDownload(decodedFileName, encodedFileName, response);
            
            logger.info("Download completed successfully for file: {}", decodedFileName);
            
        } catch (IllegalStateException e) {
            // Errore di autenticazione
            logger.error("Authentication error for file: {}", fileName, e);
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, 
                                     "Authentication required: " + e.getMessage());
                }
            } catch (Exception ex) {
                logger.error("Error sending auth error response", ex);
            }
            
        } catch (Exception e) {
            // Errore generico
            logger.error("Error downloading file: {}", fileName, e);
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                                     "Error downloading file: " + e.getMessage());
                }
            } catch (Exception ex) {
                logger.error("Error sending error response", ex);
            }
        }
    }
}
