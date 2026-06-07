package com.minelauncher.ui.services;

import com.minelauncher.auth.MicrosoftAuth;
import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.settings.SettingsManager;
import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Service para orquestrar o lançamento do Minecraft.
 * Extraído do MainController para reduzir acoplamento e complexidade.
 */
public class GameLaunchService {

    private final VersionManager versionManager;
    private final GameLauncher gameLauncher;
    private final BackupService backupService;
    private final AuthService authService;

    @com.google.inject.Inject
    public GameLaunchService(VersionManager versionManager, GameLauncher gameLauncher,
                             BackupService backupService, AuthService authService) {
        this.versionManager = versionManager;
        this.gameLauncher = gameLauncher;
        this.backupService = backupService;
        this.authService = authService;
    }

    /**
     * Exceções tipadas para diagnóstico preciso no caller (UI pode
     * mostrar mensagens específicas em vez de "Erro genérico").
     * <b>MEDIUM do code-review:</b> antes qualquer falha no fluxo
     * de launch virava {@code Exception} genérica via catch-all,
     * indistinguível de "Java não instalado" vs "conta bloqueada"
     * vs "JSON malformado". UI mostrava "Erro: null" para vários
     * casos.
     */
    public static class VersionNotInstalledException extends RuntimeException {
        public VersionNotInstalledException(String message) { super(message); }
    }
    public static class ModLoaderInstallException extends RuntimeException {
        public ModLoaderInstallException(String message, Throwable cause) { super(message, cause); }
    }
    public static class AuthenticationExpiredException extends RuntimeException {
        public AuthenticationExpiredException(String message) { super(message); }
    }
    public static class JavaNotFoundException extends RuntimeException {
        public JavaNotFoundException(String message) { super(message); }
    }

    public void launch(LaunchProfile profile, GameProfile account,
                       Consumer<String> statusUpdater, BiConsumer<String, Double> progressUpdater,
                       Consumer<String> logUpdater, Runnable onStarted, Runnable onFinished,
                       Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                // 0. Backup automático (se configurado)
                String backupPath = SettingsManager.getInstance().getBackupPath();
                if (backupPath != null && !backupPath.isBlank()) {
                    // BUG-5: worldDir agora aponta para gameDir/saves/ em vez de
                    // gameDir inteiro. Evita copiar mods/versions/assets junto.
                    File gameDir = new File(profile.getGameDir() != null
                            ? profile.getGameDir()
                            : SettingsManager.getInstance().getBaseDir().getAbsolutePath());
                    File worldDir = new File(gameDir, "saves");
                    File backupDir = new File(backupPath);
                    statusUpdater.accept("Realizando backup do mundo...");
                    // BUG-6: usa a sobrecarga (File, File) que preserva o
                    // comportamento de copiar o diretório passado (saves/) inteiro.
                    backupService.createSnapshot(worldDir, backupDir);
                }

                // 0.5. CRIT-3: renovar access token se expirado.
                // Variável final-effectively: não posso reatribuir `account`
                // se for referenciada em lambda posterior.
                final GameProfile finalAccount;
                try {
                    finalAccount = authService.refreshIfNeeded(account);
                } catch (MicrosoftAuth.RefreshTokenExpiredException ex) {
                    throw new AuthenticationExpiredException(
                            "Sessão Microsoft expirou. Faça login novamente. Detalhe: " + ex.getMessage());
                }

                // 1. Baixar versão vanilla se necessário
                if (!versionManager.getInstalledVersions().contains(profile.getGameVersion())) {
                    statusUpdater.accept("Baixando Minecraft " + profile.getGameVersion() + "...");
                    try {
                        versionManager.downloadVersion(profile.getGameVersion(), progressUpdater);
                    } catch (IOException e) {
                        throw new VersionNotInstalledException(
                                "Não foi possível baixar Minecraft " + profile.getGameVersion() + ": " + e.getMessage());
                    }
                }

                // 2. Instalar mod loader se necessário
                String loader = profile.getModLoader();
                String loaderVersion = profile.getModLoaderVersion();
                if (loader != null && !"vanilla".equals(loader) && loaderVersion != null && !loaderVersion.isEmpty()) {
                    String versionId = gameLauncher.resolveVersionId(profile);
                    if (!versionManager.getInstalledVersions().contains(versionId)) {
                        statusUpdater.accept("Instalando " + loader + " " + loaderVersion + "...");
                        try {
                            switch (loader) {
                                case "forge" -> versionManager.installForge(profile.getGameVersion(), loaderVersion, (m, p) -> statusUpdater.accept("Forge: " + m));
                                case "fabric" -> versionManager.installFabric(profile.getGameVersion(), (m, p) -> statusUpdater.accept("Fabric: " + m));
                                case "neoforge" -> versionManager.installNeoForge(profile.getGameVersion(), loaderVersion, (m, p) -> statusUpdater.accept("NeoForge: " + m));
                                case "quilt" -> versionManager.installQuilt(profile.getGameVersion(), (m, p) -> statusUpdater.accept("Quilt: " + m));
                            }
                        } catch (Exception e) {
                            throw new ModLoaderInstallException(
                                    "Falha ao instalar " + loader + " " + loaderVersion, e);
                        }
                    }
                }

                gameLauncher.launch(profile, finalAccount, logUpdater);
                onStarted.run();

                // Aguardar fim do jogo
                Thread watcher = new Thread(() -> {
                    try {
                        while (gameLauncher.isRunning()) {
                            Thread.sleep(1000);
                        }
                        onFinished.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "game-watcher");
                watcher.setDaemon(true);
                watcher.start();

            } catch (Exception e) {
                com.minelauncher.ui.services.ErrorReporter.report(e, "GameLaunchService: launch");
                onError.accept(e);
            }
        }).start();
    }
}
