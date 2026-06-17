package com.minelauncher.skin;

import javafx.scene.image.Image;

/**
 * Dados de uma skin carregada: origem, URL da textura e imagem em si.
 */
public class SkinData {

    public enum Source {
        MOJANG,         // via Mojang API (qualquer player)
        NAMEMC,         // via NameMC lookup
        URL,            // URL direta
        LOCAL_FILE,     // arquivo local
        UPLOAD          // upload do usuário
    }

    private final Source source;
    private final String ownerName;   // nick do jogador (MOJANG ou NAMEMC)
    private final String textureUrl;  // URL da imagem PNG
    private final Image image;        // imagem já baixada
    private final String localPath;   // caminho local se LOCAL_FILE
    private final String uuid;        // UUID para render 3D

    public SkinData(Source source, String ownerName, String textureUrl, Image image, String uuid) {
        this.source = source;
        this.ownerName = ownerName;
        this.textureUrl = textureUrl;
        this.image = image;
        this.localPath = null;
        this.uuid = uuid;
    }

    public SkinData(Source source, String textureUrl, Image image, String localPath, String uuid) {
        this.source = source;
        this.ownerName = null;
        this.textureUrl = textureUrl;
        this.image = image;
        this.localPath = localPath;
        this.uuid = uuid;
    }

    public Source getSource() { return source; }
    public String getOwnerName() { return ownerName; }
    public String getTextureUrl() { return textureUrl; }
    public Image getImage() { return image; }
    public String getLocalPath() { return localPath; }
    public String getUuid() { return uuid; }

    public String getDisplayLabel() {
        return switch (source) {
            case MOJANG  -> ownerName != null ? "Mojang: " + ownerName : "Mojang";
            case NAMEMC  -> "NameMC: " + (ownerName != null ? ownerName : "?");
            case URL      -> "URL";
            case LOCAL_FILE -> localPath != null ? localPath : "Arquivo local";
            case UPLOAD  -> "Upload";
        };
    }
}
