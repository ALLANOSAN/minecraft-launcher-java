package com.minelauncher.profiles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelauncher.models.LaunchProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File profilesFile;
    private final List<LaunchProfile> profiles;
    private String activeProfileName;

    public ProfileManager(File baseDir) {
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
                LOG.warn("Erro ao carregar perfis salvos", e);
            }
        }

        // 2. Se nada foi carregado, criar perfil padrão
        // Removido o importFromOfficialLauncher() automático para evitar re-importar perfis deletados.
        if (profiles.isEmpty()) {
            LaunchProfile defaultProfile = new LaunchProfile("Default", "1.21");
            profiles.add(defaultProfile);
            activeProfileName = defaultProfile.getName();
        }

        save();
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
        if (name == null) return;
        LOG.info("Tentando remover perfil: '{}'. Tamanho atual da lista: {}", name, profiles.size());
        
        boolean removed = profiles.removeIf(p -> {
            boolean match = p.getName() != null && p.getName().trim().equalsIgnoreCase(name.trim());
            if (match) LOG.info("Match encontrado para remoção: '{}'", p.getName());
            return match;
        });

        if (removed) {
            LOG.info("Perfil removido da memória. Novo tamanho da lista: {}", profiles.size());
            if (name.equalsIgnoreCase(activeProfileName) && !profiles.isEmpty()) {
                activeProfileName = profiles.get(0).getName();
            }
            save();
            LOG.info("Comando save() executado após remoção de: {}", name);
        } else {
            LOG.warn("Nenhum perfil removido para o nome '{}'. Perfis atuais na lista: {}", 
                name, profiles.stream().map(LaunchProfile::getName).collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    public void removeProfilesByDir(String directory) {
        if (directory == null || directory.isBlank()) return;
        try {
            File targetFile = new File(directory);
            String targetPath = targetFile.getCanonicalPath();
            
            boolean removed = profiles.removeIf(p -> {
                if (p.getGameDir() == null || p.getGameDir().isBlank()) return false;
                try {
                    return new File(p.getGameDir()).getCanonicalPath().equals(targetPath);
                } catch (IOException e) {
                    return new File(p.getGameDir()).getAbsolutePath().equals(targetFile.getAbsolutePath());
                }
            });
            
            if (removed) {
                if (!profiles.isEmpty()) {
                    boolean activeStillExists = profiles.stream().anyMatch(p -> p.getName().equalsIgnoreCase(activeProfileName));
                    if (!activeStillExists) {
                        activeProfileName = profiles.get(0).getName();
                    }
                } else {
                    activeProfileName = null;
                }
                save();
                LOG.info("Perfis associados ao diretório {} removidos.", targetPath);
            }
        } catch (IOException e) {
            LOG.warn("Erro ao normalizar diretório para remoção: {}", directory);
        }
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
