package com.minelauncher.ui.services;

import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.settings.SettingsManager;
import java.io.File;
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

    @com.google.inject.Inject
    public GameLaunchService(VersionManager versionManager, GameLauncher gameLauncher, BackupService backupService) {
        this.versionManager = versionManager;
        this.gameLauncher = gameLauncher;
        this.backupService = backupService;
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
                    File worldDir = new File(profile.getGameDir() != null ? profile.getGameDir() : SettingsManager.getInstance().getBaseDir().getAbsolutePath());
                    File backupDir = new File(backupPath);
                    statusUpdater.accept("Realizando backup do mundo...");
                    backupService.createSnapshot(worldDir, backupDir);
                }

                // 1. Baixar versão vanilla se necessário
                if (!versionManager.getInstalledVersions().contains(profile.getGameVersion())) {
                    statusUpdater.accept("Baixando Minecraft " + profile.getGameVersion() + "...");
                    versionManager.downloadVersion(profile.getGameVersion(), progressUpdater);
                }

                // 2. Instalar mod loader se necessário
                String loader = profile.getModLoader();
                String loaderVersion = profile.getModLoaderVersion();
                if (loader != null && !"vanilla".equals(loader) && loaderVersion != null && !loaderVersion.isEmpty()) {
                    String versionId = gameLauncher.resolveVersionId(profile);
                    if (!versionManager.getInstalledVersions().contains(versionId)) {
                        statusUpdater.accept("Instalando " + loader + " " + loaderVersion + "...");
                        switch (loader) {
                            case "forge" -> versionManager.installForge(profile.getGameVersion(), loaderVersion, (m, p) -> statusUpdater.accept("Forge: " + m));
                            case "fabric" -> versionManager.installFabric(profile.getGameVersion(), (m, p) -> statusUpdater.accept("Fabric: " + m));
                            case "neoforge" -> versionManager.installNeoForge(profile.getGameVersion(), loaderVersion, (m, p) -> statusUpdater.accept("NeoForge: " + m));
                            case "quilt" -> versionManager.installQuilt(profile.getGameVersion(), (m, p) -> statusUpdater.accept("Quilt: " + m));
                        }
                    }
                }

                gameLauncher.launch(profile, account, logUpdater);
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
