package com.minelauncher.ui.services;

import com.minelauncher.launcher.GameLauncher;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;

/**
 * Service para gerenciar operações de janela.
 */
public class WindowService {

    private Stage stage;
    private final GameLauncher gameLauncher;
    private Runnable stopLiveUpdates;

    @com.google.inject.Inject
    public WindowService(GameLauncher gameLauncher) {
        this.gameLauncher = gameLauncher;
    }

    public void setUI(Stage stage, Runnable stopLiveUpdates) {
        this.stage = stage;
        this.stopLiveUpdates = stopLiveUpdates;
    }

    public void minimize() {
        stage.setIconified(true);
    }

    public void maximize(Pane headerBar) {
        boolean isMaximized = stage.isMaximized();
        stage.setMaximized(!isMaximized);

        StackPane root = (StackPane) headerBar.getScene().getRoot();
        BorderPane container = (BorderPane) root.getChildren().get(0);

        if (!isMaximized) {
            container.getStyleClass().add("maximized");
        } else {
            container.getStyleClass().remove("maximized");
        }
    }

    public void close() {
        if (gameLauncher.isRunning()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Jogo em execução");
            alert.setHeaderText("O Minecraft está rodando");
            alert.setContentText("Deseja realmente fechar o launcher?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
                return;
            gameLauncher.kill();
        }
        stopLiveUpdates.run();
        Platform.exit();
        System.exit(0);
    }
}
