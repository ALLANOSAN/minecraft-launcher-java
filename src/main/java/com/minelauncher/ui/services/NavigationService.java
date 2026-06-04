package com.minelauncher.ui.services;

import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import java.util.Map;

/**
 * Service para gerenciar a navegação entre as abas da UI.
 */
public class NavigationService {

    private final Map<String, Pane> panes;
    private final Map<String, Button> navButtons;

    public NavigationService(Map<String, Pane> panes, Map<String, Button> navButtons) {
        this.panes = panes;
        this.navButtons = navButtons;
    }

    public void showTab(String tab) {
        panes.forEach((name, pane) -> pane.setVisible(name.equals(tab)));
        navButtons.forEach((name, btn) -> setActiveNav(btn, name.equals(tab)));
    }

    private void setActiveNav(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            if (!btn.getStyleClass().contains("active")) {
                btn.getStyleClass().add("active");
            }
            btn.setOpacity(1.0);
        } else {
            btn.getStyleClass().remove("active");
            btn.setOpacity(0.78);
        }
    }
}
