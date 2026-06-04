package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

public class ShortcutManager {

    private static final Logger LOG = LoggerFactory.getLogger(ShortcutManager.class);

    public static void createShortcut() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                createWindowsShortcut();
            } else if (os.contains("mac")) {
                createMacShortcut();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                createLinuxShortcut();
            }
        } catch (Exception e) {
            LOG.error("Falha ao criar atalho para o OS: " + os, e);
        }
    }

    private static void createLinuxShortcut() throws IOException {
        String userHome = System.getProperty("user.home");
        Path applicationsPath = Paths.get(userHome, ".local", "share", "applications");
        Files.createDirectories(applicationsPath);

        File desktopFile = applicationsPath.resolve("MineLauncher.desktop").toFile();
        String currentDir = System.getProperty("user.dir");
        if (!currentDir.endsWith(File.separator)) {
            currentDir += File.separator;
        }

        String content = "[Desktop Entry]\n" +
                "Name=MineLauncher\n" +
                "Comment=Launcher de Minecraft\n" +
                "Exec=\"" + currentDir + "launcher.sh\"\n" +
                "Icon=\"" + currentDir + "src/main/resources/images/logo.png\"\n" +
                "Terminal=false\n" +
                "Type=Application\n" +
                "Categories=Game;\n";

        try (FileWriter writer = new FileWriter(desktopFile)) {
            writer.write(content);
        }

        // Tornar executável
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(desktopFile.toPath(), perms);
            
            // Também tornar o launcher.sh executável
            File launcherSh = new File(currentDir + "launcher.sh");
            if (launcherSh.exists()) {
                Files.setPosixFilePermissions(launcherSh.toPath(), perms);
            }
        } catch (UnsupportedOperationException ignored) {}

        LOG.info("Atalho Linux criado em: " + desktopFile.getAbsolutePath());
    }

    private static void createWindowsShortcut() throws IOException, InterruptedException {
        String currentDir = System.getProperty("user.dir");
        if (!currentDir.endsWith(File.separator)) {
            currentDir += File.separator;
        }
        
        String targetPath = currentDir + "launcher.bat";
        String iconPath = currentDir + "src\\main\\resources\\images\\logo.png";

        String powershellCommand = String.format(
                "$ws = New-Object -ComObject WScript.Shell; " +
                "$s = $ws.CreateShortcut(\"$($ws.SpecialFolders.Item('Desktop'))\\MineLauncher.lnk\"); " +
                "$s.TargetPath = '%s'; " +
                "$s.WorkingDirectory = '%s'; " +
                "$s.IconLocation = '%s'; " +
                "$s.Save()",
                targetPath, currentDir, iconPath
        );

        ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-Command", powershellCommand);
        pb.start().waitFor();
        LOG.info("Atalho Windows criado na Área de Trabalho.");
    }

    private static void createMacShortcut() {
        // No Mac, poderíamos criar um .app ou um symlink no Desktop.
        // Por simplicidade, vamos apenas logar que para Mac o ideal é o usuário arrastar para Applications.
        LOG.info("Para macOS, recomenda-se criar um bundle .app ou usar o launcher.sh diretamente.");
    }
}
