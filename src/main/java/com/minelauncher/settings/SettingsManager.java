package com.minelauncher.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.minelauncher.models.GameProfile;
import com.minelauncher.utils.SecretCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;

public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);
    private static final SettingsManager INSTANCE = new SettingsManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ACCOUNTS_TYPE = new TypeToken<List<GameProfile>>(){}.getType();

    private final File baseDir;
    private final File settingsFile;
    private final File accountsFile;

    // Configurações
    private String selectedAccountUuid;
    private List<GameProfile> accounts = new ArrayList<>();
    private String theme = "dark";
    private boolean keepLauncherOpen = true;
    private boolean showSnapshots = false;
    private int downloadThreads = 8;
    private String language = "pt_BR";

    private SettingsManager() {
        String os = System.getProperty("os.name").toLowerCase();
        String appData;

        if (os.contains("win")) {
            appData = System.getenv("APPDATA");
            if (appData == null) appData = System.getProperty("user.home");
            baseDir = new File(appData, ".minecraft");
        } else if (os.contains("mac")) {
            baseDir = new File(System.getProperty("user.home"),
                    "Library/Application Support/minecraft");
        } else {
            baseDir = new File(System.getProperty("user.home"), ".minecraft");
        }

        baseDir.mkdirs();
        settingsFile = new File(baseDir, "launcher_settings.json");
        accountsFile = new File(baseDir, "launcher_accounts.json");

        LOG.info("Diretório base: {}", baseDir.getAbsolutePath());
    }

    public static SettingsManager getInstance() { return INSTANCE; }

    public void load() {
        // Carregar configurações
        if (settingsFile.exists()) {
            try {
                String json = Files.readString(settingsFile.toPath());
                SettingsData data = GSON.fromJson(json, SettingsData.class);
                if (data != null) {
                    selectedAccountUuid = data.selectedAccountUuid;
                    theme = data.theme != null ? data.theme : "dark";
                    keepLauncherOpen = data.keepLauncherOpen;
                    showSnapshots = data.showSnapshots;
                    downloadThreads = data.downloadThreads > 0 ? data.downloadThreads : 8;
                    language = data.language != null ? data.language : "pt_BR";
                }
            } catch (Exception e) {
                LOG.error("Erro ao carregar configurações", e);
            }
        }

        // Carregar contas
        if (accountsFile.exists()) {
            try {
                String json = Files.readString(accountsFile.toPath());
                List<GameProfile> loaded = GSON.fromJson(json, ACCOUNTS_TYPE);
                if (loaded != null) {
                    // FIX C-3: decifra tokens que estejam no formato "enc:..."
                    // Plain text legado é retornado como está (migração transparente)
                    for (GameProfile profile : loaded) {
                        decryptInPlace(profile);
                    }
                    accounts = loaded;
                }
            } catch (Exception e) {
                LOG.error("Erro ao carregar contas", e);
            }
        }

        LOG.info("Configurações carregadas ({} conta(s))", accounts.size());
    }

    public synchronized void save() {
        try {
            // Salvar configurações
            SettingsData data = new SettingsData();
            data.selectedAccountUuid = selectedAccountUuid;
            data.theme = theme;
            data.keepLauncherOpen = keepLauncherOpen;
            data.showSnapshots = showSnapshots;
            data.downloadThreads = downloadThreads;
            data.language = language;
            Files.writeString(settingsFile.toPath(), GSON.toJson(data));

            // FIX C-3: cifrar tokens antes de serializar contas.
            // Itera sobre uma cópia pra não mutar a lista em memória do usuário.
            // O arquivo em disco fica ilegível sem o SecretCodec.
            List<GameProfile> toPersist = new ArrayList<>(accounts.size());
            for (GameProfile profile : accounts) {
                toPersist.add(encryptedCopy(profile));
            }
            Files.writeString(accountsFile.toPath(), GSON.toJson(toPersist));
        } catch (IOException e) {
            LOG.error("Erro ao salvar configurações", e);
        }
    }

    // =============== Token cipher helpers (FIX C-3) ===============

    /**
     * Cifra os tokens de um profile in-place e retorna o mesmo objeto.
     * Usado antes de serializar para JSON.
     */
    private static GameProfile encryptedCopy(GameProfile src) {
        if (src == null) return null;
        GameProfile copy = new GameProfile();
        copy.setName(src.getName());
        copy.setUuid(src.getUuid());
        copy.setMicrosoft(src.isMicrosoft());
        copy.setOffline(src.isOffline());
        copy.setTokenExpiry(src.getTokenExpiry());
        copy.setAccessToken(encryptField(src.getAccessToken()));
        copy.setRefreshToken(encryptField(src.getRefreshToken()));
        copy.setXblToken(encryptField(src.getXblToken()));
        copy.setXstsToken(encryptField(src.getXstsToken()));
        return copy;
    }

    private static void decryptInPlace(GameProfile profile) {
        if (profile == null) return;
        profile.setAccessToken(SecretCodec.decrypt(profile.getAccessToken()));
        profile.setRefreshToken(SecretCodec.decrypt(profile.getRefreshToken()));
        profile.setXblToken(SecretCodec.decrypt(profile.getXblToken()));
        profile.setXstsToken(SecretCodec.decrypt(profile.getXstsToken()));
    }

    private static String encryptField(String value) {
        if (value == null || value.isEmpty()) return value;
        return "enc:" + SecretCodec.encrypt(value);
    }

    // =============== Getters / Setters ===============

    public File getBaseDir() { return baseDir; }
    public String getSelectedAccountUuid() { return selectedAccountUuid; }
    public void setSelectedAccountUuid(String uuid) { this.selectedAccountUuid = uuid; save(); }
    public List<GameProfile> getAccounts() { return Collections.unmodifiableList(accounts); }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; save(); }
    public boolean isKeepLauncherOpen() { return keepLauncherOpen; }
    public void setKeepLauncherOpen(boolean keep) { this.keepLauncherOpen = keep; save(); }
    public boolean isShowSnapshots() { return showSnapshots; }
    public void setShowSnapshots(boolean show) { this.showSnapshots = show; save(); }
    public int getDownloadThreads() { return downloadThreads; }
    public void setDownloadThreads(int threads) { this.downloadThreads = threads; save(); }
    public String getLanguage() { return language; }
    public void setLanguage(String lang) { this.language = lang; save(); }

    public void addAccount(GameProfile profile) {
        accounts.removeIf(a -> a.getUuid().equals(profile.getUuid()));
        accounts.add(profile);
        selectedAccountUuid = profile.getUuid().toString();
        save();
        LOG.info("Conta adicionada: {}", profile.getName());
    }

    public void removeAccount(UUID uuid) {
        accounts.removeIf(a -> a.getUuid().equals(uuid));
        if (accounts.isEmpty()) {
            selectedAccountUuid = null;
        } else {
            selectedAccountUuid = accounts.get(0).getUuid().toString();
        }
        save();
    }

    public GameProfile getSelectedAccount() {
        if (selectedAccountUuid == null && !accounts.isEmpty()) {
            return accounts.get(0);
        }
        return accounts.stream()
                .filter(a -> a.getUuid().toString().equals(selectedAccountUuid))
                .findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    private static class SettingsData {
        public String selectedAccountUuid;
        public String theme;
        public boolean keepLauncherOpen;
        public boolean showSnapshots;
        public int downloadThreads;
        public String language;
    }
}
