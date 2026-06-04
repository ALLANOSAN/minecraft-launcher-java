package com.minelauncher.ui.services;

import com.minelauncher.ui.utils.JavaFxUtils;
import javafx.application.Platform;
import javafx.scene.control.Label;
import java.util.Map;

/**
 * Service para gerenciar a máquina de estados do Launcher na UI.
 */
public class LauncherStateService {

    private final Label sessionStatusLabel;
    private static final java.util.List<String> SESSION_VARIANTS = java.util.List.of(
        "session-chip-warm", "session-chip-cool", "session-chip-danger", "session-chip-accent"
    );

    public enum LauncherState { BUSY, PLAYING, ERROR, READY }

    public LauncherStateService(Label sessionStatusLabel) {
        this.sessionStatusLabel = sessionStatusLabel;
    }

    public void setState(LauncherState newState) {
        Platform.runLater(() -> {
            if (sessionStatusLabel == null) return;

            String text;
            String styleClass;
            switch (newState) {
                case BUSY -> { text = "● BUSY"; styleClass = "session-chip-warm"; }
                case PLAYING -> { text = "● PLAYING"; styleClass = "session-chip-cool"; }
                case ERROR -> { text = "● ERROR"; styleClass = "session-chip-danger"; }
                default -> { text = "● READY"; styleClass = "session-chip-accent"; }
            }
            sessionStatusLabel.setText(text);
            JavaFxUtils.swapClass(sessionStatusLabel, SESSION_VARIANTS, styleClass);
        });
    }
}
