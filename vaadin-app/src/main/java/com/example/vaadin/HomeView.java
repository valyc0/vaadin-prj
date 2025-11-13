package com.example.vaadin;

import com.example.vaadin.layout.MainLayout;
import com.example.vaadin.service.ActiveRoleService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Route(value = "home", layout = MainLayout.class)
@AnonymousAllowed
public class HomeView extends VerticalLayout {

    private final ActiveRoleService activeRoleService;

    public HomeView(ActiveRoleService activeRoleService) {
        this.activeRoleService = activeRoleService;
        
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        
        createView();
    }
    
    private void createView() {
        // Header
        H1 title = new H1("🏠 Dashboard");
        title.getStyle().set("color", "var(--lumo-primary-color)");
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && 
            auth instanceof OAuth2AuthenticationToken oauthToken) {
            
            OAuth2User user = oauthToken.getPrincipal();
            
            // User info
            String username = user.getAttribute("preferred_username");
            String firstName = user.getAttribute("given_name");
            String lastName = user.getAttribute("family_name");
            String email = user.getAttribute("email");
            
            if (username == null) username = user.getName();
            
            // Card principale
            Div welcomeCard = createCard();
            
            H3 welcomeTitle = new H3("👋 Benvenuto!");
            Span userInfo = new Span("Utente: " + (firstName != null ? firstName + " " + lastName : username));
            userInfo.getStyle()
                .set("font-size", "1.1rem")
                .set("color", "var(--lumo-body-text-color)");
            
            if (email != null) {
                Span emailSpan = new Span("Email: " + email);
                emailSpan.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "0.9rem");
                welcomeCard.add(welcomeTitle, userInfo, emailSpan);
            } else {
                welcomeCard.add(welcomeTitle, userInfo);
            }
            
            // Separator
            Div separator = new Div();
            separator.getStyle()
                .set("height", "1px")
                .set("background", "var(--lumo-contrast-20pct)")
                .set("margin", "var(--lumo-space-m) 0");
            welcomeCard.add(separator);
            
            // Informazioni ruolo attivo
            String activeRole = activeRoleService.getActiveRole();
            String roleDescription = activeRoleService.getActiveRoleDescription();
            
            H3 roleTitle = new H3("🎭 Ruolo Attivo");
            
            if (activeRole != null && !activeRole.isEmpty()) {
                Span roleNameSpan = new Span("Ruolo: " + activeRole);
                roleNameSpan.getStyle()
                    .set("font-weight", "bold")
                    .set("font-size", "1.2rem")
                    .set("color", "var(--lumo-primary-color)");
                
                Span roleDescSpan = new Span("Descrizione: " + roleDescription);
                roleDescSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
                
                welcomeCard.add(roleTitle, roleNameSpan, roleDescSpan);
            } else {
                Span noRoleSpan = new Span("Nessun ruolo attivo selezionato");
                noRoleSpan.getStyle()
                    .set("color", "var(--lumo-error-color)")
                    .set("font-style", "italic");
                welcomeCard.add(roleTitle, noRoleSpan);
            }
            
            add(title, welcomeCard);
                
        } else {
            // User non autenticato
            add(new H1("Per favore, effettua il login"));
        }
    }
    
    private Div createCard() {
        Div card = new Div();
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-xl)")
            .set("margin", "var(--lumo-space-m)")
            .set("max-width", "800px")
            .set("width", "100%")
            .set("border", "2px solid var(--lumo-primary-color-10pct)");
        return card;
    }
}
