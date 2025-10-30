package com.example.vaadin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final Div statusDiv;
    private final Paragraph statusText;

    public SimpleStreamingUploadView(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
        
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
        
        // Upload button
        Button uploadButton = new Button("Select and Upload File");
        uploadButton.setId("uploadButton");
        add(uploadButton);
        
        // Status display
        statusDiv = new Div();
        statusDiv.setId("statusDiv");
        statusText = new Paragraph("");
        statusText.setId("statusText");
        statusDiv.add(statusText);
        add(statusDiv);
        
        // Load JavaScript and inject JWT token
        UI.getCurrent().getPage().executeJs(
            "window.jwtToken = $0;",
            getJwtToken()
        );
        
        // Load the JavaScript file
        UI.getCurrent().getPage().addJavaScript("/js/simple-streaming-upload.js");
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
