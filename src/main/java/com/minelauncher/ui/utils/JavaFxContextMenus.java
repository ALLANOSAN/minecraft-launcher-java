package com.minelauncher.ui.utils;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

/**
 * Builders/helpers para ContextMenu e validação de TextField em JavaFX.
 * Antes viviam inline em MainController.setupModListContextMenu + setupRamField.
 */
public final class JavaFxContextMenus {

    private JavaFxContextMenus() {}

    /**
     * Configura um TextField de input numérico com clamp em [min,max]
     * e valor default {@code def} ao perder foco vazio.
     * Replicado para min/max RAM.
     */
    public static void setupRamField(TextField field, int min, int max, int def) {
        field.setText(String.valueOf(def));
        field.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                field.setText(val.replaceAll("\\D", ""));
            }
            if (!field.getText().isEmpty()) {
                try {
                    int v = Integer.parseInt(field.getText());
                    if (v > max) field.setText(String.valueOf(max));
                } catch (NumberFormatException e) {
                    field.setText("");
                }
            }
        });
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                if (field.getText().isEmpty()) {
                    field.setText(String.valueOf(def));
                } else {
                    try {
                        int v = Integer.parseInt(field.getText());
                        if (v < min) field.setText(String.valueOf(min));
                    } catch (NumberFormatException e) {
                        field.setText(String.valueOf(def));
                    }
                }
            }
        });
    }

    /**
     * Faz parse seguro de TextField para int, retornando {@code def} se vazio/inválido.
     */
    public static int parseRam(TextField field, int def) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Helper para criar um MenuItem com handler.
     */
    public static MenuItem item(String label, Runnable onAction) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> onAction.run());
        return item;
    }

    /**
     * Helper para adicionar vários items a um ContextMenu.
     */
    public static ContextMenu menu(MenuItem... items) {
        ContextMenu m = new ContextMenu();
        m.getItems().addAll(items);
        return m;
    }
}
