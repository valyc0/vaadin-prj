package com.example.roles.controller;

import com.example.common.dto.FunctionalityDTO;
import com.example.common.dto.RoleDTO;
import com.example.common.dto.UserRolesDTO;
import com.example.entity.model.Functionality;
import com.example.entity.model.Role;
import com.example.entity.model.User;
import com.example.entity.repository.RoleRepository;
import com.example.entity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for managing user roles and functionalities
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "API for managing user roles and functionalities")
public class RoleController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;

    @GetMapping
    @Operation(
        summary = "Get user roles",
        description = "Returns the roles and functionalities of the authenticated user extracted from JWT token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Roles retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "User not found in database")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public UserRolesDTO getUserRoles(@Parameter(hidden = true) Authentication authentication) {
        // Extract username from JWT token
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            
            String username = extractUsername(jwt);
            System.out.println("JWT Claims: " + jwt.getClaims());
            System.out.println("Username extracted from token: " + username);
            
            // Find user in database with roles and functionalities
            User user = userRepository.findByUsernameWithRolesAndFunctionalities(username)
                .orElse(null);
            
            if (user != null) {
                List<RoleDTO> roleDtos = user.getRoles().stream()
                    .map(this::convertToRoleDto)
                    .collect(Collectors.toList());
                
                return new UserRolesDTO(username, roleDtos);
            } else {
                // User not found in DB, return default role
                System.out.println("User " + username + " not found in database");
                return new UserRolesDTO(username, List.of(
                    new RoleDTO("GUEST", "Default role for unregistered users", 
                        List.of(new FunctionalityDTO("READ_DATA", "View data", "DATA_ACCESS")))
                ));
            }
        }
        
        // Fallback if no authentication
        return new UserRolesDTO("anonymous", List.of(
            new RoleDTO("ANONYMOUS", "Anonymous user", List.of())
        ));
    }
    
    @GetMapping("/all")
    @Operation(
        summary = "Get all roles",
        description = "Returns all available roles in the system with their functionalities"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Role list retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing JWT token")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAllWithFunctionalities().stream()
            .map(this::convertToRoleDto)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/info")
    @Operation(
        summary = "JWT token information",
        description = "Returns information contained in JWT token for debugging"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token information retrieved"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing JWT token")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public Object getTokenInfo(@Parameter(hidden = true) Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaims();
        }
        return "No JWT token found";
    }
    
    /**
     * Extract username from JWT token trying different claims
     */
    private String extractUsername(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null) {
            username = jwt.getClaimAsString("sub");
        }
        if (username == null) {
            username = jwt.getClaimAsString("name");
        }
        if (username == null) {
            username = jwt.getClaimAsString("email");
        }
        return username;
    }
    
    /**
     * Convert Role entity to RoleDTO
     */
    private RoleDTO convertToRoleDto(Role role) {
        List<FunctionalityDTO> functionalityDtos = role.getFunctionalities().stream()
            .map(this::convertToFunctionalityDto)
            .collect(Collectors.toList());
            
        return new RoleDTO(role.getName(), role.getDescription(), functionalityDtos);
    }
    
    /**
     * Convert Functionality entity to FunctionalityDTO
     */
    private FunctionalityDTO convertToFunctionalityDto(Functionality functionality) {
        return new FunctionalityDTO(
            functionality.getName(), 
            functionality.getDescription(), 
            functionality.getCategory()
        );
    }
}
