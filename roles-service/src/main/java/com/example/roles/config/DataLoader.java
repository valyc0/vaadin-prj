package com.example.roles.config;

import com.example.entity.model.Functionality;
import com.example.entity.model.Role;
import com.example.entity.model.User;
import com.example.entity.repository.FunctionalityRepository;
import com.example.entity.repository.RoleRepository;
import com.example.entity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Data loader to initialize test data on application startup
 */
@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private FunctionalityRepository functionalityRepository;

    @Override
    public void run(String... args) throws Exception {
        
        // Create functionalities
        Functionality readUsers = new Functionality("READ_USERS", "View user list", "USER_MANAGEMENT");
        Functionality createUsers = new Functionality("CREATE_USERS", "Create new users", "USER_MANAGEMENT");
        Functionality updateUsers = new Functionality("UPDATE_USERS", "Modify existing users", "USER_MANAGEMENT");
        Functionality deleteUsers = new Functionality("DELETE_USERS", "Delete users", "USER_MANAGEMENT");
        
        Functionality readReports = new Functionality("READ_REPORTS", "View reports", "REPORTING");
        Functionality createReports = new Functionality("CREATE_REPORTS", "Create new reports", "REPORTING");
        Functionality exportReports = new Functionality("EXPORT_REPORTS", "Export reports", "REPORTING");
        
        Functionality adminSettings = new Functionality("ADMIN_SETTINGS", "Manage system settings", "ADMINISTRATION");
        Functionality systemLogs = new Functionality("SYSTEM_LOGS", "View system logs", "ADMINISTRATION");
        Functionality backupRestore = new Functionality("BACKUP_RESTORE", "Backup and restore", "ADMINISTRATION");
        
        Functionality readData = new Functionality("READ_DATA", "View data", "DATA_ACCESS");
        Functionality writeData = new Functionality("WRITE_DATA", "Modify data", "DATA_ACCESS");

        // Save functionalities
        functionalityRepository.saveAll(Arrays.asList(
            readUsers, createUsers, updateUsers, deleteUsers,
            readReports, createReports, exportReports,
            adminSettings, systemLogs, backupRestore,
            readData, writeData
        ));

        // Create roles
        Role guestRole = new Role("GUEST", "Guest user with limited access");
        guestRole.setFunctionalities(Set.of(readData));

        Role userRole = new Role("USER", "Standard user");
        userRole.setFunctionalities(Set.of(readData, readReports));

        Role managerRole = new Role("MANAGER", "Manager with extended privileges");
        managerRole.setFunctionalities(Set.of(
            readData, writeData, readUsers, 
            readReports, createReports, exportReports
        ));

        Role adminRole = new Role("ADMIN", "Administrator with full access");
        adminRole.setFunctionalities(Set.of(
            readUsers, createUsers, updateUsers, deleteUsers,
            readReports, createReports, exportReports,
            adminSettings, systemLogs, backupRestore,
            readData, writeData
        ));

        // Save roles
        roleRepository.saveAll(Arrays.asList(guestRole, userRole, managerRole, adminRole));

        // Create users
        User testUser = new User("test", "Test", "User", "test@example.com");
        User adminUser = new User("admin", "Admin", "User", "admin@example.com");
        User managerUser = new User("manager", "Manager", "User", "manager@example.com");
        User guestUser = new User("guest", "Guest", "User", "guest@example.com");

        // Save users
        userRepository.saveAll(Arrays.asList(testUser, adminUser, managerUser, guestUser));

        // Associate users with roles
        testUser.setRoles(Set.of(userRole, managerRole));
        adminUser.setRoles(Set.of(adminRole));
        managerUser.setRoles(Set.of(managerRole));
        guestUser.setRoles(Set.of(guestRole));

        // Update associations
        userRole.getUsers().addAll(Set.of(testUser));
        managerRole.getUsers().addAll(Set.of(testUser, managerUser));
        adminRole.getUsers().addAll(Set.of(adminUser));
        guestRole.getUsers().addAll(Set.of(guestUser));

        // Save changes
        userRepository.saveAll(Arrays.asList(testUser, adminUser, managerUser, guestUser));
        roleRepository.saveAll(Arrays.asList(guestRole, userRole, managerRole, adminRole));

        System.out.println("✅ Test data initialized successfully!");
        System.out.println("👤 Users created: test, admin, manager, guest");
        System.out.println("🎭 Roles created: GUEST, USER, MANAGER, ADMIN");
        System.out.println("⚙️ Functionalities created: " + functionalityRepository.count());
    }
}
