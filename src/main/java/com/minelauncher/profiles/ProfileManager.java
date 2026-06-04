package com.minelauncher.profiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minelauncher.models.LaunchProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File baseDir;
    private final File profilesFile;
    private final List<LaunchProfile> profiles;
    private String activeProfileName;

    public ProfileManager(File baseDir) {
        this.baseDir = baseDir;
        // Usar arquivo separado para não conflitar com launcher oficial
        this.profilesFile = new File(baseDir, "mine_launcher_profiles.json");
        this.profiles = new ArrayList<>();
        load();
    }

    public void load() {
        // 1. Tentar carregar nossos perfis salvos
        if (profilesFile.exists()) {
            try {
                String json = Files.readString(profilesFile.toPath());
                SavedData data = GSON.fromJson(json, SavedData.class);
                if (data != null && data.profiles != null) {
                    profiles.clear();
                    profiles.addAll(data.profiles);
                    activeProfileName = data.selectedProfile;
                }
                if (activeProfileName == null && !profiles.isEmpty()) {
                    activeProfileName = profiles.get(0).getName();
                }
                LOG.info("Carregados {} perfis do MineLauncher", profiles.size());
                return;
            } catch (Exception e) {
                LOG.warn("Erro ao carregar perfis salvos, tentando importar do launcher oficial", e);
            }
        }

        // 2. Importar do launcher oficial (leitura, sem modificar)
        importFromOfficialLauncher();

        // 3. Se nada foi importado, criar perfil padrão
        if (profiles.isEmpty()) {
            LaunchProfile defaultProfile = new LaunchProfile("Default", "1.21");
            profiles.add(defaultProfile);
            activeProfileName = defaultProfile.getName();
        }

        save();
    }

    /**
     * Importa perfis do launcher oficial da Mojang (somente leitura)
     */
    private void importFromOfficialLauncher() {
        File officialFile = new File(baseDir, "launcher_profiles.json");
        if (!officialFile.exists()) return;

        try {
            String json = Files.readString(officialFile.toPath());
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonObject officialProfiles = root.getAsJsonObject("profiles");

            if (officialProfiles == null) return;

            for (Map.Entry<String, JsonElement> entry : officialProfiles.entrySet()) {
                JsonObject p = entry.getValue().getAsJsonObject();
                String name = p.has("name") ? p.get("name").getAsString() : "";
                if (name.isEmpty()) continue;

                String lastVersionId = p.has("lastVersionId") ? p.get("lastVersionId").getAsString() : "1.21";
                String gameDir = p.has("gameDir") ? p.get("gameDir").getAsString() : null;

                // Detectar mod loader pela versão
                String modLoader = "vanilla";
                String modLoaderVersion = null;
                if (lastVersionId.contains("neoforge")) {
                    modLoader = "neoforge";
                    // Tentar ler inheritsFrom do JSON da versão
                    String mcVer = readInheritsFrom(lastVersionId);
                    if (mcVer != null) {
                        modLoaderVersion = lastVersionId.replace("neoforge-", "");
                        lastVersionId = mcVer;
                    }
                } else if (lastVersionId.contains("forge")) {
                    modLoader = "forge";
                    String[] parts = lastVersionId.split("-forge-", 2);
                    if (parts.length > 1) modLoaderVersion = parts[1];
                    if (parts.length > 0) lastVersionId = parts[0];
                } else if (lastVersionId.contains("fabric-loader")) {
                    modLoader = "fabric";
                    String mcVer = readInheritsFrom(lastVersionId);
                    if (mcVer != null) {
                        modLoaderVersion = lastVersionId.replace("fabric-loader-", "");
                        lastVersionId = mcVer;
                    }
                } else if (lastVersionId.contains("quilt")) {
                    modLoader = "quilt";
                    String mcVer = readInheritsFrom(lastVersionId);
                    if (mcVer != null) {
                        modLoaderVersion = lastVersionId;
                        lastVersionId = mcVer;
                    }
                }

                LaunchProfile profile = new LaunchProfile(name, lastVersionId);
                profile.setModLoader(modLoader);
                profile.setModLoaderVersion(modLoaderVersion);
                profile.setGameDir(gameDir);

                // RAM do launcher oficial
                if (p.has("javaArgs")) {
                    String args = p.get("javaArgs").getAsString();
                    parseRamFromArgs(args, profile);
                }

                profiles.add(profile);
                LOG.info("Importado perfil do launcher oficial: {} ({})", name, lastVersionId);
            }

            // Perfil selecionado
            if (root.has("selectedProfile")) {
                activeProfileName = root.get("selectedProfile").getAsString();
            }
        } catch (Exception e) {
            LOG.warn("Não foi possível importar perfis do launcher oficial", e);
        }
    }

    /**
     * Lê o campo inheritsFrom do JSON de uma versão instalada
     */
    private String readInheritsFrom(String versionId) {
        try {
            File versionJson = new File(baseDir, "versions/" + versionId + "/" + versionId + ".json");
            if (!versionJson.exists()) return null;
            String json = Files.readString(versionJson.toPath());
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj.has("inheritsFrom")) {
                return obj.get("inheritsFrom").getAsString();
            }
        } catch (Exception e) {
            LOG.debug("Não foi possível ler inheritsFrom de {}", versionId);
        }
        return null;
    }

    private void parseRamFromArgs(String args, LaunchProfile profile) {
        // -Xmx4G ou -Xmx4096M
        for (String arg : args.split("\\s+")) {
            if (arg.startsWith("-Xmx")) {
                String val = arg.substring(4).toUpperCase();
                try {
                    if (val.endsWith("G")) {
                        profile.setMaxRam(Integer.parseInt(val.replace("G", "")) * 1024);
                    } else if (val.endsWith("M")) {
                        profile.setMaxRam(Integer.parseInt(val.replace("M", "")));
                    }
                } catch (NumberFormatException e) { LOG.debug("Valor de RAM inválido: {}", arg); }
            }
            if (arg.startsWith("-Xms")) {
                String val = arg.substring(4).toUpperCase();
                try {
                    if (val.endsWith("G")) {
                        profile.setMinRam(Integer.parseInt(val.replace("G", "")) * 1024);
                    } else if (val.endsWith("M")) {
                        profile.setMinRam(Integer.parseInt(val.replace("M", "")));
                    }
                } catch (NumberFormatException e) { LOG.debug("Valor de RAM inválido: {}", arg); }
            }
        }
    }

    public void save() {
        try {
            SavedData data = new SavedData();
            data.profiles = profiles;
            data.selectedProfile = activeProfileName;
            Files.writeString(profilesFile.toPath(), GSON.toJson(data));
        } catch (IOException e) {
            LOG.error("Erro ao salvar perfis", e);
        }
    }

    public List<LaunchProfile> getProfiles() { return Collections.unmodifiableList(profiles); }

    public LaunchProfile getActiveProfile() {
        return profiles.stream()
                .filter(p -> p.getName().equals(activeProfileName))
                .findFirst()
                .orElse(profiles.isEmpty() ? null : profiles.get(0));
    }

    public void setActiveProfile(String name) {
        this.activeProfileName = name;
        save();
    }

    public void addProfile(LaunchProfile profile) {
        profiles.add(profile);
        if (activeProfileName == null) activeProfileName = profile.getName();
        save();
        LOG.info("Perfil adicionado: {}", profile.getName());
    }

    public void removeProfile(String name) {
        profiles.removeIf(p -> p.getName().equals(name));
        if (name.equals(activeProfileName) && !profiles.isEmpty()) {
            activeProfileName = profiles.get(0).getName();
        }
        save();
        LOG.info("Perfil removido: {}", name);
    }

    public void updateProfile(String name, LaunchProfile updated) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getName().equals(name)) {
                profiles.set(i, updated);
                break;
            }
        }
        save();
    }

    private static class SavedData {
        public List<LaunchProfile> profiles = new ArrayList<>();
        public String selectedProfile;
    }
}
