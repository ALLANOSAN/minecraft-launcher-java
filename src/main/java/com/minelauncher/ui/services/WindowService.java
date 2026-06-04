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

    private final Stage stage;
    private final GameLauncher gameLauncher;
    private final Runnable stopLiveUpdates;

    public WindowService(Stage stage, GameLauncher gameLauncher, Runnable stopLiveUpdates) {
        this.stage = stage;
        this.gameLauncher = gameLauncher;
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
            root.getStyleClass().add("maximized");
            container.getStyleClass().add("maximized");
        } else {
            root.getStyleClass().remove("maximized");
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
