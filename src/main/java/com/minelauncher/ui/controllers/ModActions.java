package com.minelauncher.ui.controllers;

import com.minelauncher.models.ModInfo;
import com.minelauncher.models.ModVersionInfo;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.mods.ModManager;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Ações relacionadas a mods e modpacks.
 */
public class ModActions {

    private static final Logger LOG = LoggerFactory.getLogger(ModActions.class);

    private final MainController controller;
    private final ModManager modManager;
    private final ProfileManager profileManager;

    public ModActions(MainController controller, ModManager modManager, ProfileManager profileManager) {
        this.controller = controller;
        this.modManager = modManager;
        this.profileManager = profileManager;
    }

    public void searchMods() {
        String query = controller.getModSearch().getText().trim();
        if (query.isEmpty()) {
            controller.getStatusLabel().setText("Digite um termo para buscar");
            return;
        }

        String source = controller.getSourceCombo().getValue();
        String type = controller.getTypeCombo().getValue();
        int classId = "Modpacks".equals(type) ? 4471 : 6;
        String projectType = "Modpacks".equals(type) ? "modpack" : "mod";

        controller.getStatusLabel().setText("Buscando...");
        controller.getProgressBar().setProgress(-1);
        controller.getModList().getItems().clear();
        controller.getLastSearchResults().clear();

        new Thread(() -> {
            try {
                List<ModInfo> results = new ArrayList<>();

                if ("Ambos".equals(source) || "Modrinth".equals(source)) {
                    results.addAll(modManager.searchModrinth(query, "", 20, projectType));
                }
                if ("Ambos".equals(source) || "CurseForge".equals(source)) {
                    results.addAll(modManager.searchCurseForge(query, 432, classId, "", 20));
                }

                List<String> displayNames = new ArrayList<>();
                for (ModInfo mod : results) {
                    String prefix = "modrinth".equals(mod.getSource()) ? "Modrinth" : "CurseForge";
                    String desc = mod.getDescription() != null ? mod.getDescription() : "";
                    if (desc.length() > 70) desc = desc.substring(0, 67) + "...";
                    displayNames.add("[" + prefix + "] " + mod.getName() + " -- " + desc);
                }

                Platform.runLater(() -> {
                    controller.getLastSearchResults().clear();
                    controller.getLastSearchResults().addAll(results);
                    controller.getModList().getItems().setAll(displayNames);
                    controller.getStatusLabel().setText(results.size() + " resultados encontrados");
                    controller.getProgressBar().setProgress(0);
                });

            } catch (Exception e) {
                LOG.error("Erro na busca", e);
                Platform.runLater(() -> {
                    controller.getStatusLabel().setText("Erro: " + e.getMessage());
                    controller.getProgressBar().setProgress(0);
                });
            }
        }).start();
    }

    public void installSelected() {
        String selected = controller.getModList().getSelectionModel().getSelectedItem();
        if (selected == null || selected.startsWith("Nenhum") || selected.startsWith("Buscando")) {
            controller.getStatusLabel().setText("Selecione um item para instalar");
            return;
        }

        int index = controller.getModList().getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= controller.getLastSearchResults().size()) {
            controller.getStatusLabel().setText("Item não encontrado nos resultados");
            return;
        }

        ModInfo mod = controller.getLastSearchResults().get(index);
        File baseDir = SettingsManager.getInstance().getBaseDir();
        boolean isModpack = "Modpacks".equals(controller.getTypeCombo().getValue());

        controller.getStatusLabel().setText("Buscando versões de " + mod.getName() + "...");
        controller.getProgressBar().setProgress(-1);

        new Thread(() -> {
            ModVersionInfo chosenVersion = showVersionDialog(mod, isModpack);
            if (chosenVersion == null) {
                Platform.runLater(() -> {
                    controller.getStatusLabel().setText("Instalação cancelada");
                    controller.getProgressBar().setProgress(0);
                });
                return;
            }

            mod.setDownloadUrl(chosenVersion.getDownloadUrl());
            mod.setFileName(chosenVersion.getFileName());
            mod.setVersion(chosenVersion.getVersionName());
            mod.setGameVersions(chosenVersion.getGameVersions());
            mod.setLoaders(chosenVersion.getLoaders());

            // Verificar se modpack já existe
            if (isModpack) {
                File existingDir = new File(baseDir, "modpacks/" + sanitizeName(mod.getName()));
                if (existingDir.exists() && existingDir.list() != null && existingDir.list().length > 0) {
                    CountDownLatch confirmLatch = new CountDownLatch(1);
                    AtomicBoolean confirmed = new AtomicBoolean(false);
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Modpack já existe");
                        alert.setHeaderText("Já existe um modpack instalado com esse nome:");
                        alert.setContentText(existingDir.getAbsolutePath() + "\n\nDeseja sobrescrever?");
                        confirmed.set(alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK);
                        confirmLatch.countDown();
                    });
                    try { confirmLatch.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (!confirmed.get()) {
                        Platform.runLater(() -> {
                            controller.getStatusLabel().setText("Instalação cancelada");
                            controller.getProgressBar().setProgress(0);
                        });
                        return;
                    }
                    com.minelauncher.utils.FileUtils.deleteDirectory(existingDir);
                }
            }

            Platform.runLater(() -> {
                controller.getStatusLabel().setText("Instalando " + mod.getName() + " " + mod.getVersion() + "...");
                controller.getProgressBar().setProgress(-1);
            });

            try {
                if (isModpack) {
                    modManager.installModpack(mod, baseDir, (msg, pct) -> {
                        Platform.runLater(() -> {
                            controller.getStatusLabel().setText(msg);
                            controller.getProgressBar().setProgress(pct);
                        });
                    });

                    String profileName = mod.getName();
                    String mcVersion = "1.20.1";
                    String modLoader = "vanilla";
                    String modLoaderVersion = "";
                    File modpackDir = new File(baseDir, "modpacks/" + sanitizeName(mod.getName()));

                    if (mod.getGameVersions() != null && !mod.getGameVersions().isEmpty()) {
                        mcVersion = mod.getGameVersions().get(0);
                    }
                    if (mod.getLoaders() != null && !mod.getLoaders().isEmpty()) {
                        String loader = mod.getLoaders().get(0).toLowerCase();
                        if (loader.contains("neoforge")) modLoader = "neoforge";
                        else if (loader.contains("forge")) modLoader = "forge";
                        else if (loader.contains("fabric")) modLoader = "fabric";
                        else if (loader.contains("quilt")) modLoader = "quilt";
                    }

                    File cfManifest = new File(modpackDir, "manifest.json");
                    if (cfManifest.exists()) {
                        String json = java.nio.file.Files.readString(cfManifest.toPath());
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                        if (obj.has("minecraft")) {
                            com.google.gson.JsonObject mc = obj.getAsJsonObject("minecraft");
                            if (mc.has("version")) mcVersion = mc.get("version").getAsString();
                            if (mc.has("modLoaders")) {
                                com.google.gson.JsonArray loaders = mc.getAsJsonArray("modLoaders");
                                if (loaders.size() > 0) {
                                    String loaderId = loaders.get(0).getAsJsonObject().get("id").getAsString();
                                    if (loaderId.contains("neoforge")) { modLoader = "neoforge"; modLoaderVersion = loaderId.replace("neoforge-", ""); }
                                    else if (loaderId.contains("forge")) { modLoader = "forge"; modLoaderVersion = loaderId.replace("forge-", ""); }
                                    else if (loaderId.contains("fabric")) { modLoader = "fabric"; }
                                    else if (loaderId.contains("quilt")) { modLoader = "quilt"; }
                                }
                            }
                        }
                    }

                    LaunchProfile newProfile = new LaunchProfile(profileName, mcVersion);
                    newProfile.setModLoader(modLoader);
                    newProfile.setModLoaderVersion(modLoaderVersion);
                    newProfile.setGameDir(modpackDir.getAbsolutePath());
                    profileManager.addProfile(newProfile);

                } else {
                    LaunchProfile profile = profileManager.getActiveProfile();
                    if (profile == null) throw new Exception("Nenhum perfil ativo");
                    File modsDir = new File(SettingsManager.getInstance().getBaseDir(),
                            profile.getGameDir() != null ? profile.getGameDir() + "/mods" : "mods");
                    modsDir.mkdirs();
                    modManager.installMod(modsDir, mod);
                }

                Platform.runLater(() -> {
                    controller.loadProfiles();
                    controller.getProgressBar().setProgress(1.0);
                    controller.getStatusLabel().setText("Instalado: " + mod.getName() + " " + mod.getVersion());
                });

            } catch (Exception e) {
                LOG.error("Erro na instalação", e);
                Platform.runLater(() -> {
                    controller.getProgressBar().setProgress(0);
                    controller.getStatusLabel().setText("Erro: " + e.getMessage());
                });
            }
        }).start();
    }

    public void removeSelected() {
        String selected = controller.getModList().getSelectionModel().getSelectedItem();
        if (selected == null) {
            controller.getStatusLabel().setText("Selecione um item para remover");
            return;
        }

        if (selected.startsWith("[Modpack]") || selected.startsWith("[Perfil]")) {
            int index = controller.getModList().getSelectionModel().getSelectedIndex();
            if (index < 0 || index >= controller.getLastSearchResults().size()) return;
            ModInfo item = controller.getLastSearchResults().get(index);
            String displayName = item.getName();
            File baseDir = SettingsManager.getInstance().getBaseDir();

            File targetDir = null;
            if (selected.startsWith("[Modpack]")) {
                String dirName = item.getFileName() != null ? item.getFileName() : item.getName();
                File modpacksRoot = new File(baseDir, "modpacks");
                targetDir = new File(modpacksRoot, dirName);
                if (targetDir != null && !targetDir.exists() && modpacksRoot.exists()) {
                    for (File d : modpacksRoot.listFiles(File::isDirectory)) {
                        if (d.getName().equals(dirName)
                                || d.getName().toLowerCase().contains(dirName.toLowerCase())) {
                            targetDir = d;
                            break;
                        }
                    }
                }
            } else {
                // [Perfil] — target dir = profile.gameDir (se existir)
                if (profileManager != null) {
                    LaunchProfile p = profileManager.getProfiles().stream()
                            .filter(x -> x.getName().equals(displayName))
                            .findFirst().orElse(null);
                    if (p != null && p.getGameDir() != null && !p.getGameDir().isBlank()) {
                        targetDir = new File(p.getGameDir());
                    }
                }
            }

            String bodyStr = (targetDir == null || !targetDir.exists())
                    ? "A pasta do jogo não existe no disco. Apenas o perfil será removido do MineLauncher."
                    : "O diretório do jogo será apagado do disco e o perfil removido do MineLauncher.";

            final File finalTargetDir = targetDir;
            final String finalDisplayName = displayName;
            showConfirmDialog("Remover instalação", "Remover \"" + displayName + "\"?", bodyStr, ok -> {
                if (ok) {
                    if (finalTargetDir != null && finalTargetDir.exists()) {
                        com.minelauncher.utils.FileUtils.deleteDirectory(finalTargetDir);
                    }
                    profileManager.removeProfile(finalDisplayName);
                    Platform.runLater(() -> {
                        controller.loadProfiles();
                        showInstalled();
                        controller.getStatusLabel().setText("Removido: " + finalDisplayName);
                    });
                }
            });

        } else if (selected.startsWith("[Mod") && !selected.startsWith("[Modpack]")) {
            int index = controller.getModList().getSelectionModel().getSelectedIndex();
            if (index < 0 || index >= controller.getLastSearchResults().size()) return;
            ModInfo item = controller.getLastSearchResults().get(index);
            LaunchProfile profile = profileManager.getActiveProfile();
            if (profile == null) return;
            File modsDir = new File(SettingsManager.getInstance().getBaseDir(),
                    profile.getGameDir() != null ? profile.getGameDir() + "/mods" : "mods");
            modManager.removeMod(modsDir, item.getFileName());
            controller.getStatusLabel().setText("Mod removido: " + item.getName());
        }
    }

    public void showInstalled() {
        controller.getModList().getItems().clear();
        controller.getLastSearchResults().clear();
        controller.getStatusLabel().setText("Modpacks / perfis instalados");

        File baseDir = SettingsManager.getInstance().getBaseDir();
        File modpacksDir = new File(baseDir, "modpacks");

        // 1) Lista subdiretórios de modpacks/ (modpacks reais baixados)
        if (modpacksDir.exists() && modpacksDir.listFiles() != null) {
            for (File dir : modpacksDir.listFiles(File::isDirectory)) {
                String displayName = com.minelauncher.utils.ModpackNameResolver.resolve(
                        dir, profileManager != null ? profileManager.getProfiles() : null);
                boolean dirExists = true;
                controller.getModList().getItems().add("[Modpack] " + displayName
                        + (dirExists ? "" : "  (sem pasta)"));
                ModInfo info = new ModInfo(displayName, dir.getName(), "installed");
                controller.getLastSearchResults().add(info);
            }
        }

        // 2) Lista perfis que ainda não apareceram como [Modpack].
        //    Só pula se o gameDir EXISTE e está dentro de modpacks/
        //    (porque aí ele já foi listado no passo 1).
        //    Se o gameDir está em modpacks/ mas NÃO existe, ainda
        //    precisa aparecer aqui para que o usuário possa removê-lo.
        if (profileManager != null) {
            String modpacksAbs = modpacksDir.getAbsolutePath();
            for (LaunchProfile p : profileManager.getProfiles()) {
                String gd = p.getGameDir();
                File gameDir = (gd == null || gd.isBlank()) ? null : new File(gd);
                boolean alreadyListed = gameDir != null
                        && gameDir.exists()
                        && gameDir.getAbsolutePath().startsWith(modpacksAbs);
                if (alreadyListed) continue;
                String suffix = (gameDir != null && gameDir.exists()) ? "" : "  (sem pasta)";
                controller.getModList().getItems().add("[Perfil] " + p.getName() + suffix);
                ModInfo info = new ModInfo(p.getName(), p.getName(), "profile");
                controller.getLastSearchResults().add(info);
            }
        }

        if (controller.getModList().getItems().isEmpty()) {
            controller.getModList().getItems().add("Nenhum modpack instalado");
        }
    }

    /**
     * Resolve o melhor nome "humano" para um diretório de modpack.
     * Movido para {@link com.minelauncher.utils.ModpackNameResolver} (H-2).
     */
    private String resolveModpackDisplayName(File modpackDir) {
        return com.minelauncher.utils.ModpackNameResolver.resolve(
                modpackDir, profileManager != null ? profileManager.getProfiles() : null);
    }

    /** Heurística: nomes com formato UUID ou só hex/dígitos com hífens são "lixo" pro display */
    private static boolean looksLikeGarbage(String s) {
        return com.minelauncher.utils.ModpackNameResolver.looksLikeGarbage(s);
    }

    public void checkUpdates() {
        String selected = controller.getModList().getSelectionModel().getSelectedItem();
        if (selected == null || !selected.startsWith("[Modpack]")) {
            controller.getStatusLabel().setText("Selecione um modpack instalado para verificar atualizações");
            return;
        }

        int index = controller.getModList().getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= controller.getLastSearchResults().size()) return;

        ModInfo item = controller.getLastSearchResults().get(index);
        File baseDir = SettingsManager.getInstance().getBaseDir();
        File modpackDir = new File(baseDir, "modpacks/" + item.getFileName());

        if (!modpackDir.exists()) {
            controller.getStatusLabel().setText("Diretório do modpack não encontrado");
            return;
        }

        controller.getStatusLabel().setText("Verificando atualizações...");
        controller.getProgressBar().setProgress(-1);

        new Thread(() -> {
            try {
                List<String> updates = modManager.checkModUpdates(modpackDir);
                Platform.runLater(() -> {
                    controller.getProgressBar().setProgress(0);
                    if (updates.isEmpty()) {
                        controller.getStatusLabel().setText("Todos os mods estão atualizados!");
                    } else {
                        controller.getStatusLabel().setText(updates.size() + " atualizações disponíveis");
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Atualizações disponíveis");
                        alert.setHeaderText(updates.size() + " mods têm atualização:");
                        alert.setContentText(String.join("\n", updates));
                        alert.showAndWait();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    controller.getProgressBar().setProgress(0);
                    controller.getStatusLabel().setText("Erro ao verificar: " + e.getMessage());
                });
            }
        }).start();
    }

    public void importModpack() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Importar modpack");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Modpacks (*.zip, *.mrpack)", "*.zip", "*.mrpack"),
                new javafx.stage.FileChooser.ExtensionFilter("Todos os arquivos", "*.*")
        );

        java.io.File selectedFile = fileChooser.showOpenDialog(controller.getModList().getScene().getWindow());
        if (selectedFile == null) return;

        String fileName = selectedFile.getName();
        boolean isMrpack = fileName.endsWith(".mrpack");
        String modpackName = fileName.replace(".zip", "").replace(".mrpack", "");
        String sanitized = sanitizeName(modpackName);

        File baseDir = SettingsManager.getInstance().getBaseDir();
        File modpackDir = new File(baseDir, "modpacks/" + sanitized);

        if (modpackDir.exists()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Modpack já existe");
            alert.setHeaderText("Já existe um modpack com esse nome:");
            alert.setContentText(modpackDir.getAbsolutePath());
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            com.minelauncher.utils.FileUtils.deleteDirectory(modpackDir);
        }

        controller.getStatusLabel().setText("Importando modpack...");
        controller.getProgressBar().setProgress(-1);

        new Thread(() -> {
            try {
                modpackDir.mkdirs();

                if (isMrpack) {
                    modManager.extractModrinthMrpack(selectedFile, modpackDir, (msg, pct) -> {
                        Platform.runLater(() -> {
                            controller.getStatusLabel().setText(msg);
                            controller.getProgressBar().setProgress(0.1 + pct * 0.7);
                        });
                    });
                } else {
                    modManager.extractCurseForgeZip(selectedFile, modpackDir);
                    File manifest = new File(modpackDir, "manifest.json");
                    if (manifest.exists()) {
                        modManager.downloadCurseForgeModsFromManifest(manifest, modpackDir, (msg, pct) -> {
                            Platform.runLater(() -> {
                                controller.getStatusLabel().setText(msg);
                                controller.getProgressBar().setProgress(0.3 + pct * 0.6);
                            });
                        });
                    }
                }

                String mcVersion = "1.21.1";
                String modLoader = "vanilla";
                String modLoaderVersion = "";

                File cfManifest = new File(modpackDir, "manifest.json");
                if (cfManifest.exists()) {
                    String json = java.nio.file.Files.readString(cfManifest.toPath());
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("minecraft")) {
                        com.google.gson.JsonObject mc = obj.getAsJsonObject("minecraft");
                        if (mc.has("version")) mcVersion = mc.get("version").getAsString();
                        if (mc.has("modLoaders")) {
                            com.google.gson.JsonArray loaders = mc.getAsJsonArray("modLoaders");
                            if (loaders.size() > 0) {
                                String loaderId = loaders.get(0).getAsJsonObject().get("id").getAsString();
                                if (loaderId.contains("neoforge")) { modLoader = "neoforge"; modLoaderVersion = loaderId.replace("neoforge-", ""); }
                                else if (loaderId.contains("forge")) { modLoader = "forge"; modLoaderVersion = loaderId.replace("forge-", ""); }
                                else if (loaderId.contains("fabric")) { modLoader = "fabric"; }
                                else if (loaderId.contains("quilt")) { modLoader = "quilt"; }
                            }
                        }
                    }
                }

                LaunchProfile newProfile = new LaunchProfile(modpackName, mcVersion);
                newProfile.setModLoader(modLoader);
                newProfile.setModLoaderVersion(modLoaderVersion);
                newProfile.setGameDir(modpackDir.getAbsolutePath());
                profileManager.addProfile(newProfile);

                Platform.runLater(() -> {
                    controller.loadProfiles();
                    controller.getProgressBar().setProgress(1.0);
                    controller.getStatusLabel().setText("Modpack importado: " + modpackName);
                });

            } catch (Exception e) {
                LOG.error("Erro ao importar modpack", e);
                Platform.runLater(() -> {
                    controller.getProgressBar().setProgress(0);
                    controller.getStatusLabel().setText("Erro ao importar: " + e.getMessage());
                });
            }
        }).start();
    }

    public void openModsFolder() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null) {
            controller.getStatusLabel().setText("Nenhum perfil selecionado");
            return;
        }
        File modsDir = new File(SettingsManager.getInstance().getBaseDir(),
                profile.getGameDir() != null ? profile.getGameDir() + "/mods" : "mods");
        modsDir.mkdirs();
        
        new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(modsDir);
                } else {
                    new ProcessBuilder("xdg-open", modsDir.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                Platform.runLater(() -> controller.getStatusLabel().setText("Erro ao abrir pasta: " + e.getMessage()));
            }
        }).start();
    }

    public ModVersionInfo showVersionDialog(ModInfo mod, boolean isModpack) {
        try {
            List<ModVersionInfo> versions;
            if ("curseforge".equals(mod.getSource())) {
                int modId = Integer.parseInt(mod.getId());
                versions = modManager.getCurseForgeVersions(modId);
            } else {
                versions = modManager.getModrinthAllVersions(mod.getId());
            }

            if (versions.isEmpty()) {
                Platform.runLater(() -> controller.getStatusLabel().setText("Nenhuma versão encontrada para " + mod.getName()));
                return null;
            }

            if (!isModpack) {
                LaunchProfile activeProfile = profileManager.getActiveProfile();
                if (activeProfile != null && activeProfile.getGameVersion() != null) {
                    String mcVersion = activeProfile.getGameVersion();
                    List<ModVersionInfo> filtered = versions.stream()
                            .filter(v -> v.getGameVersions() != null && v.getGameVersions().contains(mcVersion))
                            .collect(Collectors.toList());
                    if (!filtered.isEmpty()) versions = filtered;
                }
            }

            final List<ModVersionInfo> finalVersions = versions;
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ModVersionInfo> selected = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    Dialog<ModVersionInfo> dialog = new Dialog<>();
                    dialog.setTitle("Selecionar versão");
                    dialog.setHeaderText("Escolha a versão para instalar de:\n" + mod.getName());

                    ButtonType okBtn = new ButtonType("Instalar", ButtonBar.ButtonData.OK_DONE);
                    dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

                    ListView<ModVersionInfo> listView = new ListView<>();
                    listView.getItems().addAll(finalVersions);
                    listView.setPrefSize(500, 300);
                    listView.getSelectionModel().selectFirst();

                    listView.setCellFactory(lv -> new ListCell<>() {
                        @Override
                        protected void updateItem(ModVersionInfo item, boolean empty) {
                            super.updateItem(item, empty);
                            setText(empty || item == null ? null : item.toString());
                        }
                    });

                    dialog.getDialogPane().setContent(listView);

                    dialog.setResultConverter(btn -> {
                        if (btn == okBtn) return listView.getSelectionModel().getSelectedItem();
                        return null;
                    });

                    dialog.showAndWait().ifPresent(selected::set);
                } finally {
                    latch.countDown();
                }
            });

            if (!latch.await(30, TimeUnit.SECONDS)) {
                LOG.warn("Timeout ao aguardar seleção de versão");
                return null;
            }
            return selected.get();
        } catch (Exception e) {
            LOG.error("Erro ao buscar versões", e);
            Platform.runLater(() -> controller.getStatusLabel().setText("Erro ao buscar versões: " + e.getMessage()));
            return null;
        }
    }

    public static String sanitizeName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9\\s\\-]", "").replaceAll("\\s+", "_").trim();
        if (sanitized.isEmpty()) {
            sanitized = "modpack_" + Math.abs(name.hashCode());
        }
        return sanitized;
    }

    /**
     * Dialog de confirmação custom (dark theme) — overlay INLINE no Scene do primary.
     *
     * Por que overlay em vez de Stage separado: o WM (KDE/GNOME/XFCE) às vezes
     * desmaximiza a janela pai quando uma janela modal filha abre. Como overlay
     * é parte do mesmo Scene/Stage, o primary NUNCA é tocado pelo SO.
     */
    public static void showConfirmDialog(String title, String header, String body, java.util.function.Consumer<Boolean> callback) {
        Platform.runLater(() -> {
            try {
                Stage owner = com.minelauncher.MineLauncher.getPrimaryStage();
                if (owner == null || owner.getScene() == null) return;

                javafx.scene.Scene scene = owner.getScene();
                javafx.scene.layout.Pane rootPane = (javafx.scene.layout.Pane) scene.getRoot();

                // Backdrop semi-transparente
                javafx.scene.layout.Region backdrop = new javafx.scene.layout.Region();
                backdrop.setStyle("-fx-background-color: rgba(5, 7, 12, 0.72);");

                // StackPane para centralizar o card
                javafx.scene.layout.StackPane overlayRoot = new javafx.scene.layout.StackPane();
                overlayRoot.setPickOnBounds(true);
                overlayRoot.prefWidthProperty().bind(rootPane.widthProperty());
                overlayRoot.prefHeightProperty().bind(rootPane.heightProperty());
                
                backdrop.prefWidthProperty().bind(overlayRoot.widthProperty());
                backdrop.prefHeightProperty().bind(overlayRoot.heightProperty());

                // Card
                VBox card = new VBox(0);
                card.setStyle(
                    "-fx-background-color: #0F1320;" +
                    "-fx-border-color: #1A2030;" +
                    "-fx-border-width: 1;" +
                    "-fx-background-radius: 10;" +
                    "-fx-border-radius: 10;" +
                    "-fx-effect: dropshadow(gaussian, #000000CC, 24, 0, 0, 12);"
                );
                card.setMaxWidth(440);
                card.setMinWidth(440);
                card.setMaxHeight(Region.USE_PREF_SIZE);

                // Header bar
                HBox headerBar = new HBox(10);
                headerBar.setAlignment(Pos.CENTER_LEFT);
                headerBar.setPadding(new Insets(18, 22, 14, 22));
                headerBar.setStyle("-fx-border-color: #1A2030; -fx-border-width: 0 0 1 0;");
                Region dot = new Region();
                dot.setStyle("-fx-background-color: #FFB454; -fx-background-radius: 4; -fx-min-width: 8; -fx-min-height: 8;");
                Label titleLbl = new Label(title.toUpperCase());
                titleLbl.setStyle("-fx-text-fill: #FFB454; -fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 11px; -fx-font-weight: 800; -fx-letter-spacing: 2px;");
                headerBar.getChildren().addAll(dot, titleLbl);

                // Body
                VBox bodyBox = new VBox(10);
                bodyBox.setPadding(new Insets(20, 22, 20, 22));
                Label headerLbl = new Label(header);
                headerLbl.setStyle("-fx-text-fill: #E8EAF1; -fx-font-family: 'Fira Sans', sans-serif; -fx-font-size: 18px; -fx-font-weight: 700;");
                headerLbl.setWrapText(true);
                Label bodyLbl = new Label(body);
                bodyLbl.setStyle("-fx-text-fill: #8A93AB; -fx-font-family: 'Fira Sans', sans-serif; -fx-font-size: 12px;");
                bodyLbl.setWrapText(true);
                bodyBox.getChildren().addAll(headerLbl, bodyLbl);

                // Buttons
                HBox actions = new HBox(8);
                actions.setAlignment(Pos.CENTER_RIGHT);
                actions.setPadding(new Insets(0, 22, 18, 22));

                Button cancelBtn = new Button("Cancelar");
                cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #8A93AB; -fx-padding: 9 18; -fx-border-color: #1A2030; -fx-border-radius: 6; -fx-cursor: hand;");
                
                Button okBtn = new Button("Confirmar");
                okBtn.setStyle("-fx-background-color: #F87171; -fx-text-fill: #0F1320; -fx-font-weight: 800; -fx-padding: 9 18; -fx-background-radius: 6; -fx-cursor: hand;");

                actions.getChildren().addAll(cancelBtn, okBtn);
                card.getChildren().addAll(headerBar, bodyBox, actions);
                
                overlayRoot.getChildren().addAll(backdrop, card);
                StackPane.setAlignment(card, Pos.CENTER);

                Runnable close = () -> rootPane.getChildren().remove(overlayRoot);

                cancelBtn.setOnAction(e -> { close.run(); callback.accept(false); });
                okBtn.setOnAction(e -> { close.run(); callback.accept(true); });

                rootPane.getChildren().add(overlayRoot);
                card.requestFocus();
            } catch (Exception e) {
                LOG.error("Erro ao mostrar dialog", e);
            }
        });
    }
}
