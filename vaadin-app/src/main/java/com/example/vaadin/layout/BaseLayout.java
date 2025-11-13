package com.example.vaadin.layout;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Layout base che contiene il codice comune per header e user info.
 * Le sottoclassi decidono se mostrare il drawer (menu laterale) o meno.
 */
public abstract class BaseLayout extends AppLayout {

    protected void createHeader(String title, boolean showUserRole, String activeRole) {
        H1 logo = new H1(title);
        logo.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("margin", "0");

        // User info e logout
        HorizontalLayout userSection = createUserSection(showUserRole, activeRole);

        HorizontalLayout header = new HorizontalLayout(logo);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle()
            .set("padding", "0 var(--lumo-space-m)")
            .set("background", "var(--lumo-primary-color)")
            .set("color", "var(--lumo-primary-contrast-color)");

        HorizontalLayout headerWithUser = new HorizontalLayout(header, userSection);
        headerWithUser.setWidthFull();
        headerWithUser.expand(header);
        headerWithUser.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        headerWithUser.getStyle()
            .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
            .set("background", "var(--lumo-primary-color)")
            .set("color", "var(--lumo-primary-contrast-color)");

        addToNavbar(headerWithUser);
    }

    protected HorizontalLayout createUserSection(boolean showRole, String activeRole) {
        HorizontalLayout userSection = new HorizontalLayout();
        userSection.setAlignItems(FlexComponent.Alignment.CENTER);
        userSection.setSpacing(true);

        // Ottieni info utente
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User user = oauthToken.getPrincipal();
            String username = user.getAttribute("preferred_username");
            String firstName = user.getAttribute("given_name");
            
            String displayName = firstName != null ? firstName : (username != null ? username : user.getName());
            
            // Mostra ruolo se richiesto
            if (showRole && activeRole != null && !activeRole.isEmpty()) {
                Span roleInfo = new Span("🎭 " + activeRole);
                roleInfo.getStyle()
                    .set("color", "var(--lumo-primary-contrast-color)")
                    .set("font-weight", "bold")
                    .set("margin-right", "var(--lumo-space-m)");
                userSection.add(roleInfo);
            }
            
            Span userInfo = new Span("👤 " + displayName);
            userInfo.getStyle().set("margin-right", "var(--lumo-space-m)");
            
            userSection.add(userInfo);
        }

        // Logout button
        Button logoutBtn = new Button("Logout", VaadinIcon.SIGN_OUT.create());
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutBtn.addClickListener(e -> {
            getUI().ifPresent(ui -> {
                ui.getPage().executeJs("window.location.href = '/logout'");
            });
        });
        userSection.add(logoutBtn);

        return userSection;
    }
}
