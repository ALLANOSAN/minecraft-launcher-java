package com.minelauncher.ui.controllers;

import com.minelauncher.ui.services.AuthService;
import com.minelauncher.ui.services.BackupService;
import com.minelauncher.ui.services.VersionInstallationService;
import com.minelauncher.ui.services.GameLaunchService;
import com.minelauncher.ui.services.NavigationService;
import com.minelauncher.ui.services.LauncherStateService;
import static com.minelauncher.ui.services.LauncherStateService.LauncherState;
import com.minelauncher.ui.services.WindowService;
import com.minelauncher.launcher.GameLauncher;
import java.util.Map;
import com.minelauncher.mods.ModManager;
import com.minelauncher.models.ModInfo;
import com.minelauncher.launcher.VersionManager;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import com.minelauncher.skin.SkinData;
import com.minelauncher.skin.SkinManager;
import com.minelauncher.skin.SkinPreview3D;
import java.io.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private Label versionLabel;
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
    private Button skinsBtn;
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
    private ObservableList<String> allVersions;

    // Forge versions tab elements
    @FXML
    private VBox forgeSection;
    @FXML
    private Text forgeStatusText;
    @FXML
    private ListView<String> forgeVersionList;
    @FXML
    private Button installForgeBtn;
    private List<VersionManager.ForgeVersion> currentForgeVersions;

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

    // Skins / Aparência tab
    @FXML
    private VBox skinsPane;
    @FXML
    private StackPane skinPreviewHolder;
    @FXML
    private Label skinInfoLabel;
    @FXML
    private TextField skinNameField;
    @FXML
    private TextField skinUrlField;

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
    @FXML
    private TextField backupPathField;
    @FXML
    private Button browseBackupBtn;

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

    // Managers & Services
    @com.google.inject.Inject
    private ProfileManager profileManager;
    @com.google.inject.Inject
    private VersionManager versionManager;
    @com.google.inject.Inject
    private GameLauncher gameLauncher;
    @com.google.inject.Inject
    private ModManager modManager;
    @com.google.inject.Inject
    private ModActions modActions;
    @com.google.inject.Inject
    private AuthService authService;
    @com.google.inject.Inject
    private VersionInstallationService versionInstallationService;
    @com.google.inject.Inject
    private GameLaunchService gameLaunchService;
    @com.google.inject.Inject
    private NavigationService navigationService;
    @com.google.inject.Inject
    private LauncherStateService stateService;
    @com.google.inject.Inject
    private WindowService windowService;
    @com.google.inject.Inject
    private BackupService backupService;
    @com.google.inject.Inject
    private SkinManager skinManager;

    private SkinPreview3D skinPreview3D;
    private SkinData currentSkinData;

    // Lista de resultados da última busca
    private List<ModInfo> lastSearchResults = new ArrayList<>();

    // Listas-fonte para filtro local (resource packs, saves, screenshots)
    private final List<String> resourcePacksFull = new ArrayList<>();
    private final List<String> savesFull = new ArrayList<>();
    private final List<String> screenshotsFull = new ArrayList<>();

    // ── Live status / clock / RAM / net ──
    // QUAL-12: enum LauncherState removido daqui; agora vive em
    // LauncherStateService e é importado estaticamente.

    private volatile boolean netOnline = true;
    // NetMonitor é lazy-init (cria ao primeiro checkNetAsync). Encapsula a
    // thread/AtomicBoolean/lógica de ping que antes vivia aqui (H-1, H-9).
    private com.minelauncher.net.NetMonitor netMonitor;
    // StatusBarUpdater encapsula o AnimationTimer + clock/RAM/net (H-1)
    private StatusBarUpdater statusBarUpdater;

    // Fuso horário fixo do Brasil — não depende do default da JVM
    private static final java.time.ZoneId BRAZIL_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

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
        
        setupServices();
        modActions.setController(this);

        debugLog(">>> initialize() deps OK");
        setupUI();
        debugLog(">>> initialize() setupUI OK");
        setupDrag();
        loadProfiles();
        loadVersions();
        loadSavedAccount();
        // Auto-carregar skin da conta logada (se UUID disponível)
        loadAccountSkinAsync();

        // Listener: quando selecionar versão, carregar versões do Forge
        versionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String versionId = newVal.split(" \\[")[0];
                onVersionSelected(versionId);
            } else {
                forgeSection.setVisible(false);
                forgeSection.setManaged(false);
            }
        });
        backupPathField.setText(SettingsManager.getInstance().getBackupPath());
        debugLog(">>> initialize() FIM");
    }

    @FXML
    private void browseBackupPath() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Selecionar pasta de backup");
        java.io.File dir = chooser.showDialog(stage);
        if (dir != null) {
            String path = dir.getAbsolutePath();
            backupPathField.setText(path);
            SettingsManager.getInstance().setBackupPath(path);
        }
    }

    private void setupServices() {
        Map<String, Pane> panes = Map.of(
            "home", homePane, "versions", versionsPane, "mods", modsPane,
            "resourcePacks", resourcePacksPane, "saves", savesPane,
            "screenshots", screenshotsPane, "skins", skinsPane, "settings", settingsPane
        );
        Map<String, Button> navButtons = Map.of(
            "home", homeBtn, "versions", versionsBtn, "mods", modsBtn,
            "resourcePacks", resourcePacksBtn, "saves", savesBtn,
            "screenshots", screenshotsBtn, "skins", skinsBtn, "settings", settingsBtn
        );
        
        this.navigationService.setDependencies(panes, navButtons);
        this.stateService.setSessionStatusLabel(sessionStatusLabel);
        // BUG-8: windowService.setUI foi movido para setStage() — aqui
        // 'stage' ainda é null porque initialize() roda antes de setStage().
        this.versionInstallationService.setUI(statusLabel, progressBar);
        this.authService.setUI(
            this::showDeviceCodeOverlay,
            () -> {
                refreshAccountCombo();
                accountCombo.setValue(SettingsManager.getInstance().getSelectedAccount().getName() + " (Logado)");
                statusLabel.setText("Login concluído!");
            },
            err -> {
                statusLabel.setText("Erro no login: " + err);
                closeDeviceCodeOverlay();
            }
        );
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        // BUG-8: windowService.setUI agora é chamado aqui, depois que 'stage'
        // foi atribuído. Antes, era chamado dentro de initialize() com stage = null.
        this.windowService.setUI(stage, this::stopLiveUpdates);
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
                if (statusBarUpdater != null) statusBarUpdater.resetSessionTime();
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

        // Atualizar versão
        versionLabel.setText("v" + com.minelauncher.utils.AppConstants.APP_VERSION + " · CORE TERMINAL");

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
        javafx.scene.control.MenuItem installItem =
                com.minelauncher.ui.utils.JavaFxContextMenus.item("Instalar", this::installSelected);
        javafx.scene.control.MenuItem removeItem =
                com.minelauncher.ui.utils.JavaFxContextMenus.item("Remover", this::removeSelected);
        javafx.scene.control.MenuItem backupItem =
                com.minelauncher.ui.utils.JavaFxContextMenus.item("Backup save", this::backupWorld);
        javafx.scene.control.MenuItem openFolderItem =
                com.minelauncher.ui.utils.JavaFxContextMenus.item("Abrir pasta", this::openItemFolder);
        javafx.scene.control.MenuItem openScreenshot =
                com.minelauncher.ui.utils.JavaFxContextMenus.item("Abrir screenshot", this::openSelectedScreenshot);

        javafx.scene.control.ContextMenu contextMenu = com.minelauncher.ui.utils.JavaFxContextMenus.menu(
                installItem, removeItem, backupItem, openFolderItem, openScreenshot);

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
        com.minelauncher.ui.utils.JavaFxContextMenus.setupRamField(field, min, max, def);
    }

    private int parseRam(TextField field) {
        return com.minelauncher.ui.utils.JavaFxContextMenus.parseRam(field, 2048);
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
                com.minelauncher.ui.utils.DesktopUtil.open(targetDir);
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
                com.minelauncher.ui.utils.DesktopUtil.open(screenshot);
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
        // Coleta todos os nomes primeiro, depois seta de uma vez no ComboBox
        // para evitar race condition visual do JavaFX (bug onde o combo
        // mantém o valor antigo quando a lista é populada item a item).
        ObservableList<String> items = FXCollections.observableArrayList();
        for (LaunchProfile p : profileManager.getProfiles()) {
            items.add(p.getName());
        }
        profileCombo.setItems(items);

        LaunchProfile active = profileManager.getActiveProfile();
        if (active != null && items.contains(active.getName())) {
            profileCombo.getSelectionModel().select(active.getName());
        } else if (!items.isEmpty()) {
            profileCombo.getSelectionModel().selectFirst();
        }
        updateWelcomeHeader();
    }

    private void loadVersions() {
        new Thread(() -> {
            try {
                var manifest = versionManager.getVersionManifest();
                Platform.runLater(() -> {
                    allVersions = FXCollections.observableArrayList();
                    for (var v : manifest.getVersions()) {
                        allVersions.add(v.getId() + " [" + v.getType() + "]");
                    }
                    versionList.setItems(allVersions);

                    // Listener de busca — filtra a lista conforme o usuário digita
                    versionSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                        filterVersionList(newVal);
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Erro ao carregar versões: " + e.getMessage()));
            }
        }).start();
    }

    private void filterVersionList(String query) {
        if (allVersions == null) return;
        if (query == null || query.isBlank()) {
            versionList.setItems(allVersions);
            return;
        }
        String lowerQuery = query.toLowerCase();
        ObservableList<String> filtered = FXCollections.observableArrayList();
        for (String v : allVersions) {
            if (v.toLowerCase().contains(lowerQuery)) {
                filtered.add(v);
            }
        }
        versionList.setItems(filtered);
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

    // ── Skin / Aparência ──────────────────────────────────────

    @FXML
    private void showSkins() {
        showTab("skins");
        statusLabel.setText("Aparência — Skin Preview");
        if (skinPreview3D == null) {
            initSkinPreview();
        }
    }

    private void initSkinPreview() {
        if (skinPreviewHolder == null) return;
        Image defaultSkin = skinManager.getDefaultSkin();
        skinPreview3D = new SkinPreview3D(defaultSkin, 340, 380);
        skinPreviewHolder.getChildren().setAll(skinPreview3D.getSubScene());
        skinInfoLabel.setText("Skin padrão (Steve) carregada");
    }

    @FXML
    private void searchSkinByName() {
        String name = skinNameField.getText();
        if (name == null || name.trim().isEmpty()) return;
        statusLabel.setText("Buscando skin de " + name + "...");
        skinManager.fetchByUsername(name.trim()).thenAccept(skin -> {
            javafx.application.Platform.runLater(() -> {
                if (skin == null) {
                    statusLabel.setText("Jogador '" + name + "' não encontrado");
                    skinInfoLabel.setText("Jogador não encontrado. Verifique o nome.");
                    return;
                }
                applySkin(skin);
                statusLabel.setText("Skin de " + skin.getOwnerName() + " carregada");
            });
        });
    }

    @FXML
    private void loadSkinFromURL() {
        String url = skinUrlField.getText();
        if (url == null || url.trim().isEmpty()) return;
        statusLabel.setText("Baixando skin da URL...");
        skinManager.loadFromURL(url.trim()).thenAccept(skin -> {
            javafx.application.Platform.runLater(() -> {
                if (skin == null) {
                    statusLabel.setText("Falha ao carregar skin da URL");
                    skinInfoLabel.setText("URL inválida ou imagem não encontrada.");
                    return;
                }
                applySkin(skin);
                statusLabel.setText("Skin carregada da URL");
            });
        });
    }

    @FXML
    private void loadSkinFromFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Selecionar arquivo de skin");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Imagens PNG", "*.png"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        
        statusLabel.setText("Fazendo upload para MineSkin (aguarde)...");
        skinInfoLabel.setText("Processando...");
        
        skinManager.uploadToMineSkin(file.toPath()).thenAccept(skin -> {
            Platform.runLater(() -> {
                if (skin == null) {
                    statusLabel.setText("Falha no upload para MineSkin");
                    skinInfoLabel.setText("Erro ao processar skin.");
                    return;
                }
                applySkin(skin);
                statusLabel.setText("Skin processada e carregada: " + file.getName());
            });
        });
    }

    @FXML
    private void downloadCurrentSkin() {
        if (currentSkinData == null || currentSkinData.getImage() == null) {
            statusLabel.setText("Nenhuma skin carregada para baixar.");
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Salvar Skin");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PNG", "*.png"));
        chooser.setInitialFileName((currentSkinData.getOwnerName() != null ? currentSkinData.getOwnerName() : "skin") + ".png");
        
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        if (skinManager.exportSkin(currentSkinData, file.toPath())) {
            statusLabel.setText("Skin salva em: " + file.getName());
        } else {
            statusLabel.setText("Erro ao salvar skin.");
        }
    }

    /**
     * Aplica uma SkinData ao preview 3D e atualiza a label de info.
     */
    private void applySkin(SkinData skin) {
        if (skin == null || skin.getImage() == null) return;
        currentSkinData = skin;
        if (skinPreview3D == null) {
            initSkinPreview();
        }
        if (skinPreview3D != null) {
            skinPreview3D.updateSkin(skin.getImage());
        }
        skinInfoLabel.setText(skin.getDisplayLabel());
        // Se veio de busca por nome ou URL, limpa o campo
        if (skin.getSource() == SkinData.Source.MOJANG || skin.getSource() == SkinData.Source.NAMEMC) {
            skinNameField.clear();
        } else if (skin.getSource() == SkinData.Source.URL) {
            skinUrlField.clear();
        }
    }

    /**
     * Carrega a skin da conta atualmente logada (assíncrono).
     */
    private void loadAccountSkinAsync() {
        GameProfile account = SettingsManager.getInstance().getSelectedAccount();
        if (account == null || account.getUuid() == null) {
            LOG.debug("Sem conta logada com UUID — skin não será auto-carregada");
            return;
        }
        skinManager.fetchByUUID(account.getUuid()).thenAccept(skin -> {
            javafx.application.Platform.runLater(() -> {
                if (skin != null) {
                    applySkin(skin);
                    LOG.info("Skin de {} carregada automaticamente", skin.getOwnerName());
                }
            });
        });
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

        // BUG-3: o índice da lista filtrada não corresponde ao índice em savesFull.
        // Extrai o nome real do mundo a partir do item selecionado (descartando
        // o prefixo "[Save] ") e busca-o em savesFull. savesFull armazena
        // APENAS o nome do diretório (ver populateSaves), então selected é
        // sempre "[Save] <worldName>" e o replace extrai o nome limpo.
        String worldName = selected.replace("[Save] ", "").trim();
        if (!savesFull.contains(worldName)) {
            statusLabel.setText("Save não encontrado na lista");
            return;
        }
        final String resolvedWorldName = worldName;

        LaunchProfile profile = profileManager.getActiveProfile();
        if (profile == null)
            return;

        File gameDir = resolveGameDir(profile);
        File worldDir = new File(gameDir, "saves/" + worldName);
        if (!worldDir.exists()) {
            statusLabel.setText("Save não encontrado");
            return;
        }

        // BUG-6: pasta de destino dos snapshots (mesma convenção de antes,
        // gameDir/backups/). O nome do snapshot é gerado dentro do BackupService.
        File backupDir = new File(gameDir, "backups");
        backupDir.mkdirs();
        final File resolvedGameDir = gameDir;
        final File resolvedBackupDir = backupDir;

        statusLabel.setText("Fazendo backup de " + worldName + "...");
        progressBar.setProgress(-1);

        new Thread(() -> {
            try {
                // BUG-6: usa o BackupService (injetado) em vez de zipDirectory/zipFiles
                // duplicados. Assinatura: (worldName, gameDir, backupBaseDir).
                backupService.createSnapshot(resolvedWorldName, resolvedGameDir, resolvedBackupDir);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("Backup criado para " + resolvedWorldName);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    statusLabel.setText("Erro no backup: " + e.getMessage());
                });
            }
        }).start();
    }

    // BUG-6: zipDirectory/zipFiles removidos — backup agora é responsabilidade
    // exclusiva do BackupService injetado, evitando dois mecanismos paralelos
    // (zip vs. cópia de diretório) com comportamentos divergentes.

    @FXML
    private void showSettings() {
        showTab("settings");
    }

    // BUG-2: showTab() agora é um wrapper fino que delega ao NavigationService.
    // A lógica duplicada de visibilidade/active class foi removida.
    private void showTab(String tab) {
        navigationService.navigate(tab);
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
    // H-1: extraído para StatusBarUpdater

    private void startLiveUpdates() {
        if (statusBarUpdater == null) {
            statusBarUpdater = new StatusBarUpdater(
                    statusClockLabel, sessionTimeLabel, statusRamLabel, statusNetLabel,
                    netMonitor, this::checkNetAsync, profileManager);
        }
        statusBarUpdater.start();
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

    // FIX C-7: debugLog reescrito para usar SLF4J em vez de FileWriter em
    // /tmp/launcher-clock.log. O método antigo causava I/O síncrono a cada tick
    // do AnimationTimer (60 fps) e falhava silenciosamente em Windows/paths imutáveis.
    private static void debugLog(String msg) {
        LOG.debug(msg);
    }

    private void updateJavaInfo() {
        if (statusJavaLabel == null)
            return;
        statusJavaLabel.setText(String.valueOf(com.minelauncher.launcher.JavaDetector.getCurrentJavaVersion()));
    }

    private void checkNetAsync() {
        // Delegado para NetMonitor (H-1, H-9): thread-safe, daemon, sem
        // duplicação de lógica de ping/timeout. Re-entrância protegida.
        if (netMonitor == null) {
            netMonitor = new com.minelauncher.net.NetMonitor(() -> {
                netOnline = netMonitor.isOnline();
                Platform.runLater(this::renderNetLabel);
            });
        }
        netMonitor.checkAsync();
    }

    private void renderNetLabel() {
        if (statusNetLabel == null) return;
        if (statusBarUpdater != null) {
            statusBarUpdater.updateNetLabel(netOnline);
        } else {
            statusNetLabel.setText(netOnline ? "ONLINE" : "OFFLINE");
        }
        com.minelauncher.ui.utils.JavaFxUtils.swapClass(statusNetLabel, STATUS_VARIANTS, netOnline ? "status-text-accent" : "status-text-danger");
    }

    // ==================== STATE MACHINE ====================

    // QUAL-12: setState() delega para LauncherStateService, que é o dono
    // legítimo da enum e da lógica de estilo/texto. Evita a duplicação
    // que existia entre controller e service.
    private void setState(LauncherState newState) {
        if (sessionStatusLabel == null) return;
        stateService.setState(newState);
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

    public void stopLiveUpdates() {
        if (statusBarUpdater != null) {
            statusBarUpdater.stop();
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
        authService.loginMicrosoft();
    }

    private StackPane deviceCodeOverlay;

    private void showDeviceCodeOverlay(com.minelauncher.auth.MicrosoftAuth.DeviceCodeResponse dcr) {
        if (contentPane == null) return;

        deviceCodeOverlay = new StackPane();
        deviceCodeOverlay.getStyleClass().add("device-code-overlay");

        VBox card = new VBox(20);
        card.getStyleClass().add("device-code-card");
        // QUAL-16: setAlignment e setMaxSize foram movidos para CSS.

        Label title = new Label("LOGIN MICROSOFT");
        title.getStyleClass().add("device-code-title");

        Label instructions = new Label("Acesse a URL abaixo e digite o código:");
        instructions.getStyleClass().add("device-code-instructions");

        TextField codeField = new TextField(dcr.user_code());
        codeField.setEditable(false);
        codeField.getStyleClass().add("device-code-field");
        // QUAL-16: setAlignment movido para CSS.

        Hyperlink urlLink = new Hyperlink(dcr.verification_uri());
        urlLink.getStyleClass().add("device-code-link");
        urlLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(dcr.verification_uri()));
            } catch (Exception ex) {
                LOG.error("Erro ao abrir navegador", ex);
            }
        });

        HBox actions = new HBox(15);
        actions.getStyleClass().add("device-code-actions");

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
            authService.loginOffline(name);
            refreshAccountCombo();
            accountCombo.setValue(SettingsManager.getInstance().getSelectedAccount().getName() + " (Offline)");
            statusLabel.setText("Conta offline criada: " + name);
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

        String versionId = selected.split(" \\[")[0];

        // Se tiver uma versão do Forge selecionada, instalar Forge + vanilla
        int forgeIdx = forgeVersionList.getSelectionModel().getSelectedIndex();
        boolean hasForge = forgeIdx >= 0 && currentForgeVersions != null && forgeIdx < currentForgeVersions.size();
        if (hasForge) {
            installForgeVersion();
            return;
        }

        setBusy();
        statusLabel.setText("Instalando " + versionId + "...");
        
        versionInstallationService.installVersion(versionId, 
            () -> {
                // Criar perfil automaticamente para versão vanilla
                String profileName = versionId;
                // Evitar duplicata
                boolean exists = profileManager.getProfiles().stream()
                        .anyMatch(p -> p.getName().equals(profileName));
                if (!exists) {
                    LaunchProfile profile = new LaunchProfile(profileName, versionId);
                    profileManager.addProfile(profile);
                    profileManager.setActiveProfile(profileName);
                }
                loadProfiles();
                statusLabel.setText("Versão " + versionId + " instalada! Perfil criado.");
                setReady();
            },
            e -> {
                LOG.error("Erro na instalação", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Erro: " + e.getMessage());
                    setReady();
                });
            }
        );
    }

    /**
     * Quando uma versão é selecionada, carrega as versões do Forge disponíveis.
     */
    private void onVersionSelected(String versionId) {
        // Só buscar Forge para versões vanilla (não pra algo como "1.21.4 [release]")
        if (!versionId.matches("\\d+(\\.\\d+){1,2}")) {
            forgeSection.setVisible(false);
            forgeSection.setManaged(false);
            return;
        }

        new Thread(() -> {
            try {
                List<VersionManager.ForgeVersion> forgeVersions = versionManager.getForgeVersions(versionId);
                Platform.runLater(() -> {
                    currentForgeVersions = forgeVersions;
                    forgeVersionList.getItems().clear();
                    if (forgeVersions.isEmpty()) {
                        forgeSection.setVisible(false);
                        forgeSection.setManaged(false);
                        return;
                    }
                    forgeSection.setVisible(true);
                    forgeSection.setManaged(true);
                    for (VersionManager.ForgeVersion fv : forgeVersions) {
                        String display = fv.forgeVersion;
                        if (fv.recommended) {
                            display = "★ " + display + " (recomendada)";
                        } else if (fv.latest) {
                            display = "☆ " + display + " (latest)";
                        }
                        forgeVersionList.getItems().add(display);
                    }
                    forgeStatusText.setText(forgeVersions.size() + " versão(ões) do Forge encontrada(s) para " + versionId);
                    installForgeBtn.setText("Instalar Vanilla (" + versionId + ")");
                    installForgeBtn.setDisable(false);
                });
            } catch (Exception e) {
                LOG.warn("Erro ao carregar versões do Forge para {}: {}", versionId, e.getMessage());
                Platform.runLater(() -> {
                    forgeSection.setVisible(false);
                    forgeSection.setManaged(false);
                });
            }
        }).start();
    }

    /**
     * Instala apenas a versão vanilla, ignorando qualquer seleção de Forge.
     * Chamado pelo botão "Instalar Vanilla" na seção de versões do Forge.
     */
    @FXML
    private void installVanillaOnly() {
        String selected = versionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String mcVersion = selected.split(" \\[")[0];

        setBusy();
        new Thread(() -> {
            try {
                Platform.runLater(() -> statusLabel.setText("Instalando " + mcVersion + " (vanilla)..."));
                versionInstallationService.installVersion(mcVersion,
                    () -> {
                        String profileName = mcVersion;
                        boolean exists = profileManager.getProfiles().stream()
                                .anyMatch(p -> p.getName().equals(profileName));
                        if (!exists) {
                            LaunchProfile profile = new LaunchProfile(profileName, mcVersion);
                            profileManager.addProfile(profile);
                            profileManager.setActiveProfile(profileName);
                        }
                        loadProfiles();
                        Platform.runLater(() -> {
                            statusLabel.setText("Versão " + mcVersion + " instalada! Perfil criado.");
                            setReady();
                        });
                    },
                    e -> {
                        LOG.error("Erro na instalação vanilla", e);
                        Platform.runLater(() -> {
                            statusLabel.setText("Erro: " + e.getMessage());
                            setReady();
                        });
                    }
                );
            } catch (Exception e) {
                LOG.error("Erro na instalação", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Erro: " + e.getMessage());
                    setReady();
                });
            }
        }).start();
    }

    /**
     * Instala Forge + vanilla (chamado pelo {@link #installVersion()} quando
     * uma versão do Forge está selecionada na lista de Forge).
     */
    private void installForgeVersion() {
        String selected = versionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String mcVersion = selected.split(" \\[")[0];
        int forgeIdx = forgeVersionList.getSelectionModel().getSelectedIndex();
        boolean hasForge = forgeIdx >= 0 && currentForgeVersions != null && forgeIdx < currentForgeVersions.size();
        if (!hasForge) return;

        setBusy();
        new Thread(() -> {
            try {
                // Instalar Forge
                VersionManager.ForgeVersion fv = currentForgeVersions.get(forgeIdx);
                String forgeVer = fv.forgeVersion;

                Platform.runLater(() -> statusLabel.setText("Instalando " + mcVersion + " + Forge " + forgeVer + "..."));

                // 1. Baixar vanilla primeiro
                versionManager.downloadVersion(mcVersion, (msg, pct) -> 
                    Platform.runLater(() -> {
                        statusLabel.setText("Vanilla: " + msg);
                        if (pct >= 0) progressBar.setProgress(pct * 0.7);
                    })
                );

                // 2. Instalar Forge
                versionManager.installForge(mcVersion, forgeVer, (msg, pct) ->
                    Platform.runLater(() -> {
                        statusLabel.setText("Forge: " + msg);
                        if (pct >= 0) progressBar.setProgress(0.7 + pct * 0.3);
                    })
                );

                // 3. Criar perfil
                Platform.runLater(() -> {
                    String profileName = mcVersion + " (Forge " + forgeVer + ")";
                    boolean exists = profileManager.getProfiles().stream()
                            .anyMatch(p -> p.getName().equals(profileName));
                    if (!exists) {
                        LaunchProfile profile = new LaunchProfile(profileName, mcVersion);
                        profile.setModLoader("forge");
                        profile.setModLoaderVersion(forgeVer);
                        profileManager.addProfile(profile);
                        profileManager.setActiveProfile(profileName);
                    }
                    loadProfiles();
                    statusLabel.setText("Forge " + mcVersion + "-" + forgeVer + " instalado! Perfil criado.");
                    progressBar.setProgress(1.0);
                    setReady();
                });
            } catch (Exception e) {
                LOG.error("Erro na instalação", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Erro: " + e.getMessage());
                    setReady();
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

        minRamField.setText(String.valueOf(profile.getMinRam()));
        maxRamField.setText(String.valueOf(profile.getMaxRam()));

        playButton.setDisable(true);
        statusLabel.setText("Iniciando Minecraft...");
        setBusy();
        consoleArea.clear();

        gameLaunchService.launch(profile, account,
            msg -> Platform.runLater(() -> statusLabel.setText(msg)),
            (msg, pct) -> Platform.runLater(() -> { statusLabel.setText(msg); progressBar.setProgress(pct); }),
            line -> Platform.runLater(() -> { consoleArea.appendText(line + "\n"); consoleArea.setScrollTop(Double.MAX_VALUE); }),
            () -> Platform.runLater(() -> {
                statusLabel.setText("Minecraft em execução!");
                playButton.setText("Jogando...");
                setPlaying();
            }),
            () -> Platform.runLater(() -> {
                statusLabel.setText("Jogo encerrado");
                playButton.setText("JOGAR");
                playButton.setDisable(false);
                progressBar.setProgress(0);
                setReady();
            }),
            e -> Platform.runLater(() -> {
                statusLabel.setText("Erro ao iniciar: " + e.getMessage());
                playButton.setDisable(false);
                consoleArea.appendText("ERRO: " + e.getMessage() + "\n");
                setError();
            })
        );
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

        String css = getClass().getResource("/css/dark-theme.css").toExternalForm();

        // QUAL-14: estilos movidos do Java para CSS (classes .profile-settings-*).
        // Mantém a montagem dos nós no controller (lógica) e o visual no CSS (apresentação).

        // Overlay backdrop
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("profile-settings-overlay");
        overlay.setPickOnBounds(true);
        overlay.setVisible(true);

        // Card container
        VBox card = new VBox(20);
        card.getStyleClass().add("profile-settings-card");
        // QUAL-16: setPrefWidth/setMaxWidth/setMaxHeight movidos para CSS.

        // Header
        HBox header = new HBox(12);
        header.getStyleClass().add("profile-settings-header");
        // QUAL-16: setAlignment movido para CSS.
        Label title = new Label("Configurar: " + profile.getName());
        title.getStyleClass().add("profile-settings-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Button closeBtn = new Button();
        closeBtn.getStyleClass().addAll("window-btn", "profile-settings-close-btn");
        closeBtn.setGraphic(new javafx.scene.shape.SVGPath());
        ((javafx.scene.shape.SVGPath) closeBtn.getGraphic())
                .setContent("M6 6l12 12 M18 6L6 18");
        ((javafx.scene.shape.SVGPath) closeBtn.getGraphic()).getStyleClass().add("svg-path");
        closeBtn.setOnAction(e -> contentPane.getChildren().remove(overlay));
        header.getChildren().addAll(title, spacer, closeBtn);

        // Grid content
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.getStyleClass().addAll("profile-settings-grid", "profile-settings-grid-padded");
        // QUAL-14: setHgap e setVgap movidos para CSS (.profile-settings-grid).

        // RAM
        TextField ramField = new TextField(String.valueOf(profile.getMaxRam()));
        ramField.getStyleClass().addAll("profile-settings-field", "profile-settings-field-ram");
        Label ramUnit = new Label("MB");
        ramUnit.getStyleClass().add("profile-settings-unit");
        HBox ramBox = new HBox(8, ramField, ramUnit);

        // Java Path
        TextField javaPathField = new TextField(profile.getJavaPath() != null ? profile.getJavaPath() : "");
        javaPathField.setPromptText("Auto-detectar");
        javaPathField.getStyleClass().add("profile-settings-field");

        // JVM Args
        TextField jvmArgsField = new TextField(String.join(" ", profile.getJvmArgs()));
        jvmArgsField.setPromptText("Ex: -XX:+UseG1GC -XX:MaxGCPauseMillis=50");
        jvmArgsField.getStyleClass().add("profile-settings-field");

        // Resolução
        TextField widthField = new TextField(String.valueOf(profile.getWidth()));
        widthField.getStyleClass().addAll("profile-settings-field", "profile-settings-field-narrow");
        TextField heightField = new TextField(String.valueOf(profile.getHeight()));
        heightField.getStyleClass().addAll("profile-settings-field", "profile-settings-field-narrow");
        javafx.scene.control.CheckBox fullscreenCheck = new javafx.scene.control.CheckBox("Tela cheia");
        fullscreenCheck.setSelected(profile.isFullscreen());
        fullscreenCheck.getStyleClass().add("profile-settings-checkbox");

        var labelRam = new Label("RAM Máxima:");
        labelRam.getStyleClass().add("profile-settings-label");
        var labelJava = new Label("Java Path:");
        labelJava.getStyleClass().add("profile-settings-label");
        var labelJvm = new Label("JVM Args:");
        labelJvm.getStyleClass().add("profile-settings-label");
        var labelW = new Label("Largura:");
        labelW.getStyleClass().add("profile-settings-label");
        var labelH = new Label("Altura:");
        labelH.getStyleClass().add("profile-settings-label");

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
        actions.getStyleClass().addAll("profile-settings-actions", "profile-settings-actions-padded");

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("profile-settings-btn-cancel");
        cancelBtn.setOnAction(e -> contentPane.getChildren().remove(overlay));

        Button saveBtn = new Button("Salvar");
        saveBtn.getStyleClass().add("profile-settings-btn-save");
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
                            com.minelauncher.utils.FileUtils.deleteDirectory(modpackDir);
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
