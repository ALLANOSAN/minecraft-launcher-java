package com.minelauncher.ui.services;

import com.minelauncher.models.LaunchProfile;
import com.minelauncher.profiles.ProfileManager;
import com.minelauncher.settings.SettingsManager;
import java.io.File;
import java.awt.Desktop;

/**
 * Service para operações de filesystem integradas à UI.
 */
public class FileSystemUIService {

    public static void openFolder(File dir) {
        dir.mkdirs();
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir);
                } else {
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                // Logar erro em nível de sistema
                System.err.println("Erro ao abrir pasta: " + e.getMessage());
            }
        }).start();
    }

    public static File resolveGameDir(LaunchProfile profile, File baseDir) {
        if (profile.getGameDir() != null && !profile.getGameDir().isEmpty()) {
            File dir = new File(profile.getGameDir());
            if (!dir.isAbsolute()) {
                dir = new File(baseDir, profile.getGameDir());
            }
            dir.mkdirs();
            return dir;
        }
        return baseDir;
    }
}
