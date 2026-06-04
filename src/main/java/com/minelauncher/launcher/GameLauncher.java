package com.minelauncher.launcher;

import com.google.gson.Gson;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.models.VersionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GameLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(GameLauncher.class);
    private final File baseDir;
    private final VersionManager versionManager;
    private final DownloadManager downloader = new DownloadManager();
    private final Gson gson = new Gson();
    private Process gameProcess;

    public GameLauncher(File baseDir, VersionManager versionManager) {
        this.baseDir = baseDir;
        this.versionManager = versionManager;
    }

    /**
     * Lança o Minecraft
     */
    public void launch(LaunchProfile profile, GameProfile account,
                       Consumer<String> logCallback) throws IOException {

        // 1. Resolver versão real (modded → nome do diretório)
        String versionId = resolveVersionId(profile);
        LOG.info("Preparando lançamento: {} com {} (versão real: {})", profile.getName(), profile.getGameVersion(), versionId);

        // 2. Obter detalhes da versão
        VersionDetail detail = versionManager.getVersionDetail(versionId);
        if (detail == null) {
            throw new IOException("Versão não encontrada: " + versionId);
        }

        // 3. Determinar Java
        String javaPath = resolveJavaPath(profile, detail);

        // 3.5. Verificar e baixar libraries/assets faltantes
        downloadMissingLibraries(detail, versionId);

        // 4. Montar classpath
        String classpath = buildClasspath(detail, versionId);

        // 5. Determinar diretório do jogo
        File gameDir = resolveGameDir(profile);

        // 6. Montar argumentos
        List<String> args = buildLaunchArgs(detail, profile, account, classpath, javaPath, gameDir);

        // LOG DE DEBUG - mostra cada argumento separado para diagnóstico
        LOG.info("=== COMANDO COMPLETO ({} args) ===", args.size());
        for (int i = 0; i < args.size(); i++) {
            LOG.info("[{}] {}", i, args.get(i));
        }
        LOG.info("=== FIM DO COMANDO ===");

        // 6. Spawn do processo
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(gameDir);
        pb.redirectErrorStream(true);

        // Capturar logs
        pb.environment().put("INST_LAUNCHER", "MineLauncher");

        gameProcess = pb.start();

        // 7. Ler output em thread separada
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(gameProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCallback.accept(line);
                }
            } catch (IOException e) {
                LOG.debug("Stream de log encerrado");
            }
        });
        logThread.setDaemon(true);
        logThread.setName("Minecraft-Log");
        logThread.start();

        LOG.info("Minecraft iniciado (PID: {})", gameProcess.pid());
    }

    private String resolveJavaPath(LaunchProfile profile, VersionDetail detail) {
        // 1. Usuário configurou um Java manualmente no perfil — respeitar sempre
        if (profile.getJavaPath() != null && !profile.getJavaPath().isEmpty()) {
            LOG.info("Usando Java configurado no perfil: {}", profile.getJavaPath());
            return profile.getJavaPath();
        }

        // 2. Usar o Java especificado pelo modpack/versão via javaVersion.component
        //    Exemplo: { "component": "java-runtime-delta", "majorVersion": 21 }
        //    Caminho: ~/.minecraft/runtime/<component>/<os>/<component>/bin/java
        if (detail.getJavaVersion() != null && detail.getJavaVersion().getComponent() != null) {
            String component = detail.getJavaVersion().getComponent();
            String os = System.getProperty("os.name").toLowerCase();
            String home = System.getProperty("user.home");

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

            // Tentar os dois formatos de caminho que a Mojang usa
            String ext = os.contains("win") ? ".exe" : "";
            String[] candidates = {
                java.nio.file.Paths.get(home, ".minecraft", "runtime", component, osKey, component, "bin", "java" + ext).toString(),
                java.nio.file.Paths.get(home, ".minecraft", "runtime", component, osKey, "bin", "java" + ext).toString(),
            };

            for (String candidate : candidates) {
                File javaFile = new File(candidate);
                if (javaFile.exists() && javaFile.canExecute()) {
                    LOG.info("Java do modpack encontrado: {} (componente: {}, Java {})",
                        javaFile.getAbsolutePath(), component, detail.getJavaVersion().getMajorVersion());
                    return javaFile.getAbsolutePath();
                }
            }

            LOG.warn("Java do modpack NÃO encontrado! componente={}, os={}", component, osKey);
            LOG.warn("Verifique se o Java runtime foi instalado em: {}/.minecraft/runtime/{}", home, component);
            LOG.warn("Você pode instalar abrindo o launcher oficial da Mojang e iniciando o Minecraft uma vez.");
        }

        // 3. Fallback: detectar Java do sistema com a versão correta
        int targetJava = detail.getJavaVersion() != null ? detail.getJavaVersion().getMajorVersion() : 21;
        LOG.warn("Usando fallback: procurando Java {} no sistema...", targetJava);

        List<JavaDetector.JavaInstall> installs = JavaDetector.detectAll();
        for (JavaDetector.JavaInstall install : installs) {
            if (install.getMajorVersion() == targetJava) {
                LOG.warn("Java {} encontrado no sistema: {}", targetJava, install.getPath());
                return install.getPath();
            }
        }

        LOG.warn("Nenhum Java {} encontrado. Javas detectados:", targetJava);
        for (JavaDetector.JavaInstall i : installs) {
            LOG.warn("  Java {} -> {}", i.getMajorVersion(), i.getPath());
        }
        LOG.warn("Usando 'java' do PATH como fallback — pode falhar!");
        return "java";
    }

    private String buildClasspath(VersionDetail detail, String versionId) throws IOException {
        StringBuilder cp = new StringBuilder();

        // Bibliotecas da versão
        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed()) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getArtifact() == null) continue;

            VersionDetail.DownloadFile artifact = lib.getDownloads().getArtifact();
            File libFile = new File(baseDir, "libraries/" + artifact.getPath());
            if (libFile.exists()) {
                cp.append(libFile.getAbsolutePath()).append(File.pathSeparator);
            }
        }

        // JAR do cliente
        File clientJar = new File(baseDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (clientJar.exists()) {
            cp.append(clientJar.getAbsolutePath());
        }

        return cp.toString();
    }

    /**
     * Verifica e baixa libraries que estão faltando localmente.
     */
    private void downloadMissingLibraries(VersionDetail detail, String versionId) {
        int missing = 0;
        int downloaded = 0;

        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed()) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getArtifact() == null) continue;

            VersionDetail.DownloadFile artifact = lib.getDownloads().getArtifact();
            File libFile = new File(baseDir, "libraries/" + artifact.getPath());

            boolean needsDownload = !libFile.exists();

            // Verificar SHA1 de libraries existentes
            if (!needsDownload && artifact.getSha1() != null && !artifact.getSha1().isEmpty()) {
                try {
                    String actualSha1 = downloader.calculateSHA1(libFile);
                    if (!artifact.getSha1().equals(actualSha1)) {
                        LOG.warn("SHA1 mismatch para {}: esperado {}, obtido {}. Re-download...", libFile.getName(), artifact.getSha1(), actualSha1);
                        needsDownload = true;
                    }
                } catch (Exception e) {
                    LOG.warn("Erro ao verificar SHA1 de {}: {}", libFile.getName(), e.getMessage());
                    needsDownload = true;
                }
            }

            if (needsDownload) {
                missing++;
                try {
                    libFile.getParentFile().mkdirs();
                    downloader.download(artifact.getUrl(), libFile, artifact.getSha1(), null);
                    downloaded++;
                    LOG.info("Library baixada: {}", libFile.getName());
                } catch (Exception e) {
                    LOG.warn("Falha ao baixar library {}: {}", libFile.getName(), e.getMessage());
                }
            }
        }

        // Verificar JAR do cliente
        File clientJar = new File(baseDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (!clientJar.exists()) {
            LOG.warn("JAR do cliente não encontrado: {}", clientJar.getAbsolutePath());
        }

        if (missing > 0) {
            LOG.info("Libraries: {} faltantes, {} baixadas com sucesso de {}", missing, downloaded, detail.getLibraries().size());
        }
    }

    private File resolveGameDir(LaunchProfile profile) {
        if (profile.getGameDir() != null && !profile.getGameDir().isEmpty()) {
            File dir = new File(profile.getGameDir());
            // Se for caminho relativo, resolver contra baseDir
            if (!dir.isAbsolute()) {
                dir = new File(baseDir, profile.getGameDir());
            }
            dir.mkdirs();
            return dir;
        }
        return baseDir;
    }

    private List<String> buildLaunchArgs(VersionDetail detail, LaunchProfile profile,
                                          GameProfile account, String classpath,
                                          String javaPath, File gameDir) throws IOException {

        List<String> args = new ArrayList<>();

        // Mapa de placeholders completo
        Map<String, String> placeholders = buildPlaceholders(detail, profile, account, gameDir);
        placeholders.put("classpath", classpath);
        placeholders.put("natives_directory", getNativesDir(detail, profile.getGameVersion()));
        placeholders.put("classpath_separator", File.pathSeparator);
        placeholders.put("library_directory", new File(baseDir, "libraries").getAbsolutePath());

        // [1] Executável Java
        args.add(javaPath);

        // [2] RAM — sempre explícito, pois o JSON do NeoForge não define isso
        args.add("-Xmx" + profile.getMaxRam() + "M");
        args.add("-Xms" + profile.getMinRam() + "M");
        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=50");

        if (detail.getArguments() != null && detail.getArguments().getJvm() != null) {
            // [3] JVM args do JSON — resolver e substituir placeholders
            //     O JSON do NeoForge já contém, nesta ordem:
            //       -Djava.net.preferIPv6Addresses -DignoreList -DlibraryDirectory
            //       -p <modulePath>
            //       --add-modules ALL-MODULE-PATH
            //       --add-opens ... --add-exports ...
            //     É CRÍTICO manter essa ordem. Não reordenar, não filtrar.
            List<String> jvmArgs = detail.getArguments().resolveJvmArgs(gson);

            // Filtrar apenas -cp/-Xmx/-Xms que o pai vanilla possa ter injetado via mergeParent
            // para evitar duplicatas com o que já adicionamos acima
            boolean skipNext = false;
            for (String raw : jvmArgs) {
                if (skipNext) { skipNext = false; continue; }
                // Pular -cp do pai vanilla (vamos adicionar o nosso logo depois)
                if (raw.equals("-cp") || raw.equals("--class-path")) { skipNext = true; continue; }
                // Pular -Xmx/-Xms/-XX que já adicionamos
                if (raw.startsWith("-Xmx") || raw.startsWith("-Xms") || raw.startsWith("-XX:")) continue;

                args.add(replacePlaceholders(raw, placeholders));
            }

            // [4] Classpath (-cp) — sempre após os JVM args do JSON
            args.add("-cp");
            args.add(classpath);

        } else {
            // Fallback legacy (versões pré-1.13 sem bloco "arguments")
            args.add("-Djava.library.path=" + getNativesDir(detail, profile.getGameVersion()));
            args.add("-cp");
            args.add(classpath);
        }

        // [5] Main class — DEVE vir imediatamente após o -cp <classpath>
        String mainClass = detail.getMainClass();
        if (mainClass == null || mainClass.isEmpty()) {
            mainClass = "net.minecraft.client.main.Main";
        }
        args.add(mainClass);

        // [6] Game args
        if (detail.getArguments() != null && detail.getArguments().getGame() != null) {
            for (String arg : detail.getArguments().resolveGameArgs(gson)) {
                args.add(replacePlaceholders(arg, placeholders));
            }
        } else {
            // Forma legada (pré-1.13)
            args.addAll(Arrays.asList(
                    "--username", account.getName(),
                    "--version", profile.getGameVersion(),
                    "--gameDir", gameDir.getAbsolutePath(),
                    "--assetsDir", new File(baseDir, "assets").getAbsolutePath(),
                    "--assetIndex", detail.getAssets() != null ? detail.getAssets() : "17",
                    "--uuid", account.getUuid().toString().replace("-", ""),
                    "--accessToken", account.getAccessToken(),
                    "--userType", account.isMicrosoft() ? "msa" : "legacy",
                    "--versionType", "MineLauncher"
            ));
        }

        // [7] Tamanho da janela
        if (!profile.isFullscreen()) {
            args.add("--width");
            args.add(String.valueOf(profile.getWidth()));
            args.add("--height");
            args.add(String.valueOf(profile.getHeight()));
        }

        return args;
    }

    private Map<String, String> buildPlaceholders(VersionDetail detail, LaunchProfile profile,
                                                   GameProfile account, File gameDir) {
        Map<String, String> map = new HashMap<>();
        map.put("auth_player_name", account.getName());
        map.put("version_name", profile.getGameVersion());
        map.put("game_directory", gameDir.getAbsolutePath());
        map.put("assets_root", new File(baseDir, "assets").getAbsolutePath());
        map.put("assets_index_name", detail.getAssets());
        map.put("auth_uuid", account.getUuid().toString().replace("-", ""));
        map.put("auth_access_token", account.getAccessToken());
        map.put("auth_session", account.getAccessToken());
        map.put("user_type", account.isMicrosoft() ? "msa" : "legacy");
        map.put("version_type", "MineLauncher");
        map.put("clientid", "");
        map.put("auth_xuid", "");
        map.put("natives_directory", getNativesDir(detail, profile.getGameVersion()));
        map.put("launcher_name", "MineLauncher");
        map.put("launcher_version", "1.0.0");
        map.put("library_directory", new File(baseDir, "libraries").getAbsolutePath());
        map.put("classpath_separator", File.pathSeparator);
        map.put("version", profile.getGameVersion());
        return map;
    }

    private String replacePlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    private String getNativesDir(VersionDetail detail, String versionId) {
        File nativesDir = new File(baseDir, "versions/" + versionId + "/natives");
        nativesDir.mkdirs();

        // Extrair natives dos JARs
        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed() || lib.getNatives() == null) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getClassifiers() == null) continue;

            String osKey = getOSKey();
            String classifier = lib.getNatives().get(osKey);
            if (classifier == null) continue;

            VersionDetail.DownloadFile nativeFile = lib.getDownloads().getClassifiers().get(classifier);
            if (nativeFile == null) continue;

            File jarFile = new File(baseDir, "libraries/" + nativeFile.getPath());
            if (!jarFile.exists()) continue;

            try (ZipFile zip = new ZipFile(jarFile)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")
                            || name.endsWith(".jnilib")) {
                        File dest = new File(nativesDir, new File(name).getName());
                        if (!dest.exists()) {
                            Files.copy(zip.getInputStream(entry), dest.toPath());
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warn("Erro ao extrair natives de {}", jarFile.getName());
            }
        }

        return nativesDir.getAbsolutePath();
    }

    /**
     * Resolve o ID real da versão para perfis com mod loader.
     * Ex: gameVersion=1.21.4, modLoader=neoforge, modLoaderVersion=21.4.157 → "neoforge-21.4.157"
     */
    public String resolveVersionId(LaunchProfile profile) {
        String loader = profile.getModLoader();
        String loaderVersion = profile.getModLoaderVersion();
        String mcVersion = profile.getGameVersion();

        if (loader == null || "vanilla".equals(loader)) {
            return mcVersion;
        }

        // Construir nome esperado do diretório
        String candidate;
        switch (loader) {
            case "forge":
                candidate = mcVersion + "-forge-" + loaderVersion;
                break;
            case "fabric":
                candidate = "fabric-loader-" + loaderVersion + "-" + mcVersion;
                break;
            case "neoforge":
                candidate = "neoforge-" + loaderVersion;
                break;
            case "quilt":
                candidate = "quilt-loader-" + loaderVersion + "-" + mcVersion;
                break;
            default:
                return mcVersion;
        }

        // Verificar se o diretório existe
        File versionDir = new File(baseDir, "versions/" + candidate);
        if (versionDir.exists() && new File(versionDir, candidate + ".json").exists()) {
            return candidate;
        }

        // Fallback: procurar diretório que contenha o loader
        File versionsDir = new File(baseDir, "versions");
        File[] versionDirs = versionsDir.listFiles(File::isDirectory);
        if (versionDirs != null) {
            for (File dir : versionDirs) {
                if (dir.getName().contains(loader) && dir.getName().contains(mcVersion)) {
                    File json = new File(dir, dir.getName() + ".json");
                    if (json.exists()) return dir.getName();
                }
            }
        }

        LOG.warn("Versão modded não encontrada localmente: {}, usando vanilla {}", candidate, mcVersion);
        return mcVersion;
    }

    public void kill() {
        if (gameProcess != null && gameProcess.isAlive()) {
            gameProcess.destroyForcibly();
            LOG.info("Processo do jogo finalizado");
        }
    }

    public boolean isRunning() {
        return gameProcess != null && gameProcess.isAlive();
    }

    private String getOSKey() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }
}
