package com.minelauncher.launcher;

import com.google.gson.Gson;
import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.models.VersionDetail;
import com.minelauncher.utils.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Orquestra o lançamento do Minecraft.
 *
 * <p>Composição por delegação (refactor por decomposição). Cada
 * responsabilidade vive na sua própria classe:
 * <ul>
 *     <li>{@link LibraryVerifier} — verifica SHA1, baixa libraries
 *     faltantes e monta o classpath;</li>
 *     <li>{@link NativesExtractor} — extrai as libraries nativas
 *     (.so/.dll/.dylib) com marker cache;</li>
 *     <li>{@link ProcessSpawner} — inicia o processo, encaminha o
 *     log via callback, e gerencia o ciclo de vida (kill, shutdown
 *     hook, isRunning).</li>
 * </ul>
 *
 * <p>O que sobrou aqui é puramente orquestração + decisões que
 * envolvem mais de uma dessas peças: resolução de versão
 * (vanilla/forge/fabric/neoforge/quilt), resolução do Java runtime,
 * montagem dos argumentos JVM/game, e os placeholders de substituição.
 *
 * <p>A API pública é mantida estável para que {@code GameLaunchService},
 * {@code WindowService}, {@code MainController} e os testes não
 * precisem ser alterados: {@link #launch}, {@link #resolveVersionId},
 * {@link #registerShutdownHook}, {@link #killGame}, {@link #kill},
 * {@link #killAll}, {@link #isRunning}.
 */
public class GameLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(GameLauncher.class);

    private final File baseDir;
    private final VersionManager versionManager;
    private final LibraryVerifier libraryVerifier;
    private final NativesExtractor nativesExtractor;
    private final ProcessSpawner processSpawner;
    private final Gson gson = new Gson();

    public GameLauncher(File baseDir, VersionManager versionManager) {
        this(baseDir, versionManager, new DownloadManager());
    }

    public GameLauncher(File baseDir, VersionManager versionManager, DownloadManager downloader) {
        this.baseDir = baseDir;
        this.versionManager = versionManager;
        this.libraryVerifier = new LibraryVerifier(baseDir, downloader);
        this.nativesExtractor = new NativesExtractor(baseDir);
        this.processSpawner = new ProcessSpawner();
        // HIGH-11: NÃO registra shutdown hook aqui. A hook só deve ser
        // registrada UMA VEZ pelo entry point (MineLauncher.java), não
        // por cada GameLauncher instanciado. Caso contrário, testes de
        // unidade que instanciam GameLauncher poluem a sequência de
        // shutdown da JVM, e em produção rodar mais de uma vez acarreta
        // hooks duplicados tentando matar os mesmos processos.
    }

    /**
     * HIGH-11: deve ser chamado UMA VEZ pelo entry point (não pelo
     * construtor) para registrar a cleanup em caso de crash.
     */
    public void registerShutdownHook() {
        processSpawner.registerShutdownHook();
    }

    /**
     * Lança o Minecraft
     */
    public void launch(LaunchProfile profile, GameProfile account,
                       Consumer<String> logCallback) throws IOException {

        String versionId = resolveVersionId(profile);
        LOG.info("Preparando lançamento: {} com {} (versão real: {})", profile.getName(), profile.getGameVersion(), versionId);

        VersionDetail detail = versionManager.getVersionDetail(versionId);
        if (detail == null) {
            throw new IOException("Versão não encontrada: " + versionId);
        }

        String javaPath = resolveJavaPath(profile, detail);
        libraryVerifier.verifyAndDownload(detail, versionId);
        String classpath = libraryVerifier.buildClasspath(detail, versionId);
        File gameDir = resolveGameDir(profile);
        List<String> args = buildLaunchArgs(detail, profile, account, classpath, javaPath, gameDir);

        LOG.info("=== COMANDO COMPLETO ({} args) ===", args.size());
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (i > 0 && (args.get(i - 1).equals("--accessToken") || args.get(i - 1).equals("--auth_session"))) {
                LOG.info("[{}] ********** (masked)", i);
            } else if (arg.contains("accessToken=") || arg.contains("session=")) {
                 LOG.info("[{}] (masked token arg)", i);
            } else {
                LOG.info("[{}] {}", i, arg);
            }
        }
        LOG.info("=== FIM DO COMANDO ===");

        processSpawner.spawn(args, gameDir, logCallback);
    }

    private String resolveJavaPath(LaunchProfile profile, VersionDetail detail) {
        if (profile.getJavaPath() != null && !profile.getJavaPath().isEmpty()) {
            LOG.info("Usando Java configurado no perfil: {}", profile.getJavaPath());
            return profile.getJavaPath();
        }

        if (detail.getJavaVersion() != null && detail.getJavaVersion().getComponent() != null) {
            String component = detail.getJavaVersion().getComponent();
            String home = System.getProperty("user.home");

            String osKey = PlatformUtil.getMojangOSKey();
            String ext = PlatformUtil.getJavaExecutableExtension();

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

    private File resolveGameDir(LaunchProfile profile) {
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

    private List<String> buildLaunchArgs(VersionDetail detail, LaunchProfile profile,
                                          GameProfile account, String classpath,
                                          String javaPath, File gameDir) throws IOException {

        List<String> args = new ArrayList<>();

        Map<String, String> placeholders = buildPlaceholders(detail, profile, account, gameDir);
        placeholders.put("classpath", classpath);
        placeholders.put("natives_directory", nativesExtractor.getNativesDir(detail, profile.getGameVersion()));
        placeholders.put("classpath_separator", File.pathSeparator);
        placeholders.put("library_directory", new File(baseDir, "libraries").getAbsolutePath());

        args.add(javaPath);
        args.add("-Xmx" + profile.getMaxRam() + "M");
        args.add("-Xms" + profile.getMinRam() + "M");
        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=50");

        if (detail.getArguments() != null && detail.getArguments().getJvm() != null) {
            List<String> jvmArgs = detail.getArguments().resolveJvmArgs(gson);

            boolean skipNext = false;
            for (String raw : jvmArgs) {
                if (skipNext) { skipNext = false; continue; }
                if (raw.equals("-cp") || raw.equals("--class-path")) { skipNext = true; continue; }
                if (raw.startsWith("-Xmx") || raw.startsWith("-Xms") || raw.startsWith("-XX:")) continue;
                args.add(replacePlaceholders(raw, placeholders));
            }

            args.add("-cp");
            args.add(classpath);
        } else {
            args.add("-Djava.library.path=" + nativesExtractor.getNativesDir(detail, profile.getGameVersion()));
            args.add("-cp");
            args.add(classpath);
        }

        String mainClass = detail.getMainClass();
        if (mainClass == null || mainClass.isEmpty()) {
            mainClass = "net.minecraft.client.main.Main";
        }
        args.add(mainClass);

        if (detail.getArguments() != null && detail.getArguments().getGame() != null) {
            for (String arg : detail.getArguments().resolveGameArgs(gson)) {
                args.add(replacePlaceholders(arg, placeholders));
            }
        } else {
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
        map.put("natives_directory", nativesExtractor.getNativesDir(detail, profile.getGameVersion()));
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

    public String resolveVersionId(LaunchProfile profile) {
        String loader = profile.getModLoader();
        String loaderVersion = profile.getModLoaderVersion();
        String mcVersion = profile.getGameVersion();

        if (loader == null || "vanilla".equals(loader)) {
            return mcVersion;
        }

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

        File versionDir = new File(baseDir, "versions/" + candidate);
        if (versionDir.exists() && new File(versionDir, candidate + ".json").exists()) {
            return candidate;
        }

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

    /**
     * HIGH-11: mata UM processo específico e remove do set de ativos.
     * Usado quando o usuário clica em "Encerrar jogo" — não afeta
     * outros jogos que porventura estejam rodando.
     */
    public void killGame(Process p) {
        processSpawner.kill(p);
    }

    /**
     * HIGH-11: mata TODOS os processos ativos. Só deve ser chamado pelo
     * shutdown hook registrado em {@link #registerShutdownHook()}, ou
     * explicitamente pelo usuário via menu "Encerrar tudo".
     */
    public void killAll() {
        processSpawner.killAll();
    }

    /**
     * Mantido para compatibilidade: alias de killAll().
     */
    public void kill() {
        processSpawner.killAll();
    }

    public boolean isRunning() {
        return processSpawner.isRunning();
    }
}
