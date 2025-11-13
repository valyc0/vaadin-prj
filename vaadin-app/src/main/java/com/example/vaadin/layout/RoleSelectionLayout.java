package com.example.vaadin.layout;

/**
 * Layout semplificato per la selezione del ruolo.
 * Mostra solo l'header con logo, nome utente e logout.
 * Non mostra il menu laterale né il ruolo attivo.
 */
public class RoleSelectionLayout extends BaseLayout {

    public RoleSelectionLayout() {
        createHeader("🎯 Vaadin App - Selezione Ruolo", false, null);
        // Non creiamo il drawer (menu laterale)
    }
}
