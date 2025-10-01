package com.example.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for Role table display
 */
public class RoleTableDTO implements Serializable {
    
    private String roleName;
    private String roleDescription;
    private String functionalities; // String with all functionalities separated by commas

    // Constructors
    public RoleTableDTO() {}

    public RoleTableDTO(String roleName, String roleDescription, String functionalities) {
        this.roleName = roleName;
        this.roleDescription = roleDescription;
        this.functionalities = functionalities;
    }

    // Getters and Setters
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public void setRoleDescription(String roleDescription) {
        this.roleDescription = roleDescription;
    }

    public String getFunctionalities() {
        return functionalities;
    }

    public void setFunctionalities(String functionalities) {
        this.functionalities = functionalities;
    }
}
