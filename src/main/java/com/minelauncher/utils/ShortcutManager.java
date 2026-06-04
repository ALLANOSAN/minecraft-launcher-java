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
            LOG.error("Falha ao criar atalho para o OS: {}", os, e);
        }
    }

    private static void createLinuxShortcut() throws IOException {
        String userHome = System.getProperty("user.home");
        Path applicationsPath = Paths.get(userHome, ".local", "share", "applications");
        Files.createDirectories(applicationsPath);

        File desktopFile = applicationsPath.resolve("MineLauncher.desktop").toFile();
        // Validação: currentDir vem de System property (controlado pela JVM),
        // mas sanitizamos para impedir caracteres de controle/quebra-de-linha
        // que corromperiam o .desktop file.
        String currentDir = sanitizeForDesktopEntry(System.getProperty("user.dir"));

        // Usa formato Exec com aspas duplas (mais portável que aspas simples em .desktop)
        String content = "[Desktop Entry]\n" +
                "Name=MineLauncher\n" +
                "Comment=Launcher de Minecraft\n" +
                "Exec=\"" + currentDir + "/launcher.sh\"\n" +
                "Icon=\"" + currentDir + "/src/main/resources/images/logo.png\"\n" +
                "Terminal=false\n" +
                "Type=Application\n" +
                "Categories=Game;\n";

        try (FileWriter writer = new FileWriter(desktopFile)) {
            writer.write(content);
        }

        // Tornar executável
        try {
            Set<PosixFilePermission> perms = SecretCodec.ownerExecOnly();
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(desktopFile.toPath(), perms);

            File launcherSh = new File(currentDir, "launcher.sh");
            if (launcherSh.exists()) {
                Files.setPosixFilePermissions(launcherSh.toPath(), perms);
            }
        } catch (UnsupportedOperationException e) {
            // Sistema de arquivos sem suporte a POSIX (ex: FAT32 montado no Linux)
            LOG.debug("POSIX permissions não suportadas, pulando chmod", e);
        }

        LOG.info("Atalho Linux criado em: {}", desktopFile.getAbsolutePath());
    }

    private static void createWindowsShortcut() throws IOException, InterruptedException {
        String currentDir = System.getProperty("user.dir");
        if (!currentDir.endsWith(File.separator)) {
            currentDir += File.separator;
        }

        // Gera script PowerShell em arquivo temp — imune a injeção via path,
        // mesmo que contenha aspas, $, variáveis etc. (anteriormente interpolava
        // direto em -Command, o que era vetor de injeção.)
        String targetPath = currentDir + "launcher.bat";
        String iconPath = currentDir + "src\\main\\resources\\images\\logo.png";

        // PowerShell com strings single-quoted (sem interpolação de variáveis).
        // Aspas simples no path são duplicadas (regra de escape do PowerShell).
        String psScript =
                "$ws = New-Object -ComObject WScript.Shell\n" +
                "$desktop = $ws.SpecialFolders.Item('Desktop')\n" +
                "$s = $ws.CreateShortcut(\"$desktop\\MineLauncher.lnk\")\n" +
                "$s.TargetPath = '" + escapePsSingleQuoted(targetPath) + "'\n" +
                "$s.WorkingDirectory = '" + escapePsSingleQuoted(currentDir) + "'\n" +
                "$s.IconLocation = '" + escapePsSingleQuoted(iconPath) + "'\n" +
                "$s.Save()\n";

        Path scriptFile = Files.createTempFile("minelauncher-shortcut-", ".ps1");
        try {
            Files.writeString(scriptFile, psScript);
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-ExecutionPolicy", "Bypass", "-File", scriptFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) LOG.debug("ps: {}", line);
            }
            int exit = p.waitFor();
            if (exit != 0) {
                LOG.warn("PowerShell exit code {} ao criar atalho", exit);
            }
        } finally {
            try { Files.deleteIfExists(scriptFile); } catch (IOException ignored) { /* best effort */ }
        }
        LOG.info("Atalho Windows criado na Área de Trabalho.");
    }

    private static void createMacShortcut() {
        // No Mac, poderíamos criar um .app ou um symlink no Desktop.
        // Por simplicidade, vamos apenas logar que para Mac o ideal é o usuário arrastar para Applications.
        LOG.info("Para macOS, recomenda-se criar um bundle .app ou usar o launcher.sh diretamente.");
    }

    /**
     * Escapa uma string para uso dentro de aspas simples em PowerShell.
     * PowerShell usa aspas duplas ('') pra representar uma aspas simples dentro de string single-quoted.
     */
    private static String escapePsSingleQuoted(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    /**
     * Remove caracteres de controle / quebra de linha que quebrariam o formato .desktop.
     */
    private static String sanitizeForDesktopEntry(String s) {
        if (s == null) return "";
        // Permite letras, dígitos, separadores, espaços, hífen, underscore, ponto, dois-pontos.
        // Bloqueia newline, tab, retorno, e qualquer caractere de controle.
        return s.replaceAll("[\\p{Cntrl}]", "").trim();
    }
}
