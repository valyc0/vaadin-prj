package com.example.common.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for Functionality information
 */
public class FunctionalityDTO implements Serializable {
    
    private Long id;
    private String name;
    private String description;
    private String category;

    // Constructors
    public FunctionalityDTO() {}

    public FunctionalityDTO(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
