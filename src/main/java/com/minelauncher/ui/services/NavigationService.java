package com.minelauncher.ui.services;

import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import java.util.Map;

/**
 * Service para gerenciar a navegação entre as abas da UI.
 */
public class NavigationService {

    private Map<String, Pane> panes;
    private Map<String, Button> navButtons;

    @com.google.inject.Inject
    public NavigationService() {}

    public void setDependencies(Map<String, Pane> panes, Map<String, Button> navButtons) {
        this.panes = panes;
        this.navButtons = navButtons;
    }

    public void showTab(String tab) {
        panes.forEach((name, pane) -> pane.setVisible(name.equals(tab)));
        navButtons.forEach((name, btn) -> setActiveNav(btn, name.equals(tab)));
    }

    /**
     * BUG-2: alias público de {@link #showTab(String)} para que o controller
     * delegue explicitamente a navegação ao serviço em vez de replicar a lógica.
     */
    public void navigate(String tab) {
        showTab(tab);
    }

    private void setActiveNav(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            if (!btn.getStyleClass().contains("active")) {
                btn.getStyleClass().add("active");
            }
        } else {
            btn.getStyleClass().remove("active");
        }
        // QUAL-16c: opacidade movida para CSS (.nav-btn/.nav-btn.active).
        // Antes: btn.setOpacity(1.0) / btn.setOpacity(0.78) aqui.
    }
}
