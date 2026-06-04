package com.minelauncher.ui.services;

import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.settings.SettingsManager;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Service para orquestrar o lançamento do Minecraft.
 * Extraído do MainController para reduzir acoplamento e complexidade.
 */
public class GameLaunchService {

    private final VersionManager versionManager;
    private final GameLauncher gameLauncher;

    public GameLaunchService(VersionManager versionManager, GameLauncher gameLauncher) {
        this.versionManager = versionManager;
        this.gameLauncher = gameLauncher;
    }

    public void launch(LaunchProfile profile, GameProfile account,
                       Consumer<String> statusUpdater, BiConsumer<String, Double> progressUpdater,
                       Consumer<String> logUpdater, Runnable onStarted, Runnable onFinished,
                       Consumer<Exception> onError) {
        new Thread(() -> {
            try {
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
                onError.accept(e);
            }
        }).start();
    }
}
