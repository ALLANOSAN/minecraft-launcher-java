package com.minelauncher.models;

import java.util.List;

public class ModInfo {
    private String id;
    private String name;
    private String description;
    private String version;
    private String fileName;
    private String downloadUrl;
    private String iconUrl;
    private String source; // "modrinth", "curseforge", "local"
    private long fileSize;
    private boolean enabled = true;
    private List<String> gameVersions;
    private List<String> loaders;

    public ModInfo() {}

    public ModInfo(String name, String fileName, String source) {
        this.name = name;
        this.fileName = fileName;
        this.source = source;
    }

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    public String getVersion() { return version; }
    public void setVersion(String v) { this.version = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String fn) { this.fileName = fn; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String url) { this.downloadUrl = url; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String url) { this.iconUrl = url; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long size) { this.fileSize = size; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getGameVersions() { return gameVersions; }
    public void setGameVersions(List<String> gameVersions) { this.gameVersions = gameVersions; }
    public List<String> getLoaders() { return loaders; }
    public void setLoaders(List<String> loaders) { this.loaders = loaders; }

    @Override
    public String toString() { return name; }
}
