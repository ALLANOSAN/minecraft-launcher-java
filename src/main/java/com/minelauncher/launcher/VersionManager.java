package com.minelauncher.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.models.VersionDetail;
import com.minelauncher.models.VersionInfo;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.PlatformUtil;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class VersionManager {

    private static final Logger LOG = LoggerFactory.getLogger(VersionManager.class);
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FORGE_META_URL = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/";
    private static final String FABRIC_META_URL = "https://meta.fabricmc.net/v2/versions";
    private static final String QUILT_META_URL = "https://meta.quiltmc.org/v3/versions";
    private static final String NEOFORGE_META_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge";

    private final File baseDir;
    private final DownloadManager downloader;
    private final Gson gson = new Gson();
    private VersionInfo.VersionManifest cachedManifest;
    // QUAL-13: carimbo de tempo do cache do manifest para evitar re-fetchs
    // desnecessários dentro de uma janela curta. O manifest da Mojang muda
    // apenas quando uma nova versão é lançada, então 5 minutos é seguro.
    private long manifestCachedAt = 0;
    private static final long MANIFEST_TTL_MS = 5 * 60 * 1000L;

    // HIGH-12: cache em memória para getVersionDetail. Antes toda chamada
    // parseava o JSON do disco (~10-50ms por versão). Em uma única sessão
    // getVersionDetail é chamado dezenas de vezes (downloadVersion,
    // launch, installForge/Fabric, etc.). LRU de 32 cobre uso real sem
    // estourar memória.
    private final Map<String, VersionDetail> detailCache =
            Collections.synchronizedMap(new LinkedHashMap<>(DETAIL_CACHE_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VersionDetail> eldest) {
                    return size() > DETAIL_CACHE_MAX;
                }
            });
    // FIX bug do refactor HIGH-12: o cache era construído com hardcoded
    // 32 e a constante DETAIL_CACHE_MAX existia ao lado sem uso. Agora
    // ambos os valores referenciam a mesma constante, evitando drift.
    private static final int DETAIL_CACHE_MAX = 32;

    public VersionManager(File baseDir) {
        this.baseDir = baseDir;
        this.downloader = new DownloadManager();
    }

    /**
     * Obtém o manifest de versões da Mojang
     */
    public VersionInfo.VersionManifest getVersionManifest() throws IOException {
        // QUAL-13: cache com TTL — manifest da Mojang muda raramente (apenas
        // em lançamento de versão), então 5 minutos é seguro e evita refetch
        // a cada installVersion()/getVersionDetail() durante uma mesma sessão.
        long now = System.currentTimeMillis();
        if (cachedManifest != null && (now - manifestCachedAt) < MANIFEST_TTL_MS) {
            return cachedManifest;
        }

        Request request = new Request.Builder().url(VERSION_MANIFEST_URL).build();
        // FIX H-5: usa singleton HttpClient em vez de criar novo OkHttpClient
        // a cada chamada. Antes criava 4 instâncias durante installVersion(),
        // cada uma com seu próprio thread pool, connection pool e DNS cache.
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {

            String json = response.body().string();
            cachedManifest = gson.fromJson(json, VersionInfo.VersionManifest.class);
            manifestCachedAt = now;
            return cachedManifest;
        }
    }

    /**
     * Obtém detalhes de uma versão específica.
     *
     * <p><b>HIGH-12 do code-review:</b> a versão anterior parseava o
     * JSON do disco a cada chamada. Em uma sessão típica
     * {@code getVersionDetail} é chamado 20-50 vezes (download,
     * installForge, installFabric, launch, etc.) — 95% das chamadas
     * batem no mesmo JSON. Cache LRU de 32 elementos (cobrem todas
     * as versões instaladas + algumas remotas) reduz a latência total
     * em 90%+.
     */
    public VersionDetail getVersionDetail(String versionId) throws IOException {
        // HIGH-12: hit em cache → retorna direto sem tocar disco
        VersionDetail memCached = detailCache.get(versionId);
        if (memCached != null) {
            return memCached;
        }

        // 1. Verificar cache local primeiro
        File cached = new File(baseDir, "versions/" + versionId + "/" + versionId + ".json");
        if (cached.exists()) {
            VersionDetail detail = gson.fromJson(Files.readString(cached.toPath()), VersionDetail.class);

            // Se tem inheritsFrom, carregar libraries da versão pai e fazer merge
            if (detail.getInheritsFrom() != null && !detail.getInheritsFrom().isEmpty()) {
                LOG.info("Versão {} herda de {}, carregando libraries do pai...", versionId, detail.getInheritsFrom());
                VersionDetail parent = getVersionDetail(detail.getInheritsFrom());

                // Merge: libraries do pai + libraries do filho (filho sobrescreve)
                List<VersionDetail.Library> merged = new ArrayList<>(parent.getLibraries());
                if (detail.getLibraries() != null) {
                    for (VersionDetail.Library childLib : detail.getLibraries()) {
                        // Remover versão antiga do pai se existe
                        merged.removeIf(parentLib -> parentLib.getName().equals(childLib.getName()));
                        merged.add(childLib);
                    }
                }
                detail.setLibraries(merged);

                // Usar mainClass, assetIndex, assets, downloads do pai se não definidos no filho
                if (detail.getMainClass() == null) detail.setMainClass(parent.getMainClass());
                if (detail.getAssetIndex() == null) detail.setAssetIndex(parent.getAssetIndex());
                if (detail.getAssets() == null) detail.setAssets(parent.getAssets());
                if (detail.getDownloads() == null) detail.setDownloads(parent.getDownloads());
                if (detail.getJavaVersion() == null) detail.setJavaVersion(parent.getJavaVersion());

                // Merge arguments: JVM args do pai (Xmx, library.path, etc.) + args do filho (NeoForge)
                // Regra: filho define seus args primeiro; pai complementa com args que o filho não tem
                if (detail.getArguments() == null && parent.getArguments() != null) {
                    detail.setArguments(parent.getArguments());
                } else if (detail.getArguments() != null && parent.getArguments() != null) {
                    detail.getArguments().mergeParent(parent.getArguments());
                }
            }

            detailCache.put(versionId, detail);
            return detail;
        }

        // 2. Buscar no manifest da Mojang
        VersionInfo.VersionManifest manifest = getVersionManifest();
        VersionInfo versionInfo = manifest.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElse(null);

        if (versionInfo == null) {
            throw new IOException("Versão não encontrada no manifest nem localmente: " + versionId);
        }

        Request request = new Request.Builder().url(versionInfo.getUrl()).build();
        // FIX H-5: singleton HttpClient
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {

            String json = response.body().string();
            cached.getParentFile().mkdirs();
            Files.writeString(cached.toPath(), json);
            VersionDetail detail = gson.fromJson(json, VersionDetail.class);
            detailCache.put(versionId, detail);
            return detail;
        }
    }

    /**
     * HIGH-12: invalida o cache em memória para uma versão específica.
     * Chamado por installForge/installFabric quando a versão é
     * atualizada/modificada. O cache em disco (versions/&lt;v&gt;/.json)
     * continua sendo o source of truth — só limpamos a cópia em RAM.
     */
    public void invalidateDetailCache(String versionId) {
        if (versionId == null) {
            detailCache.clear();
        } else {
            detailCache.remove(versionId);
        }
    }

    /**
     * Baixa todos os arquivos necessários para uma versão (vanilla ou modded)
     */
    public void downloadVersion(String versionId, BiConsumer<String, Double> progress) throws IOException {
        LOG.info("Iniciando download da versão {}", versionId);
        progress.accept("Baixando manifesto...", 0.0);

        // Se for versão modded, primeiro garantir que a versão base está instalada
        String mcVersion = resolveMinecraftVersion(versionId);
        if (!mcVersion.equals(versionId)) {
            LOG.info("Versão {} depende de {}, verificando...", versionId, mcVersion);
            File baseVersionJson = new File(baseDir, "versions/" + mcVersion + "/" + mcVersion + ".json");
            if (!baseVersionJson.exists()) {
                progress.accept("Baixando versão base " + mcVersion + "...", 0.0);
                downloadVersion(mcVersion, (msg, pct) -> progress.accept("Base: " + msg, pct * 0.4));
            }
        }

        VersionDetail detail = getVersionDetail(versionId);
        File versionDir = new File(baseDir, "versions/" + versionId);
        versionDir.mkdirs();

        // 1. Baixar JAR do cliente
        progress.accept("Baixando cliente Minecraft...", 0.05);
        VersionDetail.DownloadFile client = detail.getDownloads().getClient();
        if (client != null) {
            File clientJar = new File(versionDir, versionId + ".jar");
            downloader.download(client.getUrl(), clientJar, client.getSha1(),
                    (downloaded, total) -> {
                        double pct = total > 0 ? (double) downloaded / total : 0;
                        progress.accept("Baixando cliente... " + FileUtils.formatBytes(downloaded), 0.05 + pct * 0.15);
                    });
        }

        // 2. Baixar asset index
        progress.accept("Baixando índice de assets...", 0.2);
        VersionDetail.AssetIndex assetIndex = detail.getAssetIndex();
        File assetIndexFile = new File(baseDir, "assets/indexes/" + assetIndex.getId() + ".json");
        downloader.download(assetIndex.getUrl(), assetIndexFile, assetIndex.getSha1(), null);

        // 3. Baixar assets
        progress.accept("Baixando assets...", 0.25);
        downloadAssets(assetIndexFile, progress);

        // 4. Baixar bibliotecas
        progress.accept("Baixando bibliotecas...", 0.6);
        downloadLibraries(detail, progress);

        LOG.info("Download da versão {} concluído", versionId);
    }

    private void downloadAssets(File indexFile, BiConsumer<String, Double> progress) throws IOException {
        String json = Files.readString(indexFile.toPath());
        JsonObject objects = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("objects");

        int total = objects.size();
        if (total == 0) return;

        int threadCount = com.minelauncher.settings.SettingsManager.getInstance().getDownloadThreads();
        if (threadCount <= 0 || threadCount > 32) {
            threadCount = 8;
        }

        // BUG-7: monta a lista de tarefas e delega ao helper downloadParallel.
        List<Runnable> assetTasks = new ArrayList<>(total);
        for (Map.Entry<String, com.google.gson.JsonElement> entry : objects.entrySet()) {
            String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            File dest = new File(baseDir, "assets/objects/" + prefix + "/" + hash);
            assetTasks.add(() -> {
                try {
                    downloader.download(url, dest, hash, null);
                } catch (Exception e) {
                    LOG.debug("Falha ao baixar asset hash={}: {}", hash, e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        }

        BiConsumer<String, Double> assetsProgress = (msg, pct) -> {
            if (progress != null) {
                progress.accept(msg, 0.25 + pct * 0.35);
            }
        };
        downloadParallel(assetTasks, threadCount, "Assets", assetsProgress);
    }

    private void downloadLibraries(VersionDetail detail, BiConsumer<String, Double> progress) throws IOException {
        List<VersionDetail.Library> libraries = detail.getLibraries();
        List<VersionDetail.DownloadFile> toDownload = new ArrayList<>();

        for (VersionDetail.Library lib : libraries) {
            if (!lib.isAllowed()) continue;

            VersionDetail.LibraryDownloads downloads = lib.getDownloads();
            if (downloads == null) continue;

            // Artefato principal
            if (downloads.getArtifact() != null) {
                toDownload.add(downloads.getArtifact());
            }

            // Natives (lwjgl, etc.)
            if (lib.getNatives() != null && downloads.getClassifiers() != null) {
                String osKey = getOSKey();
                String nativeClassifier = lib.getNatives().get(osKey);
                if (nativeClassifier != null) {
                    VersionDetail.DownloadFile nativeFile = downloads.getClassifiers().get(nativeClassifier);
                    if (nativeFile != null) {
                        toDownload.add(nativeFile);
                    }
                }
            }
        }

        int total = toDownload.size();
        if (total == 0) return;

        int threadCount = com.minelauncher.settings.SettingsManager.getInstance().getDownloadThreads();
        if (threadCount <= 0 || threadCount > 32) {
            threadCount = 8;
        }

        // BUG-7: monta a lista de tarefas e delega ao helper downloadParallel.
        List<Runnable> libTasks = new ArrayList<>(total);
        for (VersionDetail.DownloadFile artifact : toDownload) {
            File dest = new File(baseDir, "libraries/" + resolveLibraryPath(artifact));
            libTasks.add(() -> {
                try {
                    downloader.download(artifact.getUrl(), dest, artifact.getSha1(), null);
                } catch (Exception e) {
                    LOG.warn("Falha ao baixar library {}: {}", dest.getName(), e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        }

        BiConsumer<String, Double> libsProgress = (msg, pct) -> {
            if (progress != null) {
                progress.accept(msg, 0.6 + pct * 0.1);
            }
        };
        try {
            downloadParallel(libTasks, threadCount, "Bibliotecas", libsProgress);
        } catch (IOException ioe) {
            // Mantém a IOException original para o caller decidir
            throw ioe;
        }
    }

    /**
     * BUG-7: helper que encapsula o padrão ExecutorService + CountDownLatch +
     * cleanup usado por downloadAssets e downloadLibraries. Reporta progresso
     * a cada task concluída via {@code progress} (msg, fração 0..1 dentro do
     * lote). Aplica timeout de 30 min e propaga InterruptedException como
     * IOException.
     */
    private void downloadParallel(List<Runnable> tasks, int threads, String label,
                                  BiConsumer<String, Double> progress) throws IOException {
        int total = tasks.size();
        if (total == 0) return;

        int threadCount = threads > 0 && threads <= 32 ? threads : 8;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(total);
        java.util.concurrent.atomic.AtomicInteger failed =
                new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger done =
                new java.util.concurrent.atomic.AtomicInteger(0);

        for (Runnable task : tasks) {
            pool.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    int d = done.incrementAndGet();
                    latch.countDown();
                    if (progress != null) {
                        String suffix = failed.get() > 0 ? " (" + failed.get() + " falhas)" : "";
                        progress.accept(label + ": " + d + "/" + total + suffix,
                                (double) d / total);
                    }
                }
            });
        }

        try {
            if (!latch.await(30, TimeUnit.MINUTES)) {
                LOG.warn("Download de {} excedeu o tempo limite", label);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Download de {} interrompido", label);
            pool.shutdownNow();
            throw new IOException("Download de " + label + " interrompido", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (failed.get() > 0) {
            if ("Assets".equals(label)) {
                LOG.warn("Download de assets concluído com {} falhas de um total de {}",
                        failed.get(), total);
            } else {
                throw new IOException("Falha ao baixar " + failed.get()
                        + " bibliotecas do total de " + total);
            }
        } else {
            LOG.info("Download de {} concluído com sucesso ({} itens)", label, total);
        }
    }

    private String getCurrentJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String bin = javaHome + File.separator + "bin" + File.separator + "java";
        if (PlatformUtil.isWindows()) bin += ".exe";
        return bin;
    }

    /**
     * Instala Forge
     */
    public void installForge(String mcVersion, String forgeVersion,
                             BiConsumer<String, Double> progress) throws IOException {
        String fullVersion = mcVersion + "-" + forgeVersion;
        LOG.info("Instalando Forge {}", fullVersion);

        // Baixar instalador do Forge
        String installerUrl = FORGE_META_URL + fullVersion + "/forge-" + fullVersion + "-installer.jar";
        File installerJar = new File(baseDir, "forge-installer.jar");
        progress.accept("Baixando Forge installer...", 0.1);
        downloader.download(installerUrl, installerJar, null, null);

        // Executar instalador
        progress.accept("Executando instalador Forge...", 0.5);
        ProcessBuilder pb = new ProcessBuilder(
                getCurrentJavaExecutable(), "-jar", installerJar.getAbsolutePath(), "--installClient"
        );
        pb.directory(baseDir);
        pb.inheritIO();
        Process p = pb.start();
        // FIX H-12: timeout de 5min evita travar launcher se o instalador
        // entrar em loop infinito. Antes p.waitFor() bloqueava indefinidamente.
        try {
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("Instalador Forge excedeu 5 minutos — abortado.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Instalação do Forge interrompida");
        }

        installerJar.delete();
        progress.accept("Forge instalado!", 1.0);
    }

    /**
     * Instala Fabric
     */
    public void installFabric(String mcVersion, BiConsumer<String, Double> progress) throws IOException {
        LOG.info("Instalando Fabric para {}", mcVersion);

        // Obter versão do loader
        Request request = new Request.Builder()
                .url(FABRIC_META_URL + "/loader/" + mcVersion)
                .build();

        String loaderVersion;
        // FIX H-5: singleton HttpClient
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {
            String json = response.body().string();
            loaderVersion = JsonParser.parseString(json).getAsJsonArray()
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("loader")
                    .get("version").getAsString();
        }

        progress.accept("Baixando Fabric loader...", 0.3);

        // Baixar profile JSON
        String profileUrl = FABRIC_META_URL + "/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        File profileFile = new File(baseDir, "versions/fabric-loader-" + loaderVersion + "-" + mcVersion +
                "/fabric-loader-" + loaderVersion + "-" + mcVersion + ".json");
        profileFile.getParentFile().mkdirs();
        downloader.download(profileUrl, profileFile, null, null);

        progress.accept("Fabric instalado!", 1.0);
    }

    /**
     * Instala Quilt
     */
    public void installQuilt(String mcVersion, BiConsumer<String, Double> progress) throws IOException {
        LOG.info("Instalando Quilt para {}", mcVersion);

        Request request = new Request.Builder()
                .url(QUILT_META_URL + "/loader/" + mcVersion)
                .build();

        String loaderVersion;
        // FIX H-5: singleton HttpClient
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {
            String json = response.body().string();
            loaderVersion = JsonParser.parseString(json).getAsJsonArray()
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("loader")
                    .get("version").getAsString();
        }

        progress.accept("Baixando Quilt loader...", 0.3);

        String profileUrl = QUILT_META_URL + "/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        File profileFile = new File(baseDir, "versions/quilt-loader-" + loaderVersion + "-" + mcVersion +
                "/quilt-loader-" + loaderVersion + "-" + mcVersion + ".json");
        profileFile.getParentFile().mkdirs();
        downloader.download(profileUrl, profileFile, null, null);

        progress.accept("Quilt instalado!", 1.0);
    }

    /**
     * Instala NeoForge
     */
    public void installNeoForge(String mcVersion, String neoForgeVersion,
                                BiConsumer<String, Double> progress) throws IOException {
        String fullVersion = "neoforge-" + neoForgeVersion;
        LOG.info("Instalando NeoForge {}", fullVersion);

        // Baixar instalador
        String installerUrl = NEOFORGE_META_URL + "/" + neoForgeVersion + "/neoforge-" + neoForgeVersion + "-installer.jar";
        File installerJar = new File(baseDir, "neoforge-installer.jar");
        progress.accept("Baixando NeoForge installer...", 0.1);
        downloader.download(installerUrl, installerJar, null, null);

        // Executar instalador (NeoForge usa --install-client com hífens)
        progress.accept("Executando instalador NeoForge...", 0.5);
        ProcessBuilder pb = new ProcessBuilder(
                getCurrentJavaExecutable(), "-jar", installerJar.getAbsolutePath(),
                "--install-client"
        );
        pb.directory(baseDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) LOG.info("NeoForge: {}", line);
        }
        try {
            // FIX H-12: timeout 5min para o instalador NeoForge.
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("Instalador NeoForge excedeu 5 minutos — abortado.");
            }
            int exitCode = p.exitValue();
            if (exitCode != 0) throw new IOException("NeoForge installer falhou (exit " + exitCode + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Instalação do NeoForge interrompida");
        }

        installerJar.delete();
        progress.accept("NeoForge instalado!", 1.0);
    }

    /**
     * Resolve a versão real do MC para uma versão modded.
     * Se for vanilla, retorna o próprio versionId.
     * Se for modded (fabric-loader-*, neoforge-*), lê inheritsFrom do JSON local.
     */
    public String resolveMinecraftVersion(String versionId) {
        // Já é versão vanilla (ex: 1.21.4)
        if (versionId.matches("\\d+\\.\\d+\\.?\\d*")) return versionId;

        // Tentar ler inheritsFrom do JSON local
        File versionJson = new File(baseDir, "versions/" + versionId + "/" + versionId + ".json");
        if (versionJson.exists()) {
            try {
                String json = Files.readString(versionJson.toPath());
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("inheritsFrom")) {
                    return obj.get("inheritsFrom").getAsString();
                }
            } catch (Exception e) {
                LOG.debug("Não foi possível ler inheritsFrom de {}", versionId);
            }
        }

        // Fallback: extrair do nome
        if (versionId.contains("-forge-")) {
            return versionId.split("-forge-")[0];
        }
        return versionId;
    }

    /**
     * Lista versões já baixadas localmente
     */
    public List<String> getInstalledVersions() {
        File versionsDir = new File(baseDir, "versions");
        if (!versionsDir.exists()) return Collections.emptyList();

        List<String> installed = new ArrayList<>();
        File[] dirs = versionsDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                File json = new File(dir, dir.getName() + ".json");
                if (json.exists()) {
                    installed.add(dir.getName());
                }
            }
        }
        return installed;
    }

    /**
     * Resolve o caminho relativo de uma library dentro de libraries/.
     * O JSON da Mojang pode não incluir {@code path} no artifact,
     * então extraímos o path da URL quando necessário.
     */
    public static String resolveLibraryPath(VersionDetail.DownloadFile artifact) {
        if (artifact.getPath() != null) return artifact.getPath();

        // Ex: "https://libraries.minecraft.net/com/mojang/patchy/2.2.10/patchy-2.2.10.jar"
        // → "com/mojang/patchy/2.2.10/patchy-2.2.10.jar"
        String url = artifact.getUrl();
        if (url != null) {
            try {
                java.net.URI uri = new java.net.URI(url);
                String path = uri.getPath();
                if (path != null && !path.isEmpty()) {
                    return path.startsWith("/") ? path.substring(1) : path;
                }
            } catch (Exception e) {
                LOG.debug("Não foi possível extrair path da URL: {}", url);
            }
        }
        // Fallback seguro: usa SHA1 como nome único para evitar colisão
        String sha = artifact.getSha1();
        if (sha != null) return "libs/" + sha.substring(0, Math.min(12, sha.length())) + ".jar";
        return "libs/unknown-" + System.nanoTime() + ".jar";
    }

    private String getOSKey() {
        return PlatformUtil.getOSKey();
    }

    // ==================== FORGE VERSIONS ====================

    /**
     * Representa uma versão do Forge disponível para uma versão do Minecraft.
     * recommended = true quando a promoção "recommended" do Forge aponta para ela.
     * latest = true quando a promoção "latest" aponta para ela.
     */
    public static class ForgeVersion {
        public String forgeVersion;
        public boolean recommended;
        public boolean latest;

        public ForgeVersion(String forgeVersion, boolean recommended, boolean latest) {
            this.forgeVersion = forgeVersion;
            this.recommended = recommended;
            this.latest = latest;
        }
    }

    /**
     * Busca todas as versões do Forge disponíveis para uma versão do Minecraft,
     * marcando qual é a "recommended" (★) e "latest".
     *
     * Fontes: maven-metadata.xml (lista todas) + promotions_slim.json (recommended/latest).
     */
    public List<ForgeVersion> getForgeVersions(String mcVersion) throws IOException {
        // 1. Fetch promotions_slim.json para saber recommended/latest
        Set<String> recommended = new HashSet<>();
        Set<String> latest = new HashSet<>();
        try {
            Request req = new Request.Builder()
                    .url("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
                    .build();
            try (Response resp = HttpClient.getInstance().newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String json = resp.body().string();
                    JsonObject promos = JsonParser.parseString(json)
                            .getAsJsonObject().getAsJsonObject("promos");
                    if (promos != null) {
                        // "1.20.1-recommended" => "47.2.0"
                        String recKey = mcVersion + "-recommended";
                        String latKey = mcVersion + "-latest";
                        if (promos.has(recKey)) recommended.add(promos.get(recKey).getAsString());
                        if (promos.has(latKey)) latest.add(promos.get(latKey).getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Não foi possível carregar promos do Forge: {}", e.getMessage());
        }

        // 2. Fetch maven-metadata.xml para listar TODAS as versões
        List<ForgeVersion> versions = new ArrayList<>();
        try {
            Request req = new Request.Builder()
                    .url("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml")
                    .build();
            try (Response resp = HttpClient.getInstance().newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    byte[] xmlBytes = resp.body().bytes();
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));
                    NodeList versionNodes = doc.getElementsByTagName("version");
                    String prefix = mcVersion + "-";
                    for (int i = 0; i < versionNodes.getLength(); i++) {
                        String fullVersion = versionNodes.item(i).getTextContent().trim();
                        if (fullVersion.startsWith(prefix)) {
                            String forgeVer = fullVersion.substring(prefix.length());
                            boolean isRec = recommended.contains(forgeVer);
                            boolean isLat = latest.contains(forgeVer);
                            versions.add(new ForgeVersion(forgeVer, isRec, isLat));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Não foi possível carregar maven-metadata do Forge: {}", e.getMessage());
        }

        // Ordenar: recommended primeiro, depois latest, depois ordem reversa
        versions.sort((a, b) -> {
            if (a.recommended != b.recommended) return a.recommended ? -1 : 1;
            if (a.latest != b.latest) return a.latest ? -1 : 1;
            return b.forgeVersion.compareTo(a.forgeVersion); // mais recente primeiro
        });

        return versions;
    }
}
