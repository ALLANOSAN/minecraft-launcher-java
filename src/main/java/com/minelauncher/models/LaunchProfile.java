package com.minelauncher.models;

import java.util.ArrayList;
import java.util.List;

public class LaunchProfile {
    private String name;
    private String gameVersion;
    private String modLoader; // "vanilla", "forge", "fabric", "quilt"
    private String modLoaderVersion;
    private int minRam = 512;
    private int maxRam = 2048;
    private String javaPath; // null = auto-detect
    private String gameDir;  // null = default .minecraft
    private String lastUsed;
    private List<String> mods = new ArrayList<>();
    private List<String> jvmArgs = new ArrayList<>();
    private int width = 854;
    private int height = 480;
    private boolean fullscreen = false;

    public LaunchProfile() {}

    public LaunchProfile(String name, String gameVersion) {
        this.name = name;
        this.gameVersion = gameVersion;
        this.modLoader = "vanilla";
    }

    // Getters e Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String v) { this.gameVersion = v; }
    public String getModLoader() { return modLoader; }
    public void setModLoader(String loader) { this.modLoader = loader; }
    public String getModLoaderVersion() { return modLoaderVersion; }
    public void setModLoaderVersion(String v) { this.modLoaderVersion = v; }
    public int getMinRam() { return minRam; }
    public void setMinRam(int ram) { this.minRam = ram; }
    public int getMaxRam() { return maxRam; }
    public void setMaxRam(int ram) { this.maxRam = ram; }
    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String path) { this.javaPath = path; }
    public String getGameDir() { return gameDir; }
    public void setGameDir(String dir) { this.gameDir = dir; }
    public String getLastUsed() { return lastUsed; }
    public void setLastUsed(String last) { this.lastUsed = last; }
    public List<String> getMods() { return mods; }
    public void setMods(List<String> mods) { this.mods = mods; }
    public List<String> getJvmArgs() { return jvmArgs; }
    public void setJvmArgs(List<String> args) { this.jvmArgs = args; }
    public int getWidth() { return width; }
    public void setWidth(int w) { this.width = w; }
    public int getHeight() { return height; }
    public void setHeight(int h) { this.height = h; }
    public boolean isFullscreen() { return fullscreen; }
    public void setFullscreen(boolean fs) { this.fullscreen = fs; }

    @Override
    public String toString() { return name; }
}
