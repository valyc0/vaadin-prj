package com.example.entity.repository;

import com.example.entity.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Role entity
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    /**
     * Find role by name
     */
    Optional<Role> findByName(String name);
    
    /**
     * Find all roles with functionalities eagerly loaded
     */
    @Query("SELECT r FROM Role r JOIN FETCH r.functionalities")
    List<Role> findAllWithFunctionalities();
    
    /**
     * Find roles by names with functionalities eagerly loaded
     */
    @Query("SELECT r FROM Role r JOIN FETCH r.functionalities WHERE r.name IN :roleNames")
    List<Role> findByNameInWithFunctionalities(@Param("roleNames") List<String> roleNames);
}
