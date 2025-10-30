package com.example.vaadin.service;

import com.example.common.dto.UserRolesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/**
 * Service for calling the roles-service microservice using RestClient
 */
@Service
public class RolesService {

    private static final Logger logger = LoggerFactory.getLogger(RolesService.class);
    
    private final String rolesServiceUrl;
    private final RestClient restClient;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public RolesService(@Value("${roles.service-url}") String rolesServiceUrl,
                       RestClient.Builder restClientBuilder,
                       OAuth2AuthorizedClientService authorizedClientService) {
        this.rolesServiceUrl = rolesServiceUrl;
        this.restClient = restClientBuilder.build();
        this.authorizedClientService = authorizedClientService;
        logger.info("RolesService initialized with RestClient");
    }

    /**
     * Get user roles and functionalities from roles-service
     */
    public UserRolesDTO getUserRolesAndFunctionalities() {
        try {
            // Get OAuth2 token from current user
            String accessToken = getCurrentAccessToken();
            
            if (accessToken == null) {
                logger.warn("No access token available");
                return null;
            }
            
            // Call roles-service using RestClient with Bearer token
            UserRolesDTO result = restClient.get()
                    .uri(rolesServiceUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserRolesDTO.class);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error fetching roles: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get list of role names for the current user
     */
    public List<String> getUserRoles() {
        try {
            String accessToken = getCurrentAccessToken();
            
            if (accessToken == null) {
                logger.warn("No access token available");
                return Collections.singletonList("NO_TOKEN");
            }
            
            String[] roles = restClient.get()
                    .uri(rolesServiceUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String[].class);
            
            return roles != null ? Arrays.asList(roles) : Collections.emptyList();
            
        } catch (Exception e) {
            logger.error("Error fetching roles: {}", e.getMessage(), e);
            return Collections.singletonList("ERROR_LOADING_ROLES");
        }
    }
    
    /**
     * Get current OAuth2 access token
     */
    private String getCurrentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient("keycloak", oauthToken.getName());
            
            if (client != null) {
                OAuth2AccessToken accessToken = client.getAccessToken();
                logger.debug("Access token obtained: {}...", accessToken.getTokenValue().substring(0, 50));
                return accessToken.getTokenValue();
            }
        }
        
        return null;
    }

    /**
     * Get current username from OAuth2 token
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User user = oauthToken.getPrincipal();
            String username = user.getAttribute("preferred_username");
            if (username == null) {
                username = user.getName();
            }
            logger.debug("Current username: {}", username);
            return username;
        }
        
        return null;
    }
}
