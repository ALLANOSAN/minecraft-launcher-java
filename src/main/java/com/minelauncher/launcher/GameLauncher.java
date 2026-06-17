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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Default Java major version when the version manifest doesn't specify one. */
    private static final int DEFAULT_JAVA_VERSION = 21;

    /**
     * Pattern matching {@code ${placeholder}} tokens. Compiled once and
     * reused by {@link #replacePlaceholders}.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

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

        int targetJava = detail.getJavaVersion() != null ? detail.getJavaVersion().getMajorVersion() : DEFAULT_JAVA_VERSION;
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

        // HIGH-1 (code review): nativos resolvidos uma única vez. Antes era
        // chamado 3x (aqui, no else abaixo, e em buildPlaceholders) — cada
        // chamada relia o marker e resolvia getCanonicalPath. Como
        // getNativesDir é a única port de extração de natives, cachear
        // também elimina o risco de comportamento não-determinístico se
        // o marker for invalidado entre as chamadas.
        String nativesDir = nativesExtractor.getNativesDir(detail, profile.getGameVersion());

        Map<String, String> placeholders = buildPlaceholders(detail, profile, account, gameDir, nativesDir);
        placeholders.put("classpath", classpath);
        placeholders.put("natives_directory", nativesDir);
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
                // Forge module fix: o modulo automatico derivado do JAR
                // "1.20.1-forge-47.4.10.jar" tem nome tipo "_1._20._1.forge"
                // (Java prefixa com _ quando o nome começa com digito).
                // A "-DignoreList" original tem "forge-" que só casa com
                // nomes contendo "forge-" (com traço). Modulos Java usam
                // ponto/underline, então "forge-" nunca casa. Adicionamos
                // o ID da versão (que casa com o filename) e "_1." (que
                // casa com o prefixo do modulo derivado).
                if (raw.startsWith("-DignoreList=")) {
                    raw = raw.replace("-DignoreList=", "-DignoreList=" + detail.getId() + ",_1.,");
                }
                args.add(replacePlaceholders(raw, placeholders));
            }

            args.add("-cp");
            args.add(classpath);
        } else {
            args.add("-Djava.library.path=" + nativesDir);
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
                                                   GameProfile account, File gameDir,
                                                   String nativesDir) {
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
        map.put("natives_directory", nativesDir);
        map.put("launcher_name", "MineLauncher");
        map.put("launcher_version", "1.0.0");
        map.put("library_directory", new File(baseDir, "libraries").getAbsolutePath());
        map.put("classpath_separator", File.pathSeparator);
        map.put("version", profile.getGameVersion());
        return map;
    }

    /**
     * Substitui tokens {@code ${key}} no input pelos valores do mapa, em
     * passada única (sem substituição recursiva).
     *
     * <p>MEDIUM do code-review: a versão anterior usava
     * {@link String#replace(CharSequence, CharSequence)} em loop — global
     * replace que, se um valor contivesse {@code ${outra_chave}}, era
     * re-substituído pela próxima iteração. Single-pass via
     * {@link Matcher} resolve. Tokens desconhecidos são preservados como
     * estão no output (em vez de virar {@code null}).
     */
    private String replacePlaceholders(String input, Map<String, String> placeholders) {
        Matcher m = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder(input.length());
        while (m.find()) {
            String key = m.group(1);
            String value = placeholders.get(key);
            if (value != null) {
                m.appendReplacement(out, Matcher.quoteReplacement(value));
            } else {
                // Preserva o token original
                m.appendReplacement(out, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    public String resolveVersionId(LaunchProfile profile) {
        String loader = profile.getModLoader();
        String loaderVersion = profile.getModLoaderVersion();
        String mcVersion = profile.getGameVersion();

        if (loader == null || "vanilla".equals(loader)) {
            return assertSafeVersionId(mcVersion, "mcVersion (perfil: " + profile.getName() + ")");
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
                return assertSafeVersionId(mcVersion, "mcVersion (perfil: " + profile.getName() + ")");
        }

        // MEDIUM do code-review: valida o candidate antes de usar em
        // construção de File. Se o usuário configurou um versionId com
        // path traversal (ex: "../../etc"), o assertSafeVersionId aborta
        // aqui em vez de deixar LibraryVerifier/NativesExtractor escreverem
        // arquivos fora de versions/.
        candidate = assertSafeVersionId(candidate, "candidate (loader=" + loader + ")");

        File versionDir = new File(baseDir, "versions/" + candidate);
        if (versionDir.exists() && new File(versionDir, candidate + ".json").exists()) {
            return candidate;
        }

        // Fallback: procura diretório existente que case com loader + mc.
        // MEDIUM do code-review: o substring match original é frágil
        // (ex: mcVersion "1.2" casava com "1.20"). Preferimos match
        // exato por prefixo; se houver mais de um candidato, logamos
        // e usamos o mais recente (maior mtime).
        File versionsDir = new File(baseDir, "versions");
        File[] versionDirs = versionsDir.listFiles(File::isDirectory);
        if (versionDirs != null) {
            File bestMatch = null;
            int matchCount = 0;
            for (File dir : versionDirs) {
                String name = dir.getName();
                if (!name.contains(loader) || !name.contains(mcVersion)) continue;
                if (!new File(dir, name + ".json").exists()) continue;
                matchCount++;
                if (name.equals(candidate)) {
                    // Match exato tem prioridade absoluta
                    return name;
                }
                if (bestMatch == null || dir.lastModified() > bestMatch.lastModified()) {
                    bestMatch = dir;
                }
            }
            if (matchCount > 1) {
                LOG.warn("Múltiplas versões candidatas para {} ({}); usando a mais recente: {}",
                        candidate, matchCount, bestMatch != null ? bestMatch.getName() : "?");
            }
            if (bestMatch != null) {
                return bestMatch.getName();
            }
        }

        LOG.warn("Versão modded não encontrada localmente: {}, usando vanilla {}", candidate, mcVersion);
        return assertSafeVersionId(mcVersion, "mcVersion (perfil: " + profile.getName() + ")");
    }

    /**
     * Valida que um id de versão é seguro para usar em construção de
     * {@link File}. Rejeita null, vazio, e qualquer id que contenha
     * sequências de path traversal ({@code ..}) ou separadores de
     * path ({@code /}, {@code \\}, NUL).
     *
     * <p>MEDIUM do code-review: protege contra path traversal quando o
     * usuário configura uma versão com nome malicioso (ou quando um
     * modloader produz um id estranho).
     *
     * <p>M2 do code-review (commit 655ae88): esta validação é uma
     * breaking change semântica — antes, {@code resolveVersionId}
     * retornava {@code mcVersion} mesmo quando ele era null/empty, o
     * que causava NPE mais tarde em {@code getVersionDetail(null)}.
     * Agora joga {@link IllegalArgumentException} cedo com mensagem
     * clara, o que o chamador (GameLaunchService) propaga para a UI
     * via callback de erro.
     *
     * @param context rótulo curto de onde veio o id (ex: "mcVersion",
     *                "candidate") — incluído na mensagem de erro para
     *                facilitar debug de perfis legados corrompidos.
     */
    static String assertSafeVersionId(String versionId, String context) {
        if (versionId == null || versionId.isEmpty()) {
            throw new IllegalArgumentException(
                    "versionId vazio (campo: " + context + ")");
        }
        if (versionId.contains("..")
                || versionId.contains("/")
                || versionId.contains("\\")
                || versionId.contains("\0")) {
            throw new IllegalArgumentException(
                    "versionId inválido (path traversal?) campo=" + context
                            + " valor=" + versionId);
        }
        return versionId;
    }

    /** Compatibilidade: overload sem contexto. */
    static String assertSafeVersionId(String versionId) {
        return assertSafeVersionId(versionId, "versionId");
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
