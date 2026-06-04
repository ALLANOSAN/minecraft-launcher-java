package com.minelauncher.ui.utils;

import javafx.scene.control.Label;

import java.util.List;

/**
 * Helpers JavaFX genéricos reutilizáveis em vários controllers.
 * Antes: {@code swapClass} vivia como método privado em MainController.
 */
public final class JavaFxUtils {

    private JavaFxUtils() {}

    /**
     * Remove todas as classes de estilo em {@code variants} do label,
     * depois adiciona {@code target} (se não estiver presente).
     * Útil para toggle de "status-chip-success / -danger / -warning".
     */
    public static void swapClass(Label l, List<String> variants, String target) {
        if (l == null) return;
        l.getStyleClass().removeAll(variants);
        if (target != null && !l.getStyleClass().contains(target)) {
            l.getStyleClass().add(target);
        }
    }
}
