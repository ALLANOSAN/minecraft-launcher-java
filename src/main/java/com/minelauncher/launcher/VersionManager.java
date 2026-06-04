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

    public VersionManager(File baseDir) {
        this.baseDir = baseDir;
        this.downloader = new DownloadManager();
    }

    /**
     * Obtém o manifest de versões da Mojang
     */
    public VersionInfo.VersionManifest getVersionManifest() throws IOException {
        if (cachedManifest != null) return cachedManifest;

        Request request = new Request.Builder().url(VERSION_MANIFEST_URL).build();
        // FIX H-5: usa singleton HttpClient em vez de criar novo OkHttpClient
        // a cada chamada. Antes criava 4 instâncias durante installVersion(),
        // cada uma com seu próprio thread pool, connection pool e DNS cache.
        try (Response response = HttpClient.getInstance().newCall(request).execute()) {

            String json = response.body().string();
            cachedManifest = gson.fromJson(json, VersionInfo.VersionManifest.class);
            return cachedManifest;
        }
    }

    /**
     * Obtém detalhes de uma versão específica
     */
    public VersionDetail getVersionDetail(String versionId) throws IOException {
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

                // Merge arguments: JVM args do pai (Xmx, library.path, etc.) + args do filho (NeoForge)
                // Regra: filho define seus args primeiro; pai complementa com args que o filho não tem
                if (detail.getArguments() == null && parent.getArguments() != null) {
                    detail.setArguments(parent.getArguments());
                } else if (detail.getArguments() != null && parent.getArguments() != null) {
                    detail.getArguments().mergeParent(parent.getArguments());
                }
            }

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
            return gson.fromJson(json, VersionDetail.class);
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
        int current = 0;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : objects.entrySet()) {
            String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            File dest = new File(baseDir, "assets/objects/" + prefix + "/" + hash);

            downloader.download(url, dest, hash, null);
            current++;
            progress.accept("Assets: " + current + "/" + total, 0.25 + ((double) current / total) * 0.35);
        }
    }

    private void downloadLibraries(VersionDetail detail, BiConsumer<String, Double> progress) throws IOException {
        List<VersionDetail.Library> libraries = detail.getLibraries();
        int total = libraries.size();
        int current = 0;

        for (VersionDetail.Library lib : libraries) {
            if (!lib.isAllowed()) continue;

            VersionDetail.LibraryDownloads downloads = lib.getDownloads();
            if (downloads == null) continue;

            // Artefato principal
            if (downloads.getArtifact() != null) {
                VersionDetail.DownloadFile artifact = downloads.getArtifact();
                File dest = new File(baseDir, "libraries/" + artifact.getPath());
                downloader.download(artifact.getUrl(), dest, artifact.getSha1(), null);
            }

            // Natives (lwjgl, etc.)
            if (lib.getNatives() != null && downloads.getClassifiers() != null) {
                String osKey = getOSKey();
                String nativeClassifier = lib.getNatives().get(osKey);
                if (nativeClassifier != null) {
                    VersionDetail.DownloadFile nativeFile = downloads.getClassifiers().get(nativeClassifier);
                    if (nativeFile != null) {
                        File dest = new File(baseDir, "libraries/" + nativeFile.getPath());
                        downloader.download(nativeFile.getUrl(), dest, nativeFile.getSha1(), null);
                    }
                }
            }

            current++;
            progress.accept("Bibliotecas: " + current + "/" + total,
                    0.6 + ((double) current / total) * 0.1);
        }
    }

    /**
     * Instala Forge
     */
    public void installForge(String mcVersion, String forgeVersion,
                             BiConsumer<String, Double> progress) throws IOException {
        String fullVersion = mcVersion + "-forge-" + forgeVersion;
        LOG.info("Instalando Forge {}", fullVersion);

        // Baixar instalador do Forge
        String installerUrl = FORGE_META_URL + fullVersion + "/forge-" + fullVersion + "-installer.jar";
        File installerJar = new File(baseDir, "forge-installer.jar");
        progress.accept("Baixando Forge installer...", 0.1);
        downloader.download(installerUrl, installerJar, null, null);

        // Executar instalador
        progress.accept("Executando instalador Forge...", 0.5);
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", installerJar.getAbsolutePath(), "--installClient"
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
                "java", "-jar", installerJar.getAbsolutePath(),
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
        for (File dir : Objects.requireNonNull(versionsDir.listFiles(File::isDirectory))) {
            File json = new File(dir, dir.getName() + ".json");
            if (json.exists()) {
                installed.add(dir.getName());
            }
        }
        return installed;
    }

    private String getOSKey() {
        return PlatformUtil.getOSKey();
    }
}
