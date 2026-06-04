package com.minelauncher.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Carrega configurações externas de launcher.properties.
 */
public class ConfigLoader {
    private final Properties props = new Properties();

    public ConfigLoader(String baseDir) {
        try (FileInputStream fis = new FileInputStream(baseDir + "/launcher.properties")) {
            props.load(fis);
        } catch (IOException e) {
            // Se não existir, usa defaults
        }
    }

    public String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }
}
