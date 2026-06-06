package com.minelauncher.utils;

import java.util.Locale;

/**
 * Helpers de detecção de SO. Centraliza o pattern duplicado em 7+ lugares:
 *     String os = System.getProperty("os.name").toLowerCase();
 *     if (os.contains("win")) ...
 */
public final class PlatformUtil {

    private PlatformUtil() {}

    /** "windows", "osx" ou "linux" — usado como key em metadata de mods/libs. */
    public static String getOSKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    public static boolean isWindows() {
        return getOSKey().equals("windows");
    }

    public static boolean isMac() {
        return getOSKey().equals("osx");
    }

    public static boolean isLinux() {
        return getOSKey().equals("linux");
    }

    /**
     * QUAL-15: retorna a chave usada pela Mojang nos runtimes Java —
     * combina SO + arquitetura (ex.: "windows-x64", "mac-os-arm64",
     * "linux-arm64"/"linux"/"linux-x86"). Diferente de {@link #getOSKey()},
     * que devolve apenas "windows"/"osx"/"linux" para uso em metadata
     * de mods/libs. Para Linux, o fallback é "linux" (x86_64).
     */
    public static String getMojangOSKey() {
        String os = getOSKey();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean is64 = arch.contains("64");
        boolean isArm = arch.contains("aarch64") || arch.contains("arm");
        return switch (os) {
            case "windows" -> is64 ? "windows-x64" : "windows-x86";
            case "osx" -> isArm ? "mac-os-arm64" : "mac-os";
            default -> isArm ? "linux-arm64" : is64 ? "linux" : "linux-x86";
        };
    }

    /**
     * QUAL-15: extensão do executável Java (".exe" no Windows, vazio nos demais).
     */
    public static String getJavaExecutableExtension() {
        return isWindows() ? ".exe" : "";
    }
}
