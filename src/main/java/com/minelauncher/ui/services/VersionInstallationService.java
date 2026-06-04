package com.minelauncher.ui.services;

import com.minelauncher.launcher.VersionManager;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import java.util.function.Consumer;

/**
 * Service para orquestrar a instalação de versões na UI.
 */
public class VersionInstallationService {

    private final VersionManager versionManager;
    private final Label statusLabel;
    private final ProgressBar progressBar;

    public VersionInstallationService(VersionManager versionManager, Label statusLabel, ProgressBar progressBar) {
        this.versionManager = versionManager;
        this.statusLabel = statusLabel;
        this.progressBar = progressBar;
    }

    public void installVersion(String versionId, Runnable onComplete, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    statusLabel.setText("Instalando " + versionId + "...");
                    progressBar.setProgress(-1);
                });

                versionManager.downloadVersion(versionId, (msg, pct) -> {
                    Platform.runLater(() -> {
                        statusLabel.setText(msg);
                        progressBar.setProgress(pct);
                    });
                });

                Platform.runLater(() -> {
                    statusLabel.setText("Versão " + versionId + " instalada!");
                    progressBar.setProgress(1.0);
                    onComplete.run();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erro: " + e.getMessage());
                    progressBar.setProgress(0);
                    onError.accept(e);
                });
            }
        }).start();
    }
}
