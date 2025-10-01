package com.example.vaadin.service;

import com.example.common.dto.UserRolesDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/**
 * Service for calling the roles-service microservice
 */
@Service
public class RolesService {

    private final String rolesServiceUrl;
    private final RestTemplate restTemplate;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public RolesService(@Value("${roles.service-url}") String rolesServiceUrl,
                       OAuth2AuthorizedClientService authorizedClientService) {
        this.rolesServiceUrl = rolesServiceUrl;
        this.restTemplate = new RestTemplate();
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Get user roles and functionalities from roles-service
     */
    public UserRolesDTO getUserRolesAndFunctionalities() {
        try {
            // Get OAuth2 token from current user
            String accessToken = getCurrentAccessToken();
            
            if (accessToken == null) {
                System.err.println("No access token available");
                return null;
            }
            
            // Create headers with Bearer token
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Call roles-service without username in URL (extracted from JWT)
            ResponseEntity<UserRolesDTO> response = restTemplate.exchange(
                rolesServiceUrl,
                HttpMethod.GET,
                entity,
                UserRolesDTO.class
            );
            
            return response.getBody();
            
        } catch (RestClientException e) {
            System.err.println("Error fetching roles: " + e.getMessage());
            e.printStackTrace();
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
                System.err.println("No access token available");
                return Collections.singletonList("NO_TOKEN");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String[]> response = restTemplate.exchange(
                rolesServiceUrl,
                HttpMethod.GET,
                entity,
                String[].class
            );
            
            String[] roles = response.getBody();
            return roles != null ? Arrays.asList(roles) : Collections.emptyList();
            
        } catch (RestClientException e) {
            System.err.println("Error fetching roles: " + e.getMessage());
            e.printStackTrace();
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
                System.out.println("Access token obtained: " + accessToken.getTokenValue().substring(0, 50) + "...");
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
            System.out.println("Current username: " + username);
            return username;
        }
        
        return null;
    }
}
