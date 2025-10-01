package com.example.entity.repository;

import com.example.entity.model.Functionality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Functionality entity
 */
@Repository
public interface FunctionalityRepository extends JpaRepository<Functionality, Long> {
    
    /**
     * Find functionality by name
     */
    Optional<Functionality> findByName(String name);
}
