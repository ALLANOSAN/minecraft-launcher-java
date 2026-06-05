package com.minelauncher.ui.services;

import com.minelauncher.auth.MicrosoftAuth;
import com.minelauncher.auth.OfflineAuth;
import com.minelauncher.models.GameProfile;
import com.minelauncher.settings.SettingsManager;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * Service para encapsular toda a lógica de autenticação (Microsoft/Offline).
 * Extraído do MainController para melhorar a coesão.
 */
public class AuthService {

    private final MicrosoftAuth microsoftAuth;
    private Consumer<MicrosoftAuth.DeviceCodeResponse> onDeviceCodeCallback;
    private Runnable onLoginSuccess;
    private Consumer<String> onLoginError;

    @com.google.inject.Inject
    public AuthService() {
        this.microsoftAuth = new MicrosoftAuth();
    }

    public void setUI(Consumer<MicrosoftAuth.DeviceCodeResponse> onDeviceCodeCallback,
                      Runnable onLoginSuccess,
                      Consumer<String> onLoginError) {
        this.onDeviceCodeCallback = onDeviceCodeCallback;
        this.onLoginSuccess = onLoginSuccess;
        this.onLoginError = onLoginError;
    }

    public void loginMicrosoft() {
        microsoftAuth.login(dcr -> {
            Platform.runLater(() -> onDeviceCodeCallback.accept(dcr));
        }).thenAccept(profile -> {
            Platform.runLater(() -> {
                SettingsManager.getInstance().addAccount(profile);
                onLoginSuccess.run();
            });
        }).exceptionally(ex -> {
            com.minelauncher.ui.services.ErrorReporter.report(ex, "AuthService: loginMicrosoft");
            Platform.runLater(() -> onLoginError.accept(ex.getMessage()));
            return null;
        });
    }

    public void loginOffline(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) return;
        
        GameProfile profile = OfflineAuth.createOfflineProfile(playerName.trim());
        SettingsManager.getInstance().addAccount(profile);
        onLoginSuccess.run();
    }
}
