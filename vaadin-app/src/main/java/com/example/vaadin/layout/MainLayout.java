package com.example.vaadin.layout;

import com.example.vaadin.service.ActiveRoleService;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class MainLayout extends BaseLayout {

    private final ActiveRoleService activeRoleService;

    public MainLayout(ActiveRoleService activeRoleService) {
        this.activeRoleService = activeRoleService;
        
        String activeRole = activeRoleService.getActiveRole();
        
        // Aggiungi DrawerToggle all'header
        DrawerToggle toggle = new DrawerToggle();
        addToNavbar(true, toggle);
        
        createHeader("🎯 Vaadin App", true, activeRole);
        createDrawer();
    }

    private void createDrawer() {
        VerticalLayout menu = new VerticalLayout();
        menu.setSizeFull();
        menu.setPadding(true);
        menu.setSpacing(false);
        menu.getStyle().set("background", "var(--lumo-contrast-5pct)");

        // Header del menu
        Div menuHeader = new Div();
        menuHeader.setText("📋 Menu");
        menuHeader.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("font-weight", "bold")
            .set("padding", "var(--lumo-space-m)")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        menu.add(menuHeader);

        // Menu items
        menu.add(
            createMenuLink("🏠 Home", "home", VaadinIcon.DASHBOARD),
            createMenuLink("🎭 Seleziona Ruolo", "", VaadinIcon.USER_CARD),
            createMenuLink("📦 Upload File", "chunked-upload", VaadinIcon.UPLOAD),
            createMenuLink("📄 File Upload Semplice", "simple-upload", VaadinIcon.FILE_ADD),
            createMenuLink("🌊 Streaming Upload", "true-streaming", VaadinIcon.CLOUD_UPLOAD)
        );

        // Spacer
        Div spacer = new Div();
        menu.add(spacer);
        menu.setFlexGrow(1, spacer);

        // Footer del menu
        Div menuFooter = new Div();
        menuFooter.setText("v1.0.0");
        menuFooter.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("text-align", "center")
            .set("border-top", "1px solid var(--lumo-contrast-10pct)");

        menu.add(menuFooter);

        addToDrawer(menu);
    }

    private Anchor createMenuLink(String text, String route, VaadinIcon icon) {
        Anchor link = new Anchor();
        link.setText(text);
        
        if (!route.isEmpty()) {
            link.setHref(route);
        } else {
            link.setHref("");
        }
        
        link.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "var(--lumo-space-m)")
            .set("text-decoration", "none")
            .set("color", "var(--lumo-body-text-color)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin", "var(--lumo-space-xs)")
            .set("transition", "background 0.2s");

        // Hover effect
        link.getElement().addEventListener("mouseenter", e -> {
            link.getStyle().set("background", "var(--lumo-contrast-10pct)");
        });
        link.getElement().addEventListener("mouseleave", e -> {
            link.getStyle().set("background", "transparent");
        });

        return link;
    }
}
