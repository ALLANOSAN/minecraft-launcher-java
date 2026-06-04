package com.minelauncher.models;

import java.util.List;

/**
 * Representa uma versão/arquivo disponível de um mod ou modpack.
 */
public class ModVersionInfo {
    private String fileId;        // CurseForge file ID ou Modrinth version ID
    private String versionName;   // Nome da versão (ex: "All the Mods 10-7.0")
    private List<String> gameVersions;  // Versões do MC compatíveis
    private List<String> loaders;       // Loaders compatíveis (neoforge, forge, fabric, etc.)
    private String downloadUrl;
    private String fileName;
    private long fileSize;
    private String source;        // "curseforge" ou "modrinth"

    public ModVersionInfo() {}

    // Getters e Setters
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public List<String> getGameVersions() { return gameVersions; }
    public void setGameVersions(List<String> gameVersions) { this.gameVersions = gameVersions; }

    public List<String> getLoaders() { return loaders; }
    public void setLoaders(List<String> loaders) { this.loaders = loaders; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(versionName != null ? versionName : fileName);
        if (gameVersions != null && !gameVersions.isEmpty()) {
            sb.append(" (MC ").append(String.join(", ", gameVersions)).append(")");
        }
        if (loaders != null && !loaders.isEmpty()) {
            sb.append(" [").append(String.join(", ", loaders)).append("]");
        }
        if (fileSize > 0) {
            sb.append(" - ").append(formatSize(fileSize));
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
