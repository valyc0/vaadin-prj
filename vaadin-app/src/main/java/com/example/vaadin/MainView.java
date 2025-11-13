package com.example.vaadin;

import com.example.vaadin.layout.MainLayout;
import com.example.vaadin.service.ActiveRoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Route(value = "role-confirmation", layout = MainLayout.class)
@AnonymousAllowed
public class MainView extends VerticalLayout implements HasUrlParameter<String> {

    private final AuthenticationContext authenticationContext;
    private final ActiveRoleService activeRoleService;
    private String selectedRole;
    private String roleDescription;

    public MainView(AuthenticationContext authenticationContext, ActiveRoleService activeRoleService) {
        this.authenticationContext = authenticationContext;
        this.activeRoleService = activeRoleService;
        
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        if (parameter != null) {
            // Il parametro contiene "roleName|description"
            String[] parts = parameter.split("\\|", 2);
            this.selectedRole = parts[0];
            this.roleDescription = parts.length > 1 ? parts[1] : "Nessuna descrizione disponibile";
            
            // Imposta il ruolo come attivo nella sessione
            activeRoleService.setActiveRole(this.selectedRole, this.roleDescription);
        } else {
            this.selectedRole = "Nessun ruolo selezionato";
            this.roleDescription = "";
        }
        
        createView();
    }
    
    private void createView() {
        removeAll(); // Rimuovi componenti esistenti
        
        // Header della pagina
        H1 title = new H1("✅ Ruolo Selezionato");
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
            Div confirmationCard = createCard();
            
            // Informazioni utente
            H3 userTitle = new H3("👤 Utente");
            Span userInfo = new Span("Nome: " + (firstName != null ? firstName + " " + lastName : username));
            if (email != null) {
                Span emailSpan = new Span("Email: " + email);
                emailSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
                confirmationCard.add(userTitle, userInfo, emailSpan);
            } else {
                confirmationCard.add(userTitle, userInfo);
            }
            
            // Separator
            Div separator = new Div();
            separator.getStyle()
                .set("height", "1px")
                .set("background", "var(--lumo-contrast-20pct)")
                .set("margin", "var(--lumo-space-m) 0");
            confirmationCard.add(separator);
            
            // Informazioni ruolo
            H3 roleTitle = new H3("🎭 Ruolo Selezionato");
            Span roleNameSpan = new Span("Ruolo: " + selectedRole);
            roleNameSpan.getStyle()
                .set("font-weight", "bold")
                .set("font-size", "1.2rem")
                .set("color", "var(--lumo-primary-color)");
            
            Span roleDescSpan = new Span("Descrizione: " + roleDescription);
            roleDescSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
            
            confirmationCard.add(roleTitle, roleNameSpan, roleDescSpan);
            
            // Separator
            Div separator2 = new Div();
            separator2.getStyle()
                .set("height", "1px")
                .set("background", "var(--lumo-contrast-20pct)")
                .set("margin", "var(--lumo-space-m) 0");
            confirmationCard.add(separator2);
            
            // Messaggio di conferma
            Span confirmMessage = new Span("🎉 Il ruolo '" + selectedRole + "' è stato impostato come ruolo attivo per la tua sessione!");
            confirmMessage.getStyle()
                .set("font-weight", "500")
                .set("color", "var(--lumo-success-color)")
                .set("text-align", "center")
                .set("background", "var(--lumo-success-color-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)");
            
            // Bottoni di azione
            Button backToRolesBtn = new Button("← Torna ai Ruoli", VaadinIcon.ARROW_LEFT.create());
            backToRolesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            backToRolesBtn.addClickListener(e -> 
                getUI().ifPresent(ui -> ui.navigate(""))
            );
            
            Button uploadBtn = new Button("📦 Upload File", VaadinIcon.UPLOAD.create());
            uploadBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            uploadBtn.addClickListener(e -> 
                getUI().ifPresent(ui -> ui.navigate("chunked-upload"))
            );
            
            HorizontalLayout buttonLayout = new HorizontalLayout(backToRolesBtn, uploadBtn);
            buttonLayout.setJustifyContentMode(JustifyContentMode.CENTER);
            buttonLayout.setSpacing(true);
            
            confirmationCard.add(confirmMessage, buttonLayout);
            
            add(title, confirmationCard);
            
            // Mostra notifica di successo
            Notification.show("✅ Ruolo '" + selectedRole + "' selezionato con successo!", 
                3000, Notification.Position.TOP_CENTER);
                
        } else {
            // User non autenticato - redirect a login
            getUI().ifPresent(ui -> ui.navigate(""));
        }
    }
    
    private Div createCard() {
        Div card = new Div();
        card.addClassName("confirmation-card");
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