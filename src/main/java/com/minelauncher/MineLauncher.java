package com.minelauncher;

import com.minelauncher.settings.SettingsManager;
import com.minelauncher.ui.controllers.MainController;
import com.minelauncher.utils.ShortcutManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MineLauncher extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(MineLauncher.class);
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        SettingsManager.getInstance().load();
        ShortcutManager.createShortcut();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();
            controller.setStage(stage);

            // Aumentamos levemente o tamanho do Stage para acomodar a sombra e o buffer de segurança (evita corte no Linux)
            Scene scene = new Scene(root, 1210, 750);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setTitle("MineLauncher v1.0.0");
            stage.setScene(scene);
            stage.setMinWidth(920);
            stage.setMinHeight(680);
            stage.setWidth(1210);
            stage.setHeight(750);

            try {
                stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo.png")));
            } catch (Exception e) {
                LOG.warn("Logo não encontrado, usando ícone padrão");
            }

            // Centralizar na tela respeitando as visual bounds
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setX(screen.getMinX() + (screen.getWidth() - 1210) / 2);
            stage.setY(screen.getMinY() + (screen.getHeight() - 750) / 2);

            stage.show();

            // Debug — ver tamanho real da janela
            System.out.println("Stage width: " + stage.getWidth());
            System.out.println("Stage height: " + stage.getHeight());
            System.out.println("Stage X: " + stage.getX());
            System.out.println("Stage Y: " + stage.getY());
            System.out.println("Screen width: " + javafx.stage.Screen.getPrimary().getVisualBounds().getWidth());
            System.out.println("Screen height: " + javafx.stage.Screen.getPrimary().getVisualBounds().getHeight());

            LOG.info("MineLauncher iniciado com sucesso");
        } catch (IOException e) {
            LOG.error("Erro ao carregar interface", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        SettingsManager.getInstance().save();
        LOG.info("MineLauncher encerrado");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        // Forçar fuso horário do Brasil antes de qualquer coisa
        // (JVMs em containers/servidores frequentemente default para UTC)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"));
        // As propriedades abaixo podem causar problemas de escala e corte no header em algumas distros Linux
        // System.setProperty("prism.allowhidpi", "false"); 
        // System.setProperty("glass.gtk.uiScale", "1.0"); 
        launch(args);
    }
}
