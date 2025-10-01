package com.example.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for Role-Functionality table display
 */
public class RoleFunctionalityTableDTO implements Serializable {
    
    private String roleName;
    private String roleDescription;
    private String functionalityName;
    private String functionalityDescription;
    private String functionalityCategory;

    // Constructors
    public RoleFunctionalityTableDTO() {}

    public RoleFunctionalityTableDTO(String roleName, String roleDescription, 
                                   String functionalityName, String functionalityDescription, 
                                   String functionalityCategory) {
        this.roleName = roleName;
        this.roleDescription = roleDescription;
        this.functionalityName = functionalityName;
        this.functionalityDescription = functionalityDescription;
        this.functionalityCategory = functionalityCategory;
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

    public String getFunctionalityName() {
        return functionalityName;
    }

    public void setFunctionalityName(String functionalityName) {
        this.functionalityName = functionalityName;
    }

    public String getFunctionalityDescription() {
        return functionalityDescription;
    }

    public void setFunctionalityDescription(String functionalityDescription) {
        this.functionalityDescription = functionalityDescription;
    }

    public String getFunctionalityCategory() {
        return functionalityCategory;
    }

    public void setFunctionalityCategory(String functionalityCategory) {
        this.functionalityCategory = functionalityCategory;
    }
}
