package com.minelauncher.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDetector {

    private static final Logger LOG = LoggerFactory.getLogger(JavaDetector.class);

    public static class JavaInstall {
        private final String path;
        private final int majorVersion;

        public JavaInstall(String path, int majorVersion) {
            this.path = path;
            this.majorVersion = majorVersion;
        }

        public String getPath() { return path; }
        public int getMajorVersion() { return majorVersion; }

        @Override
        public String toString() { return "Java " + majorVersion + " (" + path + ")"; }
    }

    /**
     * Detecta todas as instalações de Java no sistema
     */
    public static List<JavaInstall> detectAll() {
        List<JavaInstall> installs = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            detectWindows(installs);
        } else if (os.contains("mac")) {
            detectMac(installs);
        } else {
            detectLinux(installs);
        }

        // Sempre incluir o Java atual do sistema
        String currentJava = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        int currentVersion = getCurrentJavaVersion();
        installs.add(0, new JavaInstall(currentJava, currentVersion));

        // Detectar Java bundled do Minecraft (.minecraft/runtime/)
        detectMinecraftRuntimes(installs);

        // Ordenar por versão (menor primeiro, preferindo 17 e 21)
        installs.sort((a, b) -> Integer.compare(a.getMajorVersion(), b.getMajorVersion()));

        LOG.info("Detectadas {} instalações de Java", installs.size());
        return installs;
    }

    private static void detectMinecraftRuntimes(List<JavaInstall> installs) {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        File runtimeDir = new File(home, ".minecraft/runtime");

        if (!runtimeDir.exists()) {
            LOG.debug("Diretório runtime do Minecraft não encontrado: {}", runtimeDir);
            return;
        }

        // Determinar subpasta do OS dentro do runtime
        // Estrutura do Minecraft runtime:
        //   ~/.minecraft/runtime/<runtime-name>/<os-arch>/   (contém: bin/, conf/, etc.)
        //   ~/.minecraft/runtime/<runtime-name>/<os-arch>/<runtime-name>/bin/java  (Linux/Mac)
        //   ~/.minecraft/runtime/<runtime-name>/windows-x64/bin/java               (Windows)
        String osKey;
        if (os.contains("win")) {
            String arch = System.getProperty("os.arch");
            osKey = arch.contains("64") ? "windows-x64" : "windows-x86";
        } else if (os.contains("mac")) {
            String arch = System.getProperty("os.arch");
            osKey = arch.contains("aarch64") || arch.contains("arm") ? "mac-os-arm64" : "mac-os";
        } else {
            String arch = System.getProperty("os.arch");
            osKey = arch.contains("aarch64") || arch.contains("arm") ? "linux-arm64" : "linux";
        }

        File[] runtimes = runtimeDir.listFiles(File::isDirectory);
        if (runtimes == null) return;

        for (File runtimeEntry : runtimes) {
            // Tentar o caminho completo: runtime/<name>/<osKey>/<name>/bin/java
            File candidate1 = new File(runtimeEntry, osKey + "/" + runtimeEntry.getName() + "/bin/java");
            // Tentar caminho simplificado: runtime/<name>/<osKey>/bin/java
            File candidate2 = new File(runtimeEntry, osKey + "/bin/java");

            for (File candidate : new File[]{candidate1, candidate2}) {
                if (candidate.exists() && candidate.canExecute()) {
                    int ver = getJavaVersion(candidate.getAbsolutePath());
                    if (ver > 0) {
                        LOG.info("Java bundled Minecraft encontrado: {} (Java {})", candidate.getAbsolutePath(), ver);
                        installs.add(new JavaInstall(candidate.getAbsolutePath(), ver));
                        break; // usar o primeiro caminho válido encontrado para este runtime
                    }
                }
            }
        }
    }

    private static void detectWindows(List<JavaInstall> installs) {
        // Program Files
        String[] paths = {
                "C:\\Program Files\\Java",
                "C:\\Program Files (x86)\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft\\jdk-*",
                "C:\\Program Files\\AdoptOpenJDK",
                "C:\\Program Files\\Zulu",
        };

        for (String basePath : paths) {
            File dir = new File(basePath);
            if (dir.exists() && dir.isDirectory()) {
                for (File sub : Objects.requireNonNull(dir.listFiles())) {
                    addIfValid(sub.getAbsolutePath(), installs);
                }
            }
        }

        // Via registry (PowerShell)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "powershell", "-Command",
                    "Get-ChildItem 'HKLM:\\SOFTWARE\\JavaSoft\\Java Development Kit' -ErrorAction SilentlyContinue | " +
                            "ForEach-Object { $_.GetValue('JavaHome') }"
            });
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    addIfValid(line.trim(), installs);
                }
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.debug("Não foi possível ler registry do Java");
        }
    }

    private static void detectMac(List<JavaInstall> installs) {
        String[] paths = {
                "/Library/Java/JavaVirtualMachines",
                "/System/Library/Java/JavaVirtualMachines",
                System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines",
        };

        for (String basePath : paths) {
            File dir = new File(basePath);
            if (dir.exists()) {
                for (File sub : Objects.requireNonNull(dir.listFiles())) {
                    addIfValid(sub.getAbsolutePath() + "/Contents/Home", installs);
                }
            }
        }
    }

    private static void detectLinux(List<JavaInstall> installs) {
        String[] paths = {
                "/usr/lib/jvm",
                "/usr/java",
                "/opt/java",
                "/opt/jdk",
        };

        for (String basePath : paths) {
            File dir = new File(basePath);
            if (dir.exists()) {
                for (File sub : Objects.requireNonNull(dir.listFiles())) {
                    addIfValid(sub.getAbsolutePath(), installs);
                }
            }
        }

        // which java
        try {
            Process pWhich = Runtime.getRuntime().exec(new String[]{"which", "java"});
            String line;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pWhich.getInputStream()))) {
                line = reader.readLine();
            }
            pWhich.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (line != null) {
                File resolved = new File(line.trim()).getCanonicalFile();
                addIfValid(resolved.getParentFile().getParentFile().getAbsolutePath(), installs);
            }
        } catch (Exception e) {
            LOG.debug("which java falhou");
        }
    }

    private static void addIfValid(String javaHome, List<JavaInstall> installs) {
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        File javaFile = new File(javaBin);
        if (!javaFile.exists()) return;

        int version = getJavaVersion(javaBin);
        if (version > 0) {
            installs.add(new JavaInstall(javaBin, version));
        }
    }

    private static int getJavaVersion(String javaPath) {
        // Process é AutoCloseable desde Java 7 — try-with-resources chama destroy()
        // automaticamente se o processo ainda estiver vivo no fim do bloco.
        try {
            Process p = Runtime.getRuntime().exec(new String[]{javaPath, "-version"});
            String line;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()))) {
                line = reader.readLine();
            }
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (line == null) return -1;

            Pattern pattern = Pattern.compile("\"(\\d+)(?:\\.(\\d+))?");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                int major = Integer.parseInt(matcher.group(1));
                // Java 1.8 = Java 8
                if (major == 1) {
                    String minor = matcher.group(2);
                    return minor != null ? Integer.parseInt(minor) : 8;
                }
                return major;
            }
        } catch (Exception e) {
            LOG.debug("Erro ao detectar versão do Java: {}", javaPath);
        }
        return -1;
    }

    public static int getCurrentJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }
        int dot = version.indexOf('.');
        return Integer.parseInt(dot > 0 ? version.substring(0, dot) : version);
    }
}
