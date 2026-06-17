package com.minelauncher.utils;

/**
 * Constantes globais da aplicação: URLs externas, paths de recursos,
 * dimensões da janela, timeouts, etc.
 *
 * <p>Centralizar evita strings mágicas espalhadas pelos controllers
 * e facilita mudar endpoints (ex: trocar o proxy do CurseForge).
 */
public final class AppConstants {

    private AppConstants() {}

    // ===== Recursos (FXML / CSS / images) =====
    public static final String FXML_MAIN = "/fxml/main.fxml";
    public static final String CSS_DARK_THEME = "/css/dark-theme.css";
    public static final String IMG_LOGO = "/images/logo.png";

    // ===== Janela principal =====
    public static final double WINDOW_WIDTH = 1210.0;
    public static final double WINDOW_HEIGHT = 750.0;
    public static final double WINDOW_MIN_WIDTH = 920.0;
    public static final double WINDOW_MIN_HEIGHT = 680.0;

    // ===== Versão =====
    public static final String APP_VERSION = "1.1.5";

    // ===== Título =====
    public static final String APP_TITLE = "MineLauncher v" + APP_VERSION;

    // ===== Fuso horário (Brazil) =====
    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    // ===== Endpoints Microsoft OAuth2 =====
    public static final String MS_DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    public static final String MS_TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    public static final String XBOX_RELYING_PARTY = "http://auth.xboxlive.com";
    public static final String XBOX_USER_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_AUTH_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static final String MC_LOGIN_WITH_XBOX_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    // ===== Endpoints de modpacks =====
    public static final String MODRINTH_API = "https://api.modrinth.com/v2";
    public static final String CURSEFORGE_PROXY = "https://minecraft-launcher-java.vercel.app/api/cf";
    public static final String FORGE_CDN_BASE = "https://edge.forgecdn.net/files/";
}
