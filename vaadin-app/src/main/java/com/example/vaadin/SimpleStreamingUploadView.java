package com.example.vaadin;

import com.example.vaadin.service.FileStreamingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * Simple streaming upload view using pure JavaScript
 * - No chunking
 * - True streaming with ReadableStream
 * - Minimal memory usage
 * - Direct streaming to backend
 */
@Route("simple-streaming-upload")
@PermitAll
public class SimpleStreamingUploadView extends VerticalLayout {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final FileStreamingService fileStreamingService;
    private final Div statusDiv;
    private final Paragraph statusText;
    private final Grid<String> filesGrid;

    public SimpleStreamingUploadView(OAuth2AuthorizedClientService authorizedClientService,
                                      FileStreamingService fileStreamingService) {
        this.authorizedClientService = authorizedClientService;
        this.fileStreamingService = fileStreamingService;
        
        setSpacing(true);
        setPadding(true);
        setWidth("100%");
        
        // Title
        H2 title = new H2("Simple Streaming File Upload");
        add(title);
        
        // Description
        Paragraph description = new Paragraph(
            "Pure JavaScript streaming upload - no chunking, no memory buffering. " +
            "The file is read as a stream and sent directly to the backend."
        );
        add(description);
        
        // Upload button and refresh button
        Button uploadButton = new Button("Select and Upload File");
        uploadButton.setId("uploadButton");
        
        Button refreshButton = new Button("Refresh List");
        refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        refreshButton.addClickListener(e -> refreshFilesList());
        
        HorizontalLayout buttonsLayout = new HorizontalLayout(uploadButton, refreshButton);
        buttonsLayout.setAlignItems(Alignment.BASELINE);
        add(buttonsLayout);
        
        // Status display
        statusDiv = new Div();
        statusDiv.setId("statusDiv");
        statusText = new Paragraph("");
        statusText.setId("statusText");
        statusDiv.add(statusText);
        add(statusDiv);
        
        // Files Grid
        H2 filesTitle = new H2("Uploaded Files");
        add(filesTitle);
        
        filesGrid = new Grid<>(String.class, false);
        filesGrid.setWidth("100%");
        filesGrid.setHeight("400px");
        
        // Column for filename
        filesGrid.addColumn(filename -> filename)
                .setHeader("File Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        
        // Column for actions
        filesGrid.addComponentColumn(filename -> {
            Button deleteButton = new Button("Delete");
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteButton.addClickListener(e -> deleteFile(filename));
            
            Button metadataButton = new Button("Metadata");
            metadataButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            metadataButton.addClickListener(e -> showMetadata(filename));
            
            HorizontalLayout actions = new HorizontalLayout(metadataButton, deleteButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions").setAutoWidth(true);
        
        add(filesGrid);
        
        // Load files list
        refreshFilesList();
        
        // Load JavaScript and inject JWT token
        UI.getCurrent().getPage().executeJs(
            "window.jwtToken = $0;",
            getJwtToken()
        );
        
        // Add listener for successful upload to refresh grid
        UI.getCurrent().getPage().executeJs(
            "window.addEventListener('uploadSuccess', function() { " +
            "  console.log('Upload completed, triggering refresh'); " +
            "  document.getElementById('refreshButton').click(); " +
            "});"
        );
        
        refreshButton.setId("refreshButton");
        
        // Load the JavaScript file
        UI.getCurrent().getPage().addJavaScript("/js/simple-streaming-upload.js");
    }
    
    /**
     * Refresh the files list from roles-service/MinIO
     */
    private void refreshFilesList() {
        try {
            String[] files = fileStreamingService.listFiles();
            
            if (files != null && files.length > 0) {
                filesGrid.setItems(files);
                Notification.show("Files list refreshed: " + files.length + " files", 
                    3000, Notification.Position.BOTTOM_END);
            } else {
                filesGrid.setItems();
                Notification.show("No files found", 
                    3000, Notification.Position.BOTTOM_END);
            }
        } catch (Exception e) {
            Notification notification = Notification.show(
                "Error loading files: " + e.getMessage(), 
                5000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    /**
     * Delete a file
     */
    private void deleteFile(String filename) {
        try {
            fileStreamingService.deleteFile(filename);
            
            Notification notification = Notification.show(
                "File deleted: " + filename, 
                3000, Notification.Position.BOTTOM_END);
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            
            refreshFilesList();
        } catch (Exception e) {
            Notification notification = Notification.show(
                "Error deleting file: " + e.getMessage(), 
                5000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    /**
     * Show file metadata
     */
    private void showMetadata(String filename) {
        try {
            Map<String, Object> metadata = fileStreamingService.getFileMetadata(filename);
            
            if (metadata != null) {
                StringBuilder info = new StringBuilder("Metadata for " + filename + ":\n");
                metadata.forEach((key, value) -> 
                    info.append(key).append(": ").append(value).append("\n")
                );
                
                Notification notification = Notification.show(
                    info.toString(), 
                    5000, Notification.Position.MIDDLE);
                notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            }
        } catch (Exception e) {
            Notification notification = Notification.show(
                "Error loading metadata: " + e.getMessage(), 
                5000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
    
    /**
     * Get JWT token from current authenticated user
     */
    private String getJwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return "";
        }
        
        try {
            OAuth2AuthorizedClient client = authorizedClientService
                    .loadAuthorizedClient("keycloak", authentication.getName());
            
            if (client == null) {
                return "";
            }
            
            return client.getAccessToken().getTokenValue();
            
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
