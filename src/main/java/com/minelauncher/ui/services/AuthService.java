package com.minelauncher.ui.services;

import com.minelauncher.auth.MicrosoftAuth;
import com.minelauncher.auth.OfflineAuth;
import com.minelauncher.models.GameProfile;
import com.minelauncher.settings.SettingsManager;
import javafx.application.Platform;

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

    /**
     * CRIT-3 do code-review: renova o access token se a conta
     * {@link GameProfile#isTokenExpired()} estiver expirada.
     * Chamado por GameLaunchService antes de lançar o jogo para
     * evitar o ciclo "toda vez que abre, re-pede login Microsoft"
     * que existia antes (o access token Mojang expira em ~24h mas
     * o refresh token dura meses, então basta renovar).
     *
     * <p>Accounts offline ou sem refresh token são retornadas sem
     * alteração (não é relevante renovar).
     *
     * @param profile conta a ser validada/renovada
     * @return profile (renovado ou não)
     * @throws MicrosoftAuth.RefreshTokenExpiredException se o
     *         refresh token foi revogado/expirado — nesse caso o
     *         usuário precisa re-fazer login.
     */
    public GameProfile refreshIfNeeded(GameProfile profile) {
        if (profile == null) return null;
        if (!profile.isMicrosoft()) return profile; // offline não precisa
        if (!profile.isTokenExpired()) return profile;
        if (profile.getRefreshToken() == null || profile.getRefreshToken().isEmpty()) {
            throw new MicrosoftAuth.RefreshTokenExpiredException(
                    "Conta Microsoft sem refresh token salvo; re-login necessário");
        }
        try {
            return microsoftAuth.refreshTokens(profile);
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MicrosoftAuth.RefreshTokenExpiredException(
                    "Falha ao renovar token: " + e.getMessage(), e);
        }
    }
}
