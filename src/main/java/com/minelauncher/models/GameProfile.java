package com.minelauncher.models;

import java.util.UUID;

public class GameProfile {
    private String name;
    private UUID uuid;
    private String accessToken;
    private String refreshToken;
    private String xblToken;
    private String xstsToken;
    private boolean isMicrosoft;
    private boolean isOffline;
    private long tokenExpiry;

    public GameProfile() {}

    public GameProfile(String name, UUID uuid, boolean isMicrosoft) {
        this.name = name;
        this.uuid = uuid;
        this.isMicrosoft = isMicrosoft;
        this.isOffline = !isMicrosoft;
    }

    // Getters e Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String token) { this.accessToken = token; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String token) { this.refreshToken = token; }
    public String getXblToken() { return xblToken; }
    public void setXblToken(String token) { this.xblToken = token; }
    public String getXstsToken() { return xstsToken; }
    public void setXstsToken(String token) { this.xstsToken = token; }
    public boolean isMicrosoft() { return isMicrosoft; }
    public void setMicrosoft(boolean microsoft) { isMicrosoft = microsoft; }
    public boolean isOffline() { return isOffline; }
    public void setOffline(boolean offline) { isOffline = offline; }
    public long getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(long expiry) { this.tokenExpiry = expiry; }

    public boolean isTokenExpired() {
        return System.currentTimeMillis() > tokenExpiry;
    }
}
