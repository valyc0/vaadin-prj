package com.example.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Data Transfer Object for User with Roles information
 */
public class UserRolesDTO implements Serializable {
    
    private String username;
    private List<RoleDTO> roles;

    // Constructors
    public UserRolesDTO() {}

    public UserRolesDTO(String username, List<RoleDTO> roles) {
        this.username = username;
        this.roles = roles;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<RoleDTO> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleDTO> roles) {
        this.roles = roles;
    }
}
