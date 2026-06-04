package com.minelauncher.ui.controllers;

import com.minelauncher.auth.MicrosoftAuth;
import com.minelauncher.auth.OfflineAuth;
import com.minelauncher.launcher.GameLauncher;
import com.minelauncher.mods.ModManager;
import com.minelauncher.models.ModInfo;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import java.io.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private Stage stage;
    private double xOffset, yOffset;

    // Header
    @FXML
    private HBox headerBar;
    @FXML
    private Label titleLabel;
    @FXML
    private Button minimizeBtn;
    @FXML
    private Button maximizeBtn;
    @FXML
    private Button closeBtn;

    // Sidebar
    @FXML
    private VBox sidebar;
    @FXML
    private ComboBox<String> accountCombo;
    @FXML
    private Button playButton;
    @FXML
    private ComboBox<String> profileCombo;
    @FXML
    private Button homeBtn;
    @FXML
    private Button versionsBtn;
    @FXML
    private Button modsBtn;
    @FXML
    private Button resourcePacksBtn;
    @FXML
    private Button savesBtn;
    @FXML
    private Button screenshotsBtn;
    @FXML
    private Button settingsBtn;

    // Content
    @FXML
    private StackPane contentPane;

    // Home tab
    @FXML
    private VBox homePane;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label welcomeProfileLabel;
    @FXML
    private Label welcomeVersionLabel;
    @FXML
    private Text lastPlayedText;
    @FXML
    private Text versionStatusText;

    // Versions tab
    @FXML
    private VBox versionsPane;
    @FXML
    private TextField versionSearch;
    @FXML
    private ListView<String> versionList;
    @FXML
    private Button installVersionBtn;

    // Mods tab
    @FXML
    private VBox modsPane;
    @FXML
    private TextField modSearch;
    @FXML
    private ComboBox<String> sourceCombo;
    @FXML
    private ComboBox<String> typeCombo;
    @FXML
    private ListView<String> modList;

    // Resource Packs tab
    @FXML
    private VBox resourcePacksPane;
    @FXML
    private TextField resourcePackSearch;
    @FXML
    private ListView<String> resourcePackList;

    // Saves tab
    @FXML
    private VBox savesPane;
    @FXML
    private TextField saveSearch;
    @FXML
    private ListView<String> saveList;

    // Screenshots tab
    @FXML
    private VBox screenshotsPane;
    @FXML
    private TextField screenshotSearch;
    @FXML
    private ListView<String> screenshotList;

    // Settings tab
    @FXML
    private VBox settingsPane;
    @FXML
    private TextField minRamField;
    @FXML
    private TextField maxRamField;
    @FXML
    private ComboBox<String> javaCombo;
    @FXML
    private CheckBox snapshotsCheck;
    @FXML
    private CheckBox keepOpenCheck;

    // Status
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    // Header session chip
    @FXML
    private Label sessionStatusLabel;
    @FXML
    private Label sessionTimeLabel;

    // Status bar (live readouts)
    @FXML
    private Label statusClockLabel;
    @FXML
    private Label statusJavaLabel;
    @FXML
    private Label statusRamLabel;
    @FXML
    private Label statusNetLabel;

    // Console
    @FXML
    private TextArea consoleArea;

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    // Managers
    private ProfileManager profileManager;
    private VersionManager versionManager;
    private GameLauncher gameLauncher;
    private ModManager modManager;
    private MicrosoftAuth microsoftAuth;
    private ModActions modActions;

    // Lista de resultados da última busca
    private List<ModInfo> lastSearchResults = new ArrayList<>();

    // Listas-fonte para filtro local (resource packs, saves, screenshots)
    private final List<String> resourcePacksFull = new ArrayList<>();
    private final List<String> savesFull = new ArrayList<>();
    private final List<String> screenshotsFull = new ArrayList<>();

    // ── Live status / clock / RAM / net ──
    private enum LauncherState {
        READY, BUSY, PLAYING, ERROR
    }

    private long sessionStartMs = System.currentTimeMillis();
    private javafx.animation.AnimationTimer liveTimer;
    private volatile boolean netOnline = true;
    private long lastRamUpdateNs = 0;
    private long lastNetUpdateNs = 0;
    private int clockTickCounter = 0;

    // Fuso horário fixo do Brasil — não depende do default da JVM
    private static final java.time.ZoneId BRAZIL_ZONE = java.time.ZoneId.of("America/Sao_Paulo");
    private static final java.time.format.DateTimeFormatter TIME_FMT = java.time.format.DateTimeFormatter
            .ofPattern("HH:mm:ss");
    private static final java.time.format.DateTimeFormatter TIME_FMT_FULL = java.time.format.DateTimeFormatter
            .ofPattern("dd/MM HH:mm:ss");

    private static final java.util.List<String> SESSION_VARIANTS = java.util.List.of(
            "session-chip-accent", "session-chip-warm", "session-chip-cool", "session-chip-danger",
            "session-chip-value");
    private static final java.util.List<String> STATUS_VARIANTS = java.util.List.of(
            "status-text", "status-text-accent", "status-text-warm", "status-text-cool", "status-text-danger");

    // Getters para acesso por módulos helper
    public Label getStatusLabel() {
        return statusLabel;
    }

    public ProgressBar getProgressBar() {
        return progressBar;
    }

    public ListView<String> getModList() {
        return modList;
    }

    public TextField getModSearch() {
        return modSearch;
    }

    public ComboBox<String> getSourceCombo() {
        return sourceCombo;
    }

    public ComboBox<String> getTypeCombo() {
        return typeCombo;
    }

    public List<ModInfo> getLastSearchResults() {
        return lastSearchResults;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public ModManager getModManager() {
        return modManager;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        debugLog(">>> initialize() entrou");
        SettingsManager settings = SettingsManager.getInstance();
        profileManager = new ProfileManager(settings.getBaseDir());
        versionManager = new VersionManager(settings.getBaseDir());
        gameLauncher = new GameLauncher(settings.getBaseDir(), versionManager);
        modManager = new ModManager(settings.getBaseDir());
        microsoftAuth = new MicrosoftAuth();
        modActions = new ModActions(this, modManager, profileManager);
        debugLog(">>> initialize() deps OK");

        setupUI();
        debugLog(">>> initialize() setupUI OK | clockLabel=" + statusClockLabel);
        setupDrag();
        loadProfiles();
        loadVersions();
        loadSavedAccount();
        debugLog(">>> initialize() FIM");
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void setupUI() {
        // Campos de RAM (só aceitam números)
        setupRamField(minRamField, 256, 8192, 512);
        setupRamField(maxRamField, 512, 16384, 2048);

        // Perfil combo — escuta o valueProperty (pega qualquer mudança, mesmo
        // programática)
        profileCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.equals(oldV)) {
                profileManager.setActiveProfile(newV);
                LaunchProfile p = profileManager.getActiveProfile();
                if (p != null) {
                    minRamField.setText(String.valueOf(p.getMinRam()));
                    maxRamField.setText(String.valueOf(p.getMaxRam()));
                }
                // Resetar contadores de sessão + atualizar header
                sessionStartMs = System.currentTimeMillis();
                updateClock();
                updateWelcomeHeader();
            }
        });

        // Combo de fonte (Modrinth/CurseForge/Ambos)
        sourceCombo.setItems(FXCollections.observableArrayList("Ambos", "Modrinth", "CurseForge"));
        sourceCombo.setValue("Ambos");

        // Combo de tipo (Mods/Modpacks)
        typeCombo.setItems(FXCollections.observableArrayList("Mods", "Modpacks"));
        typeCombo.setValue("Mods");

        // Filtro local das views locais (resource packs / saves / screenshots)
        resourcePackSearch.setOnKeyReleased(e -> filterResourcePacks());
        saveSearch.setOnKeyReleased(e -> filterSaves());
        screenshotSearch.setOnKeyReleased(e -> filterScreenshots());

        // Mostrar home por padrão
        showTab("home");

        // Status inicial
        statusLabel.setText("Pronto");
        progressBar.setProgress(0);

        // Context menu no modList
        setupModListContextMenu();

        // Pulse sutil no botão JOGAR (idle state)
        pulsePlayButton();

        // Live: clock, RAM, net
        startLiveUpdates();
        updateJavaInfo();
        setState(LauncherState.READY);
    }

    private void setupModListContextMenu() {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();

        javafx.scene.control.MenuItem installItem = new javafx.scene.control.MenuItem("Instalar");
        installItem.setOnAction(e -> installSelected());

        javafx.scene.control.MenuItem removeItem = new javafx.scene.control.MenuItem("Remover");
        removeItem.setOnAction(e -> removeSelected());

        javafx.scene.control.MenuItem backupItem = new javafx.scene.control.MenuItem("Backup save");
        backupItem.setOnAction(e -> backupWorld());

        javafx.scene.control.MenuItem openFolderItem = new javafx.scene.control.MenuItem("Abrir pasta");
        openFolderItem.setOnAction(e -> openItemFolder());

        javafx.scene.control.MenuItem openScreenshot = new javafx.scene.control.MenuItem("Abrir screenshot");
        openScreenshot.setOnAction(e -> openSelectedScreenshot());

        contextMenu.getItems().addAll(installItem, removeItem, backupItem, openFolderItem, openScreenshot);

        modList.setContextMenu(contextMenu);

        // Atualizar visibilidade dos itens baseado no tipo selecionado
        modList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                contextMenu.hide();
                return;
            }
            boolean isSave = newVal.startsWith("[Save]");
            boolean isScreenshot = newVal.startsWith("[Screenshot]");
            boolean isResourcePack = newVal.startsWith("[Resource Pack]");
            boolean isMod = newVal.startsWith("[Modrinth]") || newVal.startsWith("[CurseForge]");
            boolean isModpack = newVal.startsWith("[Modpack]");

            installItem.setVisible(isMod || isModpack);
            removeItem.setVisible(isMod || isModpack || isResourcePack);
            backupItem.setVisible(isSave);
            openFolderItem.setVisible(true);
            openScreenshot.setVisible(isScreenshot);
        });
    }

    private void setupRamField(TextField field, int min, int max, int def) {
        field.setText(String.valueOf(def));
        field.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) {
                field.setText(val.replaceAll("\\D", ""));
            }
            if (!field.getText().isEmpty()) {
                int v = Integer.parseInt(field.getText());
                if (v < min)
                    field.setText(String.valueOf(min));
                if (v > max)
                    field.setText(String.valueOf(max));
            }
        });
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is && field.getText().isEmpty()) {
                field.setText(String.valueOf(def));
            }
        });
    }

    private int parseRam(TextField field) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return 2048;
        }
    }

    private void openItemFolder() {
        String selected = modList.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        LaunchProfile profile = profileManager.getActiveProfile();
        File baseDir = SettingsManager.getInstance().getBaseDir();
        File gameDir;
        if (profile != null && profile.getGameDir() != null && !profile.getGameDir().isEmpty()) {
            gameDir = new File(profile.getGameDir());
            if (!gameDir.isAbsolute())
                gameDir = new File(baseDir, profile.getGameDir());
        } else {
            gameDir = baseDir;
        }

        File targetDir;
        if (selected.startsWith("[Save]")) {
            targetDir = new File(gameDir, "saves");
        } else if (selected.startsWith("[Screenshot]")) {
            targetDir = new File(gameDir, "screenshots");
        } else if (selected.startsWith("[Resource Pack]")) {
            targetDir = new File(gameDir, "resourcepacks");
        } else {
            targetDir = new File(gameDir, "mods");
        }

        new Thread(() -> {
            try {
                if (targetDir.exists()) {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(targetDir);
                    } else {
                        new ProcessBuilder("xdg-open", targetDir.getAbsolutePath()).start();
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Erro ao abrir pasta: " + e.getMessage()));
            }
        }).start();
    }

    private void openSelectedScreenshot() {
        String selected = modList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.startsWith("[Screenshot]"))
            return;

        LaunchProfile profile = profileManager.getActiveProfile();
        File baseDir = SettingsManager.getInstance().getBaseDir();
        File gameDir;
        if (profile != null && profile.getGameDir() != null && !profile.getGameDir().isEmpty()) {
            gameDir = new File(profile.getGameDir());
            if (!gameDir.isAbsolute())
                gameDir = new File(baseDir, profile.getGameDir());
        } else {
            gameDir = baseDir;
        }

        String fileName = selected.replace("[Screenshot] ", "").split(" - ")[0].trim();
        File screenshot = new File(gameDir, "screenshots/" + fileName);
        
        new Thread(() -> {
            try {
                if (screenshot.exists()) {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(screenshot);
                    } else {
                        new ProcessBuilder("xdg-open", screenshot.getAbsolutePath()).start();
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Erro ao abrir screenshot: " + e.getMessage()));
            }
        }).start();
    }

    private void setupDrag() {
        headerBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        headerBar.setOnMouseDragged(event -> {
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(Math.max(screen.getMinY(), event.getScreenY() - yOffset));
        });
    }

    public void loadProfiles() {
        profileCombo.setItems(FXCollections.observableArrayList());
        for (LaunchProfile p : profileManager.getProfiles()) {
            profileCombo.getItems().add(p.getName());
        }
        LaunchProfile active = profileManager.getActiveProfile();
        if (active != null) {
            profileCombo.setValue(active.getName());
        }
        updateWelcomeHeader();
    }

    private void loadVersions() {
        new Thread(() -> {
            try {
                var manifest = versionManager.getVersionManifest();
                Platform.runLater(() -> {
                    versionList.setItems(FXCollections.observableArrayList());
                    for (var v : manifest.getVersions()) {
                        versionList.getItems().add(v.getId() + " [" + v.getType() + "]");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Erro ao carregar versões: " + e.getMessage()));
            }
        }).start();
    }

    private void loadSavedAccount() {
        refreshAccountCombo();

        // Listener para trocar de conta
        accountCombo.setOnAction(e -> {
            String selected = accountCombo.getValue();
            if (selected == null)
                return;

            // Extrair nome (remover sufixo " (Offline)" ou " (Microsoft)")
            String accountName = selected.replace(" (Offline)", "").replace(" (Microsoft)", "").trim();

            for (GameProfile acc : SettingsManager.getInstance().getAccounts()) {
                if (acc.getName().equals(accountName)) {
                    SettingsManager.getInstance().setSelectedAccountUuid(acc.getUuid().toString());
                    LOG.info("Conta selecionada: {}", acc.getName());
                    break;
                }
            }
        });

        GameProfile account = SettingsManager.getInstance().getSelectedAccount();
        if (account != null) {
            String suffix = account.isOffline() ? " (Offline)" : " (Microsoft)";
            accountCombo.setValue(account.getName() + suffix);
            LOG.info("Conta carregada: {}", account.getName());
        }
    }

    private void refreshAccountCombo() {
        accountCombo.getItems().clear();
        for (GameProfile acc : SettingsManager.getInstance().getAccounts()) {
            String suffix = acc.isOffline() ? " (Offline)" : " (Microsoft)";
            accountCombo.getItems().add(acc.getName() + suffix);
        }
    }

    // ==================== NAVEGAÇÃO ====================

    @FXML
    private void showHome() {
        showTab("home");
    }

    @FXML
    private void showVersions() {
        showTab("versions");
    }

    @FXML
    private void showMods() {
        showTab("mods");
    }

    @FXML
    private void showResourcePacks() {
        showTab("resourcePacks");
        statusLabel.setText("Resource Packs");
        LaunchProfile profile = profileManager.getActiveProfile();
        resourcePacksFull.clear();
        resourcePackSearch.clear();
        resourcePackList.getItems().clear();

        if (profile == null) {
            resourcePackList.getItems().add("Nenhum perfil ativo");
            return;
        }

        File gameDir = resolveGameDir(profile);
        File rpDir = new File(gameDir, "resourcepacks");

        if (!rpDir.exists() || rpDir.listFiles() == null) {
            resourcePackList.getItems().add("Nenhum resource pack encontrado");
            return;
        }

        for (File f : rpDir.listFiles()) {
            if (f.isFile() && (f.getName().endsWith(".zip") || f.getName().endsWith(".jar"))) {
                resourcePacksFull.add(f.getName());
            }
        }
        if (resourcePacksFull.isEmpty()) {
            resourcePackList.getItems().add("Nenhum resource pack encontrado");
        } else {
            java.util.Collections.sort(resourcePacksFull);
            resourcePackList.getItems().addAll(resourcePacksFull);
        }
    }

    @FXML
    private void showSaves() {
        showTab("saves");
        statusLabel.setText("Saves / Mundos");
        LaunchProfile profile = profileManager.getActiveProfile();
        savesFull.clear();
        saveSearch.clear();
        saveList.getItems().clear();

        if (profile == null) {
            saveList.getItems().add("Nenhum perfil ativo");
            return;
        }

        File gameDir = resolveGameDir(profile);
        File savesDir = new File(gameDir, "saves");

        if (!savesDir.exists() || savesDir.listFiles() == null) {
            saveList.getItems().add("Nenhum save encontrado");
            return;
        }

        for (File f : savesDir.listFiles()) {
            if (f.isDirectory() && new File(f, "level.dat").exists()) {
                savesFull.add(f.getName());
            }
        }
        if (savesFull.isEmpty()) {
            saveList.getItems().add("Nenhum save encontrado");
        } else {
            java.util.Collections.sort(savesFull);
            saveList.getItems().addAll(savesFull);
        }
    }

    @FXML
    private void showScreenshots() {
        showTab("screenshots");
        statusLabel.setText("Screenshots");
        LaunchProfile profile = profileManager.getActiveProfile();
        screenshotsFull.clear();
        screenshotSearch.clear();
        screenshotList.getItems().clear();

        if (profile == null) {
            screenshotList.getItems().add("Nenhum perfil ativo");
            return;
        }

        File gameDir = resolveGameDir(profile);
        File ssDir = new File(gameDir, "screenshots");

        if (!ssDir.exists() || ssDir.listFiles() == null) {
            screenshotList.getItems().add("Nenhum screenshot encontrado");
            return;
        }

        java.io.File[] files = ssDir.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
        if (files != null) {
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(java.io.File::lastModified).reversed());
            for (File f : files) {
                String date = java.time.format.DateTimeFormatter
                        .ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(BRAZIL_ZONE)
                        .format(java.time.Instant.ofEpochMilli(f.lastModified()));
                screenshotsFull.add(f.getName() + " - " + date);
            }
        }
        if (screenshotsFull.isEmpty()) {
            screenshotList.getItems().add("Nenhum screenshot encontrado");
        } else {
            screenshotList.getItems().addAll(screenshotsFull);
        }
    }

    @FXML
    private void refreshResourcePacks() {
        showResourcePacks();
    }

    @FXML
    private void refreshSaves() {
        showSaves();
    }

    @FXML
    private void refreshScreenshots() {
        showScreenshots();
    }

    @FXML
    private void openResourcePacksFolder() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null)
            return;
        File dir = new File(resolveGameDir(profile), "resourcepacks");
        dir.mkdirs();
        openInFileManager(dir);
    }

    @FXML
    private void openSavesFolder() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null)
            return;
        File dir = new File(resolveGameDir(profile), "saves");
        dir.mkdirs();
        openInFileManager(dir);
    }

    @FXML
    private void openScreenshotsFolder() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null)
            return;
        File dir = new File(resolveGameDir(profile), "screenshots");
        dir.mkdirs();
        openInFileManager(dir);
    }

    private File resolveGameDir(LaunchProfile profile) {
        if (profile.getGameDir() != null && !profile.getGameDir().isEmpty()) {
            File dir = new File(profile.getGameDir());
            if (!dir.isAbsolute())
                return new File(SettingsManager.getInstance().getBaseDir(), profile.getGameDir());
            return dir;
        }
        return SettingsManager.getInstance().getBaseDir();
    }

    private void openInFileManager(File dir) {
        new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(dir);
                } else {
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                LOG.error("Failed to open folder: {}", dir, e);
                Platform.runLater(() -> statusLabel.setText("Não foi possível abrir a pasta"));
            }
        }).start();
    }

    private void filterResourcePacks() {
        String query = resourcePackSearch.getText().toLowerCase();
        resourcePackList.getItems().clear();
        if (query.isEmpty()) {
            resourcePackList.getItems().addAll(resourcePacksFull);
            return;
        }
        for (String item : resourcePacksFull) {
            if (item.toLowerCase().contains(query)) {
                resourcePackList.getItems().add(item);
            }
        }
    }

    private void filterSaves() {
        String query = saveSearch.getText().toLowerCase();
        saveList.getItems().clear();
        if (query.isEmpty()) {
            saveList.getItems().addAll(savesFull);
            return;
        }
        for (String item : savesFull) {
            if (item.toLowerCase().contains(query)) {
                saveList.getItems().add(item);
            }
        }
    }

    private void filterScreenshots() {
        String query = screenshotSearch.getText().toLowerCase();
        screenshotList.getItems().clear();
        if (query.isEmpty()) {
            screenshotList.getItems().addAll(screenshotsFull);
            return;
        }
        for (String item : screenshotsFull) {
            if (item.toLowerCase().contains(query)) {
                screenshotList.getItems().add(item);
            }
        }
    }

    @FXML
    private void backupWorld() {
        String selected = saveList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Selecione um save para fazer backup");
            return;
        }

        int index = saveList.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= savesFull.size())
            return;

        String worldName = savesFull.get(index);
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null)
            return;

        File gameDir = resolveGameDir(profile);
        File worldDir = new File(gameDir, "saves/" + worldName);
        if (!worldDir.exists()) {
            statusLabel.setText("Save não encontrado");
            return;
        }

        File backupDir = new File(gameDir, "backups");
        backupDir.mkdirs();

        String timestamp = java.time.LocalDateTime.now(BRAZIL_ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        File backupFile = new File(backupDir, worldName + "_" + timestamp + ".zip");

        statusLabel.setText("Fazendo backup de " + worldName + "...");
        progressBar.setProgress(-1);

        new Thread(() -> {
            try {
                zipDirectory(worldDir, backupFile);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("Backup criado: " + backupFile.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    statusLabel.setText("Erro no backup: " + e.getMessage());
                });
            }
        }).start();
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile))) {
            zipFiles(sourceDir, sourceDir, zos);
        }
    }

    private void zipFiles(File rootDir, File sourceFile, java.util.zip.ZipOutputStream zos) throws IOException {
        if (sourceFile.isDirectory()) {
            for (File child : sourceFile.listFiles()) {
                zipFiles(rootDir, child, zos);
            }
        } else {
            String entryName = rootDir.toPath().relativize(sourceFile.toPath()).toString();
            zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    @FXML
    private void showSettings() {
        showTab("settings");
    }

    private void showTab(String tab) {
        homePane.setVisible(tab.equals("home"));
        versionsPane.setVisible(tab.equals("versions"));
        modsPane.setVisible(tab.equals("mods"));
        resourcePacksPane.setVisible(tab.equals("resourcePacks"));
        savesPane.setVisible(tab.equals("saves"));
        screenshotsPane.setVisible(tab.equals("screenshots"));
        settingsPane.setVisible(tab.equals("settings"));

        setActiveNav(homeBtn, tab.equals("home"));
        setActiveNav(versionsBtn, tab.equals("versions"));
        setActiveNav(modsBtn, tab.equals("mods"));
        setActiveNav(resourcePacksBtn, tab.equals("resourcePacks"));
        setActiveNav(savesBtn, tab.equals("saves"));
        setActiveNav(screenshotsBtn, tab.equals("screenshots"));
        setActiveNav(settingsBtn, tab.equals("settings"));
    }

    private void setActiveNav(Button btn, boolean active) {
        if (btn == null)
            return;
        if (active) {
            if (!btn.getStyleClass().contains("active")) {
                btn.getStyleClass().add("active");
            }
            btn.setOpacity(1.0);
        } else {
            btn.getStyleClass().remove("active");
            btn.setOpacity(0.78);
        }
    }

    private void pulsePlayButton() {
        if (playButton == null || playButton.isDisabled())
            return;
        ScaleTransition st = new ScaleTransition(Duration.millis(1400), playButton);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.025);
        st.setToY(1.025);
        st.setAutoReverse(true);
        st.setCycleCount(ScaleTransition.INDEFINITE);
        st.play();
    }

    // ==================== LIVE UPDATES (clock · RAM · net) ====================

    private void startLiveUpdates() {
        // 1) Popula valores iniciais IMEDIATAMENTE
        updateClock();
        updateRam();
        checkNetAsync();
        debugLog("startLiveUpdates OK | label=" + statusClockLabel + " session=" + sessionTimeLabel);

        // 2) AnimationTimer — roda a 60fps na FX thread, é o timer mais confiável
        if (liveTimer == null) {
            liveTimer = new javafx.animation.AnimationTimer() {
                @Override
                public void handle(long now) {
                    // Clock + session time — a cada 1s
                    updateClock();

                    // RAM a cada 2s
                    if (now - lastRamUpdateNs >= 2_000_000_000L) {
                        updateRam();
                        lastRamUpdateNs = now;
                    }

                    // Net a cada 30s
                    if (now - lastNetUpdateNs >= 30_000_000_000L) {
                        checkNetAsync();
                        lastNetUpdateNs = now;
                    }
                }
            };
            liveTimer.start();
        }
    }

    private void updateClock() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(BRAZIL_ZONE);
        if (statusClockLabel != null) {
            // Relógio de parede: data + hora (fuso BRT)
            statusClockLabel.setText(now.format(TIME_FMT_FULL));
        }
        if (sessionTimeLabel != null) {
            // Tempo de sessão: formato compacto tipo "42s", "5m 12s", "1h 03m"
            sessionTimeLabel.setText(formatElapsedCompact(System.currentTimeMillis() - sessionStartMs));
        }
        if (++clockTickCounter % 100 == 0) {
            debugLog("tick #" + clockTickCounter + " clock=" + now.format(TIME_FMT));
        }
    }

    private static String formatElapsedCompact(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0)
            return String.format("%dh %02dm", h, m);
        if (m > 0)
            return String.format("%dm %02ds", m, sec);
        return sec + "s";
    }

    private void updateWelcomeHeader() {
        if (profileManager == null)
            return;
        LaunchProfile p = profileManager.getActiveProfile();
        if (p == null) {
            if (welcomeProfileLabel != null)
                welcomeProfileLabel.setText("—");
            if (welcomeVersionLabel != null)
                welcomeVersionLabel.setText("—");
            return;
        }
        if (welcomeProfileLabel != null) {
            welcomeProfileLabel.setText(p.getName().toUpperCase());
        }
        if (welcomeVersionLabel != null) {
            String loader = p.getModLoader();
            String version = p.getGameVersion();
            String text = (loader == null || "vanilla".equals(loader))
                    ? version
                    : version + " · " + loader.toUpperCase();
            welcomeVersionLabel.setText(text);
        }
    }

    private static void debugLog(String msg) {
        try (java.io.FileWriter fw = new java.io.FileWriter(
                "/tmp/launcher-clock.log", true)) {
            fw.write("[" + java.time.LocalDateTime.now() + "] " + msg + "\n");
        } catch (java.io.IOException ignored) {
        }
    }

    private void updateRam() {
        if (statusRamLabel == null)
            return;
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        statusRamLabel.setText(formatBytes(usedBytes) + " / " + formatBytes(maxBytes));
    }

    private void updateJavaInfo() {
        if (statusJavaLabel == null)
            return;
        statusJavaLabel.setText(String.valueOf(com.minelauncher.launcher.JavaDetector.getCurrentJavaVersion()));
    }

    private void checkNetAsync() {
        new Thread(() -> {
            boolean ok = pingMojang();
            netOnline = ok;
            Platform.runLater(this::renderNetLabel);
        }, "net-check").start();
    }

    private boolean pingMojang() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) java.net.URI
                    .create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json").toURL()
                    .openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private void renderNetLabel() {
        if (statusNetLabel == null)
            return;
        statusNetLabel.setText(netOnline ? "ONLINE" : "OFFLINE");
        swapClass(statusNetLabel, STATUS_VARIANTS, netOnline ? "status-text-accent" : "status-text-danger");
    }

    // ==================== STATE MACHINE ====================

    private void setState(LauncherState newState) {
        if (sessionStatusLabel == null)
            return;

        String text;
        String styleClass;
        switch (newState) {
            case BUSY:
                text = "● BUSY";
                styleClass = "session-chip-warm";
                break;
            case PLAYING:
                text = "● PLAYING";
                styleClass = "session-chip-cool";
                break;
            case ERROR:
                text = "● ERROR";
                styleClass = "session-chip-danger";
                break;
            default:
                text = "● READY";
                styleClass = "session-chip-accent";
        }
        sessionStatusLabel.setText(text);
        swapClass(sessionStatusLabel, SESSION_VARIANTS, styleClass);
    }

    public void setBusy() {
        setState(LauncherState.BUSY);
    }

    public void setError() {
        setState(LauncherState.ERROR);
    }

    public void setPlaying() {
        setState(LauncherState.PLAYING);
    }

    public void setReady() {
        setState(LauncherState.READY);
    }

    // ==================== UTILS ====================

    private void swapClass(Label l, java.util.List<String> variants, String target) {
        if (l == null)
            return;
        l.getStyleClass().removeAll(variants);
        if (target != null && !l.getStyleClass().contains(target)) {
            l.getStyleClass().add(target);
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 1024.0) {
            return (mb < 10 ? String.format("%.1f", mb) : String.format("%.0f", mb)) + " MB";
        }
        double gb = mb / 1024.0;
        return (gb < 10 ? String.format("%.2f", gb) : String.format("%.1f", gb)) + " GB";
    }

    public void stopLiveUpdates() {
        if (liveTimer != null) {
            liveTimer.stop();
            liveTimer = null;
        }
    }

    // ==================== HEADER ====================

    @FXML
    private void minimizeWindow() {
        stage.setIconified(true);
    }

    @FXML
    private void maximizeWindow() {
        boolean isMaximized = stage.isMaximized();
        stage.setMaximized(!isMaximized);
        
        // Ajustar estilos para maximizado
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

    @FXML
    private void closeWindow() {
        if (gameLauncher.isRunning()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Jogo em execução");
            alert.setHeaderText("O Minecraft está rodando");
            alert.setContentText("Deseja realmente fechar o launcher?");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
                return;
            gameLauncher.kill();
        }
        stopLiveUpdates();
        Platform.exit();
        System.exit(0);
    }

    // ==================== LOGIN ====================

    @FXML
    private void loginMicrosoft() {
        statusLabel.setText("Iniciando login Microsoft...");
        
        microsoftAuth.login(dcr -> {
            Platform.runLater(() -> {
                showDeviceCodeOverlay(dcr);
            });
        }).thenAccept(profile -> {
            Platform.runLater(() -> {
                SettingsManager.getInstance().addAccount(profile);
                refreshAccountCombo();
                accountCombo.setValue(profile.getName() + " (Microsoft)");
                statusLabel.setText("Login concluído: " + profile.getName());
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusLabel.setText("Erro no login: " + ex.getMessage());
                // Tentar fechar overlay se existir
                closeDeviceCodeOverlay();
            });
            return null;
        });
    }

    private StackPane deviceCodeOverlay;

    private void showDeviceCodeOverlay(com.minelauncher.auth.MicrosoftAuth.DeviceCodeResponse dcr) {
        if (contentPane == null) return;

        deviceCodeOverlay = new StackPane();
        deviceCodeOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        
        VBox card = new VBox(20);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setStyle("-fx-background-color: #121826; -fx-padding: 40; -fx-background-radius: 16; -fx-border-color: #2a3349; -fx-border-radius: 16;");
        card.setMaxSize(500, 350);

        Label title = new Label("LOGIN MICROSOFT");
        title.setStyle("-fx-text-fill: #9EFF8E; -fx-font-weight: 800; -fx-font-size: 18px; -fx-letter-spacing: 2px;");

        Label instructions = new Label("Acesse a URL abaixo e digite o código:");
        instructions.setStyle("-fx-text-fill: #c0c8d8; -fx-font-size: 14px;");

        TextField codeField = new TextField(dcr.user_code());
        codeField.setEditable(false);
        codeField.setAlignment(javafx.geometry.Pos.CENTER);
        codeField.setStyle("-fx-background-color: #1a2236; -fx-text-fill: #ffffff; -fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 28px; -fx-font-weight: 800; -fx-padding: 15; -fx-border-color: #2a3349; -fx-border-radius: 8;");

        Hyperlink urlLink = new Hyperlink(dcr.verification_uri());
        urlLink.setStyle("-fx-text-fill: #7DD3FC; -fx-font-size: 14px;");
        urlLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(dcr.verification_uri()));
            } catch (Exception ex) {
                LOG.error("Erro ao abrir navegador", ex);
            }
        });

        HBox actions = new HBox(15);
        actions.setAlignment(javafx.geometry.Pos.CENTER);

        Button copyBtn = new Button("Copiar Código");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(dcr.user_code());
            clipboard.setContent(content);
            copyBtn.setText("Copiado!");
        });

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("btn-ghost");
        cancelBtn.setOnAction(e -> closeDeviceCodeOverlay());

        actions.getChildren().addAll(copyBtn, cancelBtn);
        card.getChildren().addAll(title, instructions, codeField, urlLink, actions);
        deviceCodeOverlay.getChildren().add(card);
        
        contentPane.getChildren().add(deviceCodeOverlay);
    }

    private void closeDeviceCodeOverlay() {
        if (deviceCodeOverlay != null && contentPane != null) {
            contentPane.getChildren().remove(deviceCodeOverlay);
            deviceCodeOverlay = null;
        }
    }

    @FXML
    private void loginOffline() {
        TextInputDialog dialog = new TextInputDialog("Jogador");
        dialog.setTitle("Modo Offline");
        dialog.setHeaderText("Digite seu nome de jogador:");
        dialog.setContentText("Nome:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                GameProfile profile = OfflineAuth.createOfflineProfile(name.trim());
                SettingsManager.getInstance().addAccount(profile);
                refreshAccountCombo();
                accountCombo.setValue(profile.getName() + " (Offline)");
                statusLabel.setText("Conta offline criada: " + profile.getName());
            }
        });
    }

    @FXML
    private void removeAccount() {
        String selected = accountCombo.getValue();
        if (selected == null) {
            statusLabel.setText("Selecione uma conta para remover");
            return;
        }

        String accountName = selected.replace(" (Offline)", "").replace(" (Microsoft)", "").trim();
        for (GameProfile acc : SettingsManager.getInstance().getAccounts()) {
            if (acc.getName().equals(accountName)) {
                SettingsManager.getInstance().removeAccount(acc.getUuid());
                refreshAccountCombo();
                accountCombo.setValue(null);
                statusLabel.setText("Conta removida: " + accountName);
                break;
            }
        }
    }

    // ==================== INSTALAR VERSÃO ====================

    @FXML
    private void installVersion() {
        String selected = versionList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Selecione uma versão para instalar");
            return;
        }

        String versionId = selected.split(" \\[")[0]; // Remover [type]

        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    statusLabel.setText("Instalando " + versionId + "...");
                    progressBar.setProgress(-1); // Indeterminado
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
                    loadProfiles();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erro: " + e.getMessage());
                    progressBar.setProgress(0);
                });
            }
        }).start();
    }

    // ==================== INICIAR JOGO ====================

    @FXML
    private void launchGame() {
        GameProfile account = SettingsManager.getInstance().getSelectedAccount();
        if (account == null) {
            statusLabel.setText("Faça login primeiro!");
            setError();
            return;
        }

        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("Nenhum perfil selecionado!");
            setError();
            return;
        }

        // Sincronizar sliders da config com o perfil ativo
        minRamField.setText(String.valueOf(profile.getMinRam()));
        maxRamField.setText(String.valueOf(profile.getMaxRam()));

        playButton.setDisable(true);
        statusLabel.setText("Iniciando Minecraft...");
        setBusy();
        consoleArea.clear();

        new Thread(() -> {
            try {
                // 1. Baixar versão vanilla se necessário
                if (!versionManager.getInstalledVersions().contains(profile.getGameVersion())) {
                    Platform.runLater(
                            () -> statusLabel.setText("Baixando Minecraft " + profile.getGameVersion() + "..."));
                    versionManager.downloadVersion(profile.getGameVersion(), (msg, pct) -> {
                        Platform.runLater(() -> {
                            statusLabel.setText(msg);
                            progressBar.setProgress(pct);
                        });
                    });
                }

                // 2. Instalar mod loader se necessário
                String loader = profile.getModLoader();
                String loaderVersion = profile.getModLoaderVersion();
                if (loader != null && !"vanilla".equals(loader) && loaderVersion != null && !loaderVersion.isEmpty()) {
                    String versionId = gameLauncher.resolveVersionId(profile);
                    if (!versionManager.getInstalledVersions().contains(versionId)) {
                        Platform.runLater(
                                () -> statusLabel.setText("Instalando " + loader + " " + loaderVersion + "..."));
                        switch (loader) {
                            case "forge":
                                versionManager.installForge(profile.getGameVersion(), loaderVersion, (msg, pct) -> {
                                    Platform.runLater(() -> statusLabel.setText("Forge: " + msg));
                                });
                                break;
                            case "fabric":
                                versionManager.installFabric(profile.getGameVersion(), (msg, pct) -> {
                                    Platform.runLater(() -> statusLabel.setText("Fabric: " + msg));
                                });
                                break;
                            case "neoforge":
                                versionManager.installNeoForge(profile.getGameVersion(), loaderVersion, (msg, pct) -> {
                                    Platform.runLater(() -> statusLabel.setText("NeoForge: " + msg));
                                });
                                break;
                            case "quilt":
                                versionManager.installQuilt(profile.getGameVersion(), (msg, pct) -> {
                                    Platform.runLater(() -> statusLabel.setText("Quilt: " + msg));
                                });
                                break;
                        }
                    }
                }

                gameLauncher.launch(profile, account, line -> {
                    Platform.runLater(() -> {
                        consoleArea.appendText(line + "\n");
                        // Auto-scroll
                        consoleArea.setScrollTop(Double.MAX_VALUE);
                    });
                });

                Platform.runLater(() -> {
                    statusLabel.setText("Minecraft em execução!");
                    playButton.setText("Jogando...");
                    setPlaying();
                });

                // Aguardar fim do jogo
                new Thread(() -> {
                    try {
                        while (gameLauncher.isRunning()) {
                            Thread.sleep(1000);
                        }
                        Platform.runLater(() -> {
                            statusLabel.setText("Jogo encerrado");
                            playButton.setText("JOGAR");
                            playButton.setDisable(false);
                            progressBar.setProgress(0);
                            setReady();
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erro ao iniciar: " + e.getMessage());
                    playButton.setDisable(false);
                    consoleArea.appendText("ERRO: " + e.getMessage() + "\n");
                    setError();
                });
            }
        }).start();
    }

    // ==================== MODS ====================

    @FXML
    private void searchMods() {
        modActions.searchMods();
    }

    @FXML
    private void openModsFolder() {
        modActions.openModsFolder();
    }

    @FXML
    private void installSelected() {
        modActions.installSelected();
    }

    @FXML
    private void removeSelected() {
        modActions.removeSelected();
    }

    @FXML
    private void checkUpdates() {
        modActions.checkUpdates();
    }

    @FXML
    private void openProfileSettings() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("Nenhum perfil selecionado");
            return;
        }

        // Overlay backdrop
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.6);");
        overlay.setPickOnBounds(true);
        overlay.setVisible(true);

        // Card container
        VBox card = new VBox(20);
        card.setStyle(
                "-fx-background-color: #121826; -fx-background-radius: 16; -fx-border-color: #2a3349; -fx-border-radius: 16;");
        card.setPrefWidth(540);
        card.setMaxWidth(540);
        card.setMaxHeight(520);
        card.setEffect(new javafx.scene.effect.DropShadow(24, 0, 6, javafx.scene.paint.Color.rgb(0, 0, 0, 0.7)));

        String css = getClass().getResource("/css/dark-theme.css").toExternalForm();

        // Header
        HBox header = new HBox(12);
        header.setPadding(new javafx.geometry.Insets(18, 20, 0, 20));
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label title = new Label("Configurar: " + profile.getName());
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: 800;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Button closeBtn = new Button();
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-background-radius: 6; -fx-padding: 4; -fx-cursor: hand;");
        closeBtn.getStyleClass().add("window-btn");
        closeBtn.setGraphic(new javafx.scene.shape.SVGPath());
        ((javafx.scene.shape.SVGPath) closeBtn.getGraphic()).setContent("M6 6l12 12 M18 6L6 18");
        ((javafx.scene.shape.SVGPath) closeBtn.getGraphic())
                .setStyle("-fx-stroke: #9ba3b8; -fx-stroke-width: 1.8; -fx-fill: transparent;");
        closeBtn.setOnAction(e -> contentPane.getChildren().remove(overlay));
        header.getChildren().addAll(title, spacer, closeBtn);

        // Grid content
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new javafx.geometry.Insets(12, 20, 8, 20));
        grid.setStyle("-fx-background-color: #121826;");

        // RAM
        TextField ramField = new TextField(String.valueOf(profile.getMaxRam()));
        ramField.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e8ebf3; -fx-border-color: #2a3349; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-pref-width: 120;");
        Label ramUnit = new Label("MB");
        ramUnit.setStyle("-fx-text-fill: #5fcc6e; -fx-font-weight: bold; -fx-font-size: 13px;");
        HBox ramBox = new HBox(8, ramField, ramUnit);

        // Java Path
        TextField javaPathField = new TextField(profile.getJavaPath() != null ? profile.getJavaPath() : "");
        javaPathField.setPromptText("Auto-detectar");
        javaPathField.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e8ebf3; -fx-border-color: #2a3349; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");

        // JVM Args
        TextField jvmArgsField = new TextField(String.join(" ", profile.getJvmArgs()));
        jvmArgsField.setPromptText("Ex: -XX:+UseG1GC -XX:MaxGCPauseMillis=50");
        jvmArgsField.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e8ebf3; -fx-border-color: #2a3349; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12;");

        // Resolução
        TextField widthField = new TextField(String.valueOf(profile.getWidth()));
        widthField.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e8ebf3; -fx-border-color: #2a3349; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-pref-width: 80;");
        TextField heightField = new TextField(String.valueOf(profile.getHeight()));
        heightField.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #e8ebf3; -fx-border-color: #2a3349; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-pref-width: 80;");
        javafx.scene.control.CheckBox fullscreenCheck = new javafx.scene.control.CheckBox("Tela cheia");
        fullscreenCheck.setSelected(profile.isFullscreen());
        fullscreenCheck.setStyle("-fx-text-fill: #c0c8d8;");

        var fieldStyle = "-fx-text-fill: #9ba3b8; -fx-font-size: 13px;";
        var labelRam = new Label("RAM Máxima:");
        labelRam.setStyle(fieldStyle);
        var labelJava = new Label("Java Path:");
        labelJava.setStyle(fieldStyle);
        var labelJvm = new Label("JVM Args:");
        labelJvm.setStyle(fieldStyle);
        var labelW = new Label("Largura:");
        labelW.setStyle(fieldStyle);
        var labelH = new Label("Altura:");
        labelH.setStyle(fieldStyle);

        grid.add(labelRam, 0, 0);
        grid.add(ramBox, 1, 0);
        grid.add(labelJava, 0, 1);
        grid.add(javaPathField, 1, 1, 2, 1);
        grid.add(labelJvm, 0, 2);
        grid.add(jvmArgsField, 1, 2, 2, 1);
        grid.add(labelW, 0, 3);
        grid.add(widthField, 1, 3);
        grid.add(labelH, 0, 4);
        grid.add(heightField, 1, 4);
        grid.add(fullscreenCheck, 1, 5);

        // Action buttons
        HBox actions = new HBox(10);
        actions.setPadding(new javafx.geometry.Insets(4, 20, 18, 20));
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle(
                "-fx-background-color: #1a2236; -fx-text-fill: #c0c8d8; -fx-font-weight: 700; -fx-background-radius: 10; -fx-padding: 10 22; -fx-cursor: hand; -fx-border-color: #2a3349; -fx-border-radius: 10; -fx-font-size: 13px;");
        cancelBtn.setOnAction(e -> contentPane.getChildren().remove(overlay));

        Button saveBtn = new Button("Salvar");
        saveBtn.setStyle(
                "-fx-background-color: linear-gradient(to right, #5fcc6e, #2ea84a); -fx-text-fill: #0a0e1a; -fx-font-weight: 800; -fx-background-radius: 10; -fx-padding: 10 22; -fx-cursor: hand; -fx-font-size: 13px;");
        saveBtn.setOnAction(e -> {
            profile.setMaxRam(parseRam(ramField));
            String jp = javaPathField.getText().trim();
            profile.setJavaPath(jp.isEmpty() ? null : jp);
            String jvmArgs = jvmArgsField.getText().trim();
            profile.setJvmArgs(jvmArgs.isEmpty() ? List.of() : List.of(jvmArgs.split("\\s+")));
            try {
                profile.setWidth(Integer.parseInt(widthField.getText().trim()));
            } catch (NumberFormatException ex) {
                LOG.debug("Largura inválida: {}", widthField.getText());
            }
            try {
                profile.setHeight(Integer.parseInt(heightField.getText().trim()));
            } catch (NumberFormatException ex) {
                LOG.debug("Altura inválida: {}", heightField.getText());
            }
            profile.setFullscreen(fullscreenCheck.isSelected());
            profileManager.updateProfile(profile.getName(), profile);
            contentPane.getChildren().remove(overlay);
            statusLabel.setText("Configurações salvas para " + profile.getName());
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);

        card.getChildren().addAll(header, grid, actions);
        overlay.getChildren().add(card);
        contentPane.getChildren().add(overlay);

        // Aplicar CSS no overlay (para sliders, etc.)
        overlay.getStylesheets().add(css);
        card.getStylesheets().add(css);

        // Trazer overlay para frente
        overlay.toFront();
    }

    @FXML
    private void duplicateProfile() {
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null) {
            statusLabel.setText("Nenhum perfil selecionado");
            return;
        }

        String newName = profile.getName() + " (Cópia)";
        LaunchProfile copy = new LaunchProfile(newName, profile.getGameVersion());
        copy.setModLoader(profile.getModLoader());
        copy.setModLoaderVersion(profile.getModLoaderVersion());
        copy.setMinRam(profile.getMinRam());
        copy.setMaxRam(profile.getMaxRam());
        copy.setJavaPath(profile.getJavaPath());
        copy.setGameDir(profile.getGameDir());
        copy.setJvmArgs(new ArrayList<>(profile.getJvmArgs()));
        copy.setWidth(profile.getWidth());
        copy.setHeight(profile.getHeight());
        copy.setFullscreen(profile.isFullscreen());

        profileManager.addProfile(copy);
        loadProfiles();
        profileCombo.setValue(newName);
        statusLabel.setText("Perfil duplicado: " + newName);
    }

    @FXML
    private void importModpack() {
        modActions.importModpack();
    }

    public void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files)
                    deleteDirectory(f);
            }
        }
        dir.delete();
    }

    @FXML
    private void showInstalled() {
        modActions.showInstalled();
    }

    public void removeSelectedProfile() {
        if (profileManager == null || profileCombo == null)
            return;
        String name = profileCombo.getValue();
        if (name == null || name.isBlank()) {
            statusLabel.setText("Selecione um perfil para remover");
            return;
        }
        
        // Confirmação via dialog dark (assíncrono agora)
        com.minelauncher.ui.controllers.ModActions.showConfirmDialog(
                "Remover perfil",
                "Remover \"" + name + "\"?",
                "O perfil será apagado do MineLauncher. Se houver um modpack instalado com o mesmo nome, ele também será removido do disco.",
                ok -> {
                    if (ok) {
                        // Tenta remover o diretório do modpack se existir
                        java.io.File baseDir = com.minelauncher.settings.SettingsManager.getInstance().getBaseDir();
                        java.io.File modpackDir = new java.io.File(baseDir, "modpacks/" + name);
                        if (modpackDir.exists()) {
                            deleteDirectory(modpackDir);
                        }

                        // Remove o perfil
                        profileManager.removeProfile(name);
                        
                        // RECARREGA A UI
                        Platform.runLater(() -> {
                            loadProfiles();
                            updateWelcomeHeader();
                            statusLabel.setText("Perfil removido: " + name);
                        });
                    }
                }
        );
    }

    // ==================== SETTINGS ====================

    @FXML
    private void saveSettings() {
        SettingsManager settings = SettingsManager.getInstance();
        settings.setShowSnapshots(snapshotsCheck.isSelected());
        settings.setKeepLauncherOpen(keepOpenCheck.isSelected());
        settings.save();

        // Atualizar perfil ativo com RAM
        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile != null) {
            profile.setMinRam(parseRam(minRamField));
            profile.setMaxRam(parseRam(maxRamField));
            profileManager.updateProfile(profile.getName(), profile);
        }

        statusLabel.setText("Configurações salvas!");
    }
}
