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
}
