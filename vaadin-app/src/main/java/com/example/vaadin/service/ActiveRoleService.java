package com.example.vaadin.service;

import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Service;

/**
 * Service to manage active role in Vaadin session
 */
@Service
public class ActiveRoleService {
    
    private static final String ACTIVE_ROLE_KEY = "activeRole";
    private static final String ACTIVE_ROLE_DESCRIPTION_KEY = "activeRoleDescription";
    
    /**
     * Set active role for user in current session
     */
    public void setActiveRole(String roleName, String roleDescription) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ACTIVE_ROLE_KEY, roleName);
            session.setAttribute(ACTIVE_ROLE_DESCRIPTION_KEY, roleDescription);
            System.out.println("Active role set in session: " + roleName);
        }
    }
    
    /**
     * Get active role from current session
     */
    public String getActiveRole() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            return (String) session.getAttribute(ACTIVE_ROLE_KEY);
        }
        return null;
    }
    
    /**
     * Get active role description from current session
     */
    public String getActiveRoleDescription() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            return (String) session.getAttribute(ACTIVE_ROLE_DESCRIPTION_KEY);
        }
        return null;
    }
    
    /**
     * Check if user has active role set
     */
    public boolean hasActiveRole() {
        return getActiveRole() != null;
    }
    
    /**
     * Clear active role from session
     */
    public void clearActiveRole() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ACTIVE_ROLE_KEY, null);
            session.setAttribute(ACTIVE_ROLE_DESCRIPTION_KEY, null);
            System.out.println("Active role removed from session");
        }
    }
}
