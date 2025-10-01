package com.example.entity.repository;

import com.example.entity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by username with roles and functionalities eagerly loaded
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles r JOIN FETCH r.functionalities WHERE u.username = :username")
    Optional<User> findByUsernameWithRolesAndFunctionalities(@Param("username") String username);
}
