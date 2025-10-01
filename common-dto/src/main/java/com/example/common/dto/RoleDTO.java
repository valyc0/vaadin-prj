package com.example.common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Data Transfer Object for Role information
 */
public class RoleDTO implements Serializable {
    
    private Long id;
    private String name;
    private String description;
    private List<FunctionalityDTO> functionalities;

    // Constructors
    public RoleDTO() {}

    public RoleDTO(String name, String description, List<FunctionalityDTO> functionalities) {
        this.name = name;
        this.description = description;
        this.functionalities = functionalities;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<FunctionalityDTO> getFunctionalities() {
        return functionalities;
    }

    public void setFunctionalities(List<FunctionalityDTO> functionalities) {
        this.functionalities = functionalities;
    }
}
