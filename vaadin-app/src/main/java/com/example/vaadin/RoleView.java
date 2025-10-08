package com.example.vaadin;

import com.example.common.dto.RoleDTO;
import com.example.common.dto.RoleTableDTO;
import com.example.common.dto.UserRolesDTO;
import com.example.vaadin.service.ActiveRoleService;
import com.example.vaadin.service.RolesService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Route("")
@AnonymousAllowed
public class RoleView extends VerticalLayout implements BeforeEnterObserver {

    private final RolesService rolesService;
    private final AuthenticationContext authenticationContext;
    private final ActiveRoleService activeRoleService;

    public RoleView(RolesService rolesService, AuthenticationContext authenticationContext, ActiveRoleService activeRoleService) {
        this.rolesService = rolesService;
        this.authenticationContext = authenticationContext;
        this.activeRoleService = activeRoleService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Se l'utente è autenticato, controlla se ha un solo ruolo
        if (auth != null && auth.isAuthenticated() && 
            auth instanceof OAuth2AuthenticationToken oauthToken) {
            
            try {
                UserRolesDTO response = rolesService.getUserRolesAndFunctionalities();
                
                if (response != null && response.getRoles() != null && response.getRoles().size() == 1) {
                    // L'utente ha un solo ruolo - imposta automaticamente e naviga a MainView
                    RoleDTO singleRole = response.getRoles().get(0);
                    activeRoleService.setActiveRole(singleRole.getName(), singleRole.getDescription());
                    
                    // Naviga direttamente alla pagina di conferma
                    String parameter = singleRole.getName() + "|" + singleRole.getDescription();
                    event.forwardTo("role-confirmation/" + parameter);
                    return;
                }
            } catch (Exception e) {
                // In caso di errore, continua normalmente e mostra la pagina
                e.printStackTrace();
            }
        }
        
        // Renderizza la pagina normalmente
        initializeView();
    }
    
    private void initializeView() {
        setSizeFull();
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        
        // Header
        H1 title = new H1("🔐 Vaadin + Keycloak Demo");
        title.getStyle().set("color", "var(--lumo-primary-color)");
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && 
            auth instanceof OAuth2AuthenticationToken oauthToken) {
            
            // User is authenticated
            createAuthenticatedView(oauthToken);
            
        } else {
            // User is not authenticated
            createLoginView();
        }
        
        add(title);
    }
    
    private void createAuthenticatedView(OAuth2AuthenticationToken oauthToken) {
        OAuth2User user = oauthToken.getPrincipal();
        
        // User info
        String username = user.getAttribute("preferred_username");
        String firstName = user.getAttribute("given_name");
        String lastName = user.getAttribute("family_name");
        String email = user.getAttribute("email");
        
        if (username == null) username = user.getName();
        
        Div welcomeCard = createCard();
        H3 welcomeTitle = new H3("👋 Benvenuto!");
        Span userInfo = new Span("Utente: " + (firstName != null ? firstName + " " + lastName : username));
        if (email != null) {
            Span emailSpan = new Span("Email: " + email);
            emailSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
            welcomeCard.add(welcomeTitle, userInfo, emailSpan);
        } else {
            welcomeCard.add(welcomeTitle, userInfo);
        }
        
        // Active Role info
        if (activeRoleService.hasActiveRole()) {
            Div separator = new Div();
            separator.getStyle()
                .set("height", "1px")
                .set("background", "var(--lumo-contrast-20pct)")
                .set("margin", "var(--lumo-space-m) 0");
            
            Span activeRoleLabel = new Span("🎭 Ruolo Attivo:");
            activeRoleLabel.getStyle().set("font-weight", "500");
            
            Span activeRoleValue = new Span(activeRoleService.getActiveRole());
            activeRoleValue.getStyle()
                .set("color", "var(--lumo-primary-color)")
                .set("font-weight", "bold");
            
            HorizontalLayout activeRoleLayout = new HorizontalLayout(activeRoleLabel, activeRoleValue);
            activeRoleLayout.setSpacing(false);
            activeRoleLayout.getStyle().set("gap", "var(--lumo-space-xs)");
            
            welcomeCard.add(separator, activeRoleLayout);
        }
        
        // Roles and Functionalities section
        Div rolesCard = createLargeCard();  // Card più grande per la tabella
        H3 rolesTitle = new H3("🎭 Ruoli e Funzionalità");
        
        // Create grid for roles with functionalities
        Grid<RoleTableDTO> grid = new Grid<>(RoleTableDTO.class, false);
        grid.setHeight("500px");  // Aumentata l'altezza
        grid.setWidthFull();
        
        // Add columns
        grid.addColumn(RoleTableDTO::getRoleName)
            .setHeader("Ruolo")
            .setWidth("200px")
            .setFlexGrow(0);
            
        grid.addColumn(RoleTableDTO::getRoleDescription)
            .setHeader("Descrizione")
            .setWidth("300px")
            .setFlexGrow(1);
        
        // Column per le funzionalità con renderer personalizzato per mostrare lista verticale
        grid.addColumn(new ComponentRenderer<>(roleTableDTO -> {
            VerticalLayout functionalitiesLayout = new VerticalLayout();
            functionalitiesLayout.setPadding(false);
            functionalitiesLayout.setSpacing(false);
            functionalitiesLayout.getStyle().set("gap", "2px");
            
            if (roleTableDTO.getFunctionalities() != null && !roleTableDTO.getFunctionalities().isEmpty()) {
                String[] functionalities = roleTableDTO.getFunctionalities().split(", ");
                for (String functionality : functionalities) {
                    Span functionalitySpan = new Span("• " + functionality);
                    functionalitySpan.getStyle()
                        .set("font-size", "0.875rem")
                        .set("white-space", "nowrap")
                        .set("overflow", "visible");
                    functionalitiesLayout.add(functionalitySpan);
                }
            } else {
                Span emptySpan = new Span("Nessuna funzionalità");
                emptySpan.getStyle()
                    .set("font-style", "italic")
                    .set("color", "var(--lumo-secondary-text-color)");
                functionalitiesLayout.add(emptySpan);
            }
            
            return functionalitiesLayout;
        }))
        .setHeader("Funzionalità")
        .setFlexGrow(2);
        
        // Column per il pulsante di selezione ruolo
        grid.addColumn(new ComponentRenderer<>(roleTableDTO -> {
            Button selectRoleBtn = new Button("Seleziona Ruolo", VaadinIcon.CHECK.create());
            selectRoleBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            selectRoleBtn.addClickListener(e -> {
                // Naviga alla pagina di conferma passando il ruolo e la descrizione
                String parameter = roleTableDTO.getRoleName() + "|" + roleTableDTO.getRoleDescription();
                getUI().ifPresent(ui -> ui.navigate("role-confirmation/" + parameter));
            });
            return selectRoleBtn;
        }))
        .setHeader("Azioni")
        .setWidth("150px")
        .setFlexGrow(0);
        
        // Add message when no data
        Span noDataMessage = new Span("👆 Clicca 'Carica Ruoli' per visualizzare i tuoi ruoli e funzionalità");
        noDataMessage.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("font-style", "italic")
            .set("text-align", "center")
            .set("padding", "var(--lumo-space-l)");
        
        Button loadRolesBtn = new Button("Ricarica Ruoli", VaadinIcon.REFRESH.create());
        loadRolesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loadRolesBtn.addClickListener(e -> loadUserRolesTable(grid, noDataMessage));
        
        Button logoutBtn = new Button("Logout", VaadinIcon.SIGN_OUT.create());
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        logoutBtn.addClickListener(e -> authenticationContext.logout());
        
        HorizontalLayout buttonLayout = new HorizontalLayout(loadRolesBtn, logoutBtn);
        rolesCard.add(rolesTitle, noDataMessage, grid, buttonLayout);
        
        add(welcomeCard, rolesCard);
        
        // Carica automaticamente i ruoli all'avvio
        loadUserRolesTable(grid, noDataMessage);
    }
    
    private void createLoginView() {
        Div loginCard = createCard();
        H3 loginTitle = new H3("🔑 Accesso Richiesto");
        Span loginText = new Span("Per utilizzare questa applicazione devi effettuare il login con Keycloak.");
        
        Button loginBtn = new Button("Login con Keycloak", VaadinIcon.LOCK.create());
        loginBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        loginBtn.addClickListener(e -> 
            getUI().ifPresent(ui -> ui.getPage().setLocation("/oauth2/authorization/keycloak"))
        );
        
        loginCard.add(loginTitle, loginText, loginBtn);
        add(loginCard);
    }
    
    private Div createCard() {
        Div card = new Div();
        card.addClassName("card");
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-l)")
            .set("margin", "var(--lumo-space-m)")
            .set("max-width", "600px")
            .set("width", "100%");
        return card;
    }
    
    private Div createLargeCard() {
        Div card = new Div();
        card.addClassName("large-card");
        card.getStyle()
            .set("background", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("box-shadow", "var(--lumo-box-shadow-s)")
            .set("padding", "var(--lumo-space-l)")
            .set("margin", "var(--lumo-space-m)")
            .set("max-width", "1200px")  // Card più grande
            .set("width", "100%");
        return card;
    }
    
    private void loadUserRolesTable(Grid<RoleTableDTO> grid, Span noDataMessage) {
        try {
            UserRolesDTO response = rolesService.getUserRolesAndFunctionalities();
            
            if (response == null) {
                Notification.show("❌ Errore nel caricamento dei ruoli", 3000, 
                    Notification.Position.TOP_CENTER);
                return;
            }
            
            List<RoleTableDTO> tableData = new ArrayList<>();
            
            // Transform response to table data - una riga per ruolo
            for (RoleDTO role : response.getRoles()) {
                String functionalities = "";
                
                if (role.getFunctionalities() != null && !role.getFunctionalities().isEmpty()) {
                    // Crea una stringa con tutte le funzionalità del ruolo - formato migliorato
                    functionalities = role.getFunctionalities().stream()
                        .map(func -> func.getName() + " [" + func.getCategory() + "]")
                        .collect(Collectors.joining(", "));
                } else {
                    functionalities = "Nessuna funzionalità assegnata";
                }
                
                tableData.add(new RoleTableDTO(
                    role.getName(),
                    role.getDescription(),
                    functionalities
                ));
            }
            
            if (tableData.isEmpty()) {
                noDataMessage.setText("ℹ️ Nessun ruolo assegnato all'utente");
                noDataMessage.setVisible(true);
                grid.setItems(tableData);
            } else {
                noDataMessage.setVisible(false);
                grid.setItems(tableData);
                Notification.show("✅ Ruoli e funzionalità caricati con successo! (" + tableData.size() + " ruoli)", 
                    3000, Notification.Position.TOP_CENTER);
            }
            
        } catch (Exception e) {
            Notification.show("❌ Errore imprevisto: " + e.getMessage(), 5000, 
                Notification.Position.TOP_CENTER);
            e.printStackTrace();
        }
    }
}
