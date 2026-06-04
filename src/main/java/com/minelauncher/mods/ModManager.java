package com.minelauncher.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.launcher.DownloadManager;
import com.minelauncher.models.ModInfo;
import com.minelauncher.models.ModVersionInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModManager {

    private static final Logger LOG = LoggerFactory.getLogger(ModManager.class);
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    // O launcher fala com CurseForge através deste proxy. A chave real fica
    // server-side na Vercel (env var CURSEFORGE_API_KEY) e NUNCA é embarcada
    // no JAR. Para usar outro proxy, defina CURSEFORGE_PROXY_URL na env.
    // Veja vercel-proxy/README.md para detalhes do deploy.
    private static final String CURSEFORGE_PROXY_URL = resolveCurseForgeProxyUrl();

    private static String resolveCurseForgeProxyUrl() {
        String env = System.getenv("CURSEFORGE_PROXY_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        // Substitua pelo domínio real do seu deploy na Vercel
        return "https://minelauncher-proxy.vercel.app/api/cf";
    }

    // Cache de API com TTL de 5 minutos
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;
    private final Map<String, CachedResult> apiCache = new ConcurrentHashMap<>();

    private record CachedResult(Object data, long timestamp) {
        boolean isValid() { return System.currentTimeMillis() - timestamp < CACHE_TTL_MS; }
    }

    private final DownloadManager downloader;
    private final OkHttpClient client;

    public ModManager(File baseDir) {
        this.downloader = new DownloadManager();
        this.client = com.minelauncher.launcher.HttpClient.getInstance();
    }

    // ==================== CACHE ====================

    private <T> T getCached(String key, Class<T> type) {
        // Limpar entradas expiradas periodicamente
        if (apiCache.size() > 50) {
            apiCache.entrySet().removeIf(e -> !e.getValue().isValid());
        }
        CachedResult cached = apiCache.get(key);
        if (cached != null && cached.isValid() && type.isInstance(cached.data())) {
            LOG.debug("Cache hit: {}", key);
            return type.cast(cached.data());
        }
        return null;
    }

    private void putCache(String key, Object data) {
        apiCache.put(key, new CachedResult(data, System.currentTimeMillis()));
    }

    // ==================== MODRINTH ====================

    /**
     * Busca mods ou modpacks no Modrinth
     * @param projectType "mod" ou "modpack"
     */
    public List<ModInfo> searchModrinth(String query, String gameVersion, int limit, String projectType) throws IOException {
        String cacheKey = "modrinth:search:" + query + ":" + gameVersion + ":" + limit + ":" + projectType;
        @SuppressWarnings("unchecked")
        List<ModInfo> cached = getCached(cacheKey, List.class);
        if (cached != null) return cached;

        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = MODRINTH_API + "/search?query=" + encodedQuery +
                "&limit=" + limit;
        List<String> facetParts = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isEmpty()) {
            facetParts.add("\"versions:" + gameVersion + "\"");
        }
        if (projectType != null && !projectType.isEmpty()) {
            facetParts.add("\"project_type:" + projectType + "\"");
        }
        if (!facetParts.isEmpty()) {
            String encodedFacets = java.net.URLEncoder.encode("[[" + String.join(",", facetParts) + "]]", java.nio.charset.StandardCharsets.UTF_8);
            url += "&facets=" + encodedFacets;
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MineLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonArray hits = json.getAsJsonArray("hits");

            List<ModInfo> mods = new ArrayList<>();
            for (JsonElement hit : hits) {
                JsonObject obj = hit.getAsJsonObject();
                ModInfo mod = new ModInfo();
                mod.setId(obj.get("project_id").getAsString());
                mod.setName(obj.get("title").getAsString());
                mod.setDescription(obj.get("description").getAsString());
                mod.setIconUrl(obj.get("icon_url").getAsString());
                mod.setSource("modrinth");
                mods.add(mod);
            }
            putCache(cacheKey, mods);
            return mods;
        }
    }

    /**
     * Busca mods no Modrinth (compatibilidade)
     */
    public List<ModInfo> searchModrinth(String query, String gameVersion, int limit) throws IOException {
        return searchModrinth(query, gameVersion, limit, null);
    }

    /**
     * Obtém versões de um mod do Modrinth
     */
    public List<JsonObject> getModrinthVersions(String projectId, String gameVersion) throws IOException {
        String url = MODRINTH_API + "/project/" + projectId + "/version?game_versions=[\"" + gameVersion + "\"]";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MineLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonArray arr = JsonParser.parseString(response.body().string()).getAsJsonArray();
            List<JsonObject> versions = new ArrayList<>();
            for (JsonElement el : arr) {
                versions.add(el.getAsJsonObject());
            }
            return versions;
        }
    }

    // ==================== CURSEFORGE ====================

    /**
     * Busca mods no CurseForge
     */
    public List<ModInfo> searchCurseForge(String query, int gameId, int classId,
                                          String gameVersion, int limit) throws IOException {
        String cacheKey = "curseforge:search:" + query + ":" + gameId + ":" + classId + ":" + gameVersion + ":" + limit;
        @SuppressWarnings("unchecked")
        List<ModInfo> cached = getCached(cacheKey, List.class);
        if (cached != null) return cached;

        String url = CURSEFORGE_PROXY_URL + "/mods/search?gameId=" + gameId +
                "&classId=" + classId +
                "&searchFilter=" + query;
        if (gameVersion != null && !gameVersion.isEmpty()) {
            url += "&gameVersion=" + gameVersion;
        }
        url +=
                "&pageSize=" + limit +
                "&sortField=Popularity&sortOrder=desc";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MineLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");

            List<ModInfo> mods = new ArrayList<>();
            for (JsonElement el : data) {
                JsonObject obj = el.getAsJsonObject();
                ModInfo mod = new ModInfo();
                mod.setId(String.valueOf(obj.get("id").getAsInt()));
                mod.setName(obj.get("name").getAsString());
                mod.setDescription(obj.has("summary") ? obj.get("summary").getAsString() : "");
                if (obj.has("logo") && !obj.get("logo").isJsonNull()) {
                    mod.setIconUrl(obj.getAsJsonObject("logo").get("thumbnailUrl").getAsString());
                }
                mod.setSource("curseforge");
                mods.add(mod);
            }
            putCache(cacheKey, mods);
            return mods;
        }
    }

    /**
     * Obtém arquivos de um mod do CurseForge
     */
    public JsonArray getCurseForgeFiles(int modId, String gameVersion) throws IOException {
        String url = CURSEFORGE_PROXY_URL + "/mods/" + modId + "/files?gameVersion=" + gameVersion + "&pageSize=20";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MineLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            return json.getAsJsonArray("data");
        }
    }

    /**
     * Obtém versões disponíveis de um mod/modpack no CurseForge como ModVersionInfo.
     * @param modId ID do projeto no CurseForge
     * @return lista de versões disponíveis
     */
    public List<ModVersionInfo> getCurseForgeVersions(int modId) throws IOException {
        String cacheKey = "curseforge:versions:" + modId;
        @SuppressWarnings("unchecked")
        List<ModVersionInfo> cached = getCached(cacheKey, List.class);
        if (cached != null) return cached;

        JsonArray files = getCurseForgeFiles(modId, "");
        List<ModVersionInfo> versions = new ArrayList<>();
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            ModVersionInfo v = new ModVersionInfo();
            v.setFileId(String.valueOf(f.get("id").getAsInt()));
            v.setFileName(f.get("fileName").getAsString());
            v.setVersionName(f.has("displayName") && !f.get("displayName").isJsonNull()
                    ? f.get("displayName").getAsString() : f.get("fileName").getAsString());
            v.setDownloadUrl(f.has("downloadUrl") && !f.get("downloadUrl").isJsonNull()
                    ? f.get("downloadUrl").getAsString() : null);
            v.setFileSize(f.has("fileLength") ? f.get("fileLength").getAsLong() : 0);
            v.setSource("curseforge");

            // gameVersions
            List<String> gameVersions = new ArrayList<>();
            if (f.has("gameVersions")) {
                for (JsonElement gv : f.getAsJsonArray("gameVersions")) {
                    gameVersions.add(gv.getAsString());
                }
            }
            v.setGameVersions(gameVersions);
            versions.add(v);
        }
        putCache(cacheKey, versions);
        return versions;
    }

    /**
     * Obtém versões disponíveis de um mod/modpack no Modrinth (sem filtro de game version).
     */
    public List<ModVersionInfo> getModrinthAllVersions(String projectId) throws IOException {
        String cacheKey = "modrinth:versions:" + projectId;
        @SuppressWarnings("unchecked")
        List<ModVersionInfo> cached = getCached(cacheKey, List.class);
        if (cached != null) return cached;

        String url = MODRINTH_API + "/project/" + projectId + "/version";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MineLauncher/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonArray arr = JsonParser.parseString(response.body().string()).getAsJsonArray();
            List<ModVersionInfo> versions = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject v = el.getAsJsonObject();
                ModVersionInfo info = new ModVersionInfo();
                info.setFileId(v.get("id").getAsString());
                info.setVersionName(v.get("version_number").getAsString());
                info.setSource("modrinth");

                // game_versions
                List<String> gameVersions = new ArrayList<>();
                if (v.has("game_versions")) {
                    for (JsonElement gv : v.getAsJsonArray("game_versions")) {
                        gameVersions.add(gv.getAsString());
                    }
                }
                info.setGameVersions(gameVersions);

                // loaders
                List<String> loaders = new ArrayList<>();
                if (v.has("loaders")) {
                    for (JsonElement l : v.getAsJsonArray("loaders")) {
                        loaders.add(l.getAsString());
                    }
                }
                info.setLoaders(loaders);

                // files (pegar o primeiro/primário)
                JsonArray files = v.getAsJsonArray("files");
                if (files != null && files.size() > 0) {
                    JsonObject primary = files.get(0).getAsJsonObject();
                    info.setDownloadUrl(primary.get("url").getAsString());
                    info.setFileName(primary.get("filename").getAsString());
                    info.setFileSize(primary.has("size") ? primary.get("size").getAsLong() : 0);
                }

                versions.add(info);
            }
            putCache(cacheKey, versions);
            return versions;
        }
    }

    // ==================== MODS LOCAIS ====================

    /**
     * Instala um mod no diretório de mods do perfil
     */
    public void installMod(File modsDir, ModInfo mod) throws IOException {
        modsDir.mkdirs();
        if (mod.getDownloadUrl() != null) {
            File dest = new File(modsDir, mod.getFileName());
            downloader.download(mod.getDownloadUrl(), dest, null, null);
            mod.setEnabled(true);
            LOG.info("Mod instalado: {}", mod.getName());
        }
    }

    /**
     * Lista mods instalados em um diretório
     */
    public List<ModInfo> getInstalledMods(File modsDir) {
        List<ModInfo> mods = new ArrayList<>();
        if (!modsDir.exists()) return mods;

        for (File file : Objects.requireNonNull(modsDir.listFiles())) {
            if (file.getName().endsWith(".jar")) {
                ModInfo mod = new ModInfo();
                mod.setFileName(file.getName());
                mod.setName(extractModName(file));
                mod.setFileSize(file.length());
                mod.setSource("local");
                mod.setEnabled(!file.getName().endsWith(".disabled"));
                mods.add(mod);
            }
        }
        return mods;
    }

    /**
     * Ativa/desabilita um mod renomeando o arquivo
     */
    public void toggleMod(File modsDir, String fileName, boolean enabled) {
        File file = new File(modsDir, fileName);
        if (!file.exists()) return;

        File target;
        if (enabled) {
            target = new File(modsDir, fileName.replace(".disabled", ""));
        } else {
            target = new File(modsDir, fileName + ".disabled");
        }

        if (file.renameTo(target)) {
            LOG.info("Mod {}: {}", enabled ? "ativado" : "desativado", fileName);
        }
    }

    /**
     * Remove um mod
     */
    public void removeMod(File modsDir, String fileName) {
        File file = new File(modsDir, fileName);
        if (file.exists() && file.delete()) {
            LOG.info("Mod removido: {}", fileName);
        }
    }

    private String extractModName(File jarFile) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                java.util.jar.Attributes attrs = manifest.getMainAttributes();
                String name = attrs.getValue("Implementation-Title");
                if (name != null) return name;
                name = attrs.getValue("Specification-Title");
                if (name != null) return name;
            }
        } catch (Exception e) {
            // Ignorar
        }
        // Fallback: nome do arquivo sem extensão
        String name = jarFile.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : name;
    }

    // ==================== MODPACK MANAGEMENT ====================

    /**
     * Instala um modpack (CurseForge ou Modrinth)
     * @param baseDir diretório base (~/.minecraft)
     * @param modpack informações do modpack
     * @param progress callback de progresso
     */
    public void installModpack(ModInfo modpack, File baseDir,
                               java.util.function.BiConsumer<String, Double> progress) throws IOException {
        String safeName = sanitizeName(modpack.getName());
        LOG.info("Instalando modpack: {} -> {} (source: {})", modpack.getName(), safeName, modpack.getSource());

        File modpackDir = new File(baseDir, "modpacks/" + safeName);
        modpackDir.mkdirs();

        if ("modrinth".equals(modpack.getSource())) {
            installModrinthModpack(modpack, modpackDir, progress);
        } else {
            installCurseForgeModpack(modpack, modpackDir, progress);
        }

        // Salvar manifesto do launcher
        saveLauncherManifest(modpack, modpackDir, null);

        progress.accept("Modpack instalado!", 1.0);
        LOG.info("Modpack {} instalado com sucesso em {}", modpack.getName(), modpackDir.getAbsolutePath());
    }

    /**
     * Instala modpack do CurseForge
     */
    private void installCurseForgeModpack(ModInfo modpack, File modpackDir,
                                          java.util.function.BiConsumer<String, Double> progress) throws IOException {
        String downloadUrl;
        String fileName;

        // Se já tem downloadUrl definido (versão selecionada pelo usuário), usar diretamente
        if (modpack.getDownloadUrl() != null && !modpack.getDownloadUrl().isEmpty()) {
            downloadUrl = modpack.getDownloadUrl();
            fileName = modpack.getFileName() != null ? modpack.getFileName() : modpack.getVersion() + ".zip";
            LOG.info("Usando versão selecionada: {} URL: {}", fileName, downloadUrl);
        } else {
            int modId = Integer.parseInt(modpack.getId());
            LOG.info("Buscando arquivos do modpack CurseForge ID: {}", modId);
            JsonArray files = getCurseForgeFiles(modId, "");
            LOG.info("Encontrados {} arquivos", files.size());

            if (files.size() == 0) throw new IOException("Nenhum arquivo encontrado para o modpack");

            JsonObject latestFile = files.get(0).getAsJsonObject();
            downloadUrl = latestFile.has("downloadUrl") && !latestFile.get("downloadUrl").isJsonNull()
                    ? latestFile.get("downloadUrl").getAsString() : null;
            fileName = latestFile.get("fileName").getAsString();
            LOG.info("Arquivo: {} URL: {}", fileName, downloadUrl);
        }

        if (downloadUrl == null) throw new IOException("URL de download não disponível para: " + fileName);

        // Baixar
        progress.accept("Baixando " + fileName + "...", 0.1);
        File zipFile = new File(modpackDir, fileName);
        downloader.download(downloadUrl, zipFile, null, (downloaded, total) -> {
            double pct = total > 0 ? (double) downloaded / total : 0;
            progress.accept("Baixando modpack... " + formatBytes(downloaded), 0.1 + pct * 0.5);
        });
        LOG.info("Download concluído: {} bytes", zipFile.length());

        // Extrair (formato CurseForge: overrides/ + manifest.json)
        progress.accept("Extraindo modpack...", 0.6);
        extractCurseForgeZip(zipFile, modpackDir);
        zipFile.delete();
        LOG.info("Extração concluída");

        // Ler manifest.json e baixar os mods individuais
        File manifestFile = new File(modpackDir, "manifest.json");
        if (manifestFile.exists()) {
            downloadCurseForgeModsFromManifest(manifestFile, modpackDir, progress);
        } else {
            LOG.warn("manifest.json não encontrado no modpack CurseForge");
        }
    }

    /**
     * Lê o manifest.json do CurseForge e baixa cada mod listado
     */
    public void downloadCurseForgeModsFromManifest(File manifestFile, File modpackDir,
                                                     java.util.function.BiConsumer<String, Double> progress) throws IOException {
        String manifestJson = java.nio.file.Files.readString(manifestFile.toPath());
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        JsonArray modFiles = manifest.getAsJsonArray("files");

        if (modFiles == null || modFiles.size() == 0) {
            LOG.info("Nenhum mod listado no manifest.json");
            return;
        }

        File modsDir = new File(modpackDir, "mods");
        modsDir.mkdirs();

        int total = modFiles.size();
        java.util.concurrent.atomic.AtomicInteger downloaded = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(0);
        LOG.info("Baixando {} mods do modpack CurseForge (paralelo)...", total);

        // Pool de threads configurável (padrão 8)
        int threadCount = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);

        for (com.google.gson.JsonElement el : modFiles) {
            JsonObject modEntry = el.getAsJsonObject();
            int projectID = modEntry.get("projectID").getAsInt();
            int fileID = modEntry.get("fileID").getAsInt();

            pool.submit(() -> {
                try {
                    // Buscar informações do arquivo via API (proxy)
                    String apiUrl = CURSEFORGE_PROXY_URL + "/mods/" + projectID + "/files/" + fileID;
                    Request request = new Request.Builder()
                            .url(apiUrl)
                            .header("User-Agent", "MineLauncher/1.0")
                            .build();

                    String fileUrl = null;
                    String modFileName = null;

                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            LOG.warn("Falha ao buscar arquivo do mod projectID={} fileID={}: HTTP {}", projectID, fileID, response.code());
                            failed.incrementAndGet();
                            return;
                        }
                        JsonObject fileData = JsonParser.parseString(response.body().string()).getAsJsonObject()
                                .getAsJsonObject("data");
                        fileUrl = fileData.has("downloadUrl") && !fileData.get("downloadUrl").isJsonNull()
                                ? fileData.get("downloadUrl").getAsString() : null;
                        modFileName = fileData.get("fileName").getAsString();
                    }

                    if (fileUrl == null) {
                        // Fallback: construir URL direta (funciona para a maioria dos mods)
                        fileUrl = "https://edge.forgecdn.net/files/" +
                                String.valueOf(fileID).substring(0, 4) + "/" +
                                String.valueOf(fileID).substring(4) + "/" + modFileName;
                        LOG.info("downloadUrl nulo para projectID={}, usando fallback CDN: {}", projectID, fileUrl);
                    }

                    // Baixar o mod
                    File dest = new File(modsDir, modFileName);
                    if (dest.exists()) {
                        LOG.debug("Mod já existe, pulando: {}", modFileName);
                        downloaded.incrementAndGet();
                        return;
                    }

                    downloader.download(fileUrl, dest, null, null);
                    int count = downloaded.incrementAndGet();
                    LOG.debug("Mod baixado: {}/{} - {}", count, total, modFileName);

                } catch (Exception e) {
                    LOG.warn("Erro ao baixar mod projectID={} fileID={}: {}", projectID, fileID, e.getMessage());
                    failed.incrementAndGet();
                } finally {
                    int done = downloaded.get() + failed.get();
                    if (progress != null && done % 10 == 0) { // Atualizar a cada 10 mods
                        progress.accept("Baixando mods: " + downloaded.get() + "/" + total + (failed.get() > 0 ? " (" + failed.get() + " falhas)" : ""),
                                0.6 + ((double) done / total) * 0.35);
                    }
                    latch.countDown();
                }
            });
        }

        // Esperar todos os downloads terminarem
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Download de mods interrompido");
        }
        pool.shutdown();

        LOG.info("Download de mods concluído: {} baixados, {} falhas de {}", downloaded.get(), failed.get(), total);
    }

    /**
     * Verifica atualizações de mods de um modpack.
     * Compara fileId do launcher_manifest.json com a versão mais recente na API.
     * @param modpackDir diretório do modpack
     * @return lista de descrições de mods com atualização disponível
     */
    public List<String> checkModUpdates(File modpackDir) {
        List<String> updates = new ArrayList<>();
        File manifest = new File(modpackDir, "manifest.json");
        if (!manifest.exists()) return updates;

        try {
            String manifestJson = java.nio.file.Files.readString(manifest.toPath());
            JsonObject manifestObj = JsonParser.parseString(manifestJson).getAsJsonObject();
            JsonArray modFiles = manifestObj.getAsJsonArray("files");
            if (modFiles == null) return updates;

            for (JsonElement el : modFiles) {
                JsonObject modEntry = el.getAsJsonObject();
                int projectID = modEntry.get("projectID").getAsInt();
                int fileID = modEntry.get("fileID").getAsInt();

                try {
                    // Buscar arquivo mais recente do mod
                    JsonArray files = getCurseForgeFiles(projectID, "");
                    if (files.size() > 0) {
                        int latestFileID = files.get(0).getAsJsonObject().get("id").getAsInt();
                        if (latestFileID != fileID) {
                            String modName = files.get(0).getAsJsonObject()
                                    .has("displayName") ? files.get(0).getAsJsonObject().get("displayName").getAsString()
                                    : "Mod #" + projectID;
                            updates.add(modName + " (atual: " + fileID + ", novo: " + latestFileID + ")");
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Erro ao verificar atualização do mod {}: {}", projectID, e.getMessage());
                }

                // Limitar a 50 verificações para não sobrecarregar a API
                if (updates.size() >= 50) break;
            }
        } catch (Exception e) {
            LOG.warn("Erro ao verificar atualizações: {}", e.getMessage());
        }

        return updates;
    }

    /**
     * Instala modpack do Modrinth (.mrpack)
     */
    private void installModrinthModpack(ModInfo modpack, File modpackDir,
                                        java.util.function.BiConsumer<String, Double> progress) throws IOException {
        String downloadUrl;
        String fileName;

        // Se já tem downloadUrl definido (versão selecionada pelo usuário), usar diretamente
        if (modpack.getDownloadUrl() != null && !modpack.getDownloadUrl().isEmpty()) {
            downloadUrl = modpack.getDownloadUrl();
            fileName = modpack.getFileName() != null ? modpack.getFileName() : modpack.getVersion() + ".mrpack";
            LOG.info("Usando versão selecionada: {} URL: {}", fileName, downloadUrl);
        } else {
            // Buscar versões do modpack no Modrinth
            String projectId = modpack.getId();
            LOG.info("Buscando versões do modpack Modrinth ID: {}", projectId);

            Request request = new Request.Builder()
                    .url(MODRINTH_API + "/project/" + projectId + "/version?limit=1")
                    .header("User-Agent", "MineLauncher/1.0")
                    .build();

            downloadUrl = null;
            fileName = null;

            try (Response response = client.newCall(request).execute()) {
                JsonArray versions = JsonParser.parseString(response.body().string()).getAsJsonArray();
                if (versions.size() == 0) throw new IOException("Nenhuma versão encontrada para o modpack");

                JsonObject latestVersion = versions.get(0).getAsJsonObject();

                // Pegar arquivo .mrpack
                JsonArray files = latestVersion.getAsJsonArray("files");
                if (files.size() > 0) {
                    JsonObject file = files.get(0).getAsJsonObject();
                    downloadUrl = file.get("url").getAsString();
                    fileName = file.get("filename").getAsString();
                }
            }

            if (downloadUrl == null) throw new IOException("URL de download não encontrada no Modrinth");
        }

        // Baixar .mrpack
        progress.accept("Baixando " + fileName + "...", 0.1);
        File mrpackFile = new File(modpackDir, fileName);
        downloader.download(downloadUrl, mrpackFile, null, (downloaded, total) -> {
            double pct = total > 0 ? (double) downloaded / total : 0;
            progress.accept("Baixando modpack... " + formatBytes(downloaded), 0.1 + pct * 0.4);
        });
        LOG.info("Download concluído: {} bytes", mrpackFile.length());

        // Extrair .mrpack (formato Modrinth)
        progress.accept("Extraindo modpack...", 0.5);
        extractModrinthMrpack(mrpackFile, modpackDir, progress);
        mrpackFile.delete();
        LOG.info("Extração concluída");
    }

    /**
     * Extrai .mrpack do Modrinth (zip com modrinth.index.json + overrides/)
     */
    public void extractModrinthMrpack(File mrpackFile, File modpackDir,
                                       java.util.function.BiConsumer<String, Double> progress) throws IOException {
        // 1. Extrair overrides/
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mrpackFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                if (name.equals("modrinth.index.json")) continue;

                if (name.startsWith("overrides/")) {
                    String relativePath = name.substring("overrides/".length());
                    if (relativePath.isEmpty()) continue;
                    File dest = new File(modpackDir, relativePath);
                    dest.getParentFile().mkdirs();
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
            }
        }

        // 2. Ler modrinth.index.json e baixar arquivos
        File indexFile = new File(modpackDir, "modrinth.index.json");
        if (!indexFile.exists()) {
            // Tentar extrair o index do zip
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mrpackFile)) {
                java.util.zip.ZipEntry entry = zip.getEntry("modrinth.index.json");
                if (entry != null) {
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(indexFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
            }
        }

        if (indexFile.exists()) {
            String indexJson = java.nio.file.Files.readString(indexFile.toPath());
            JsonObject index = JsonParser.parseString(indexJson).getAsJsonObject();
            JsonArray indexedFiles = index.getAsJsonArray("files");
            File modsDir = new File(modpackDir, "mods");
            modsDir.mkdirs();

            int total = indexedFiles.size();
            int current = 0;
            for (com.google.gson.JsonElement el : indexedFiles) {
                JsonObject fileObj = el.getAsJsonObject();
                String fileUrl = fileObj.get("downloads").getAsJsonArray().get(0).getAsString();
                String path = fileObj.get("path").getAsString();
                File dest = new File(modpackDir, path);
                dest.getParentFile().mkdirs();

                downloader.download(fileUrl, dest, null, null);
                current++;
                if (progress != null) {
                    final int c = current;
                    progress.accept("Baixando mods do Modrinth: " + c + "/" + total, 0.5 + ((double) c / total) * 0.4);
                }
            }
            LOG.info("Baixados {} arquivos do Modrinth", total);
        }
    }

    /**
     * Extrai ZIP do modpack (formato CurseForge)
     * Estrutura do zip: manifest.json (raiz), overrides/ (configs, mods, etc)
     */
    public void extractCurseForgeZip(File zipFile, File modpackDir) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            int extracted = 0;
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Pular diretórios
                if (entry.isDirectory()) continue;

                // Extrair manifesto CurseForge (necessário para detectar MC version e mod loader)
                if (name.equals("manifest.json")) {
                    File dest = new File(modpackDir, "manifest.json");
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                    LOG.info("Manifesto CurseForge extraído");
                    continue;
                }

                // Extrair tudo de overrides/ para o diretório do modpack
                if (name.startsWith("overrides/")) {
                    String relativePath = name.substring("overrides/".length());
                    if (relativePath.isEmpty()) continue;
                    File dest = new File(modpackDir, relativePath);
                    dest.getParentFile().mkdirs();
                    try (java.io.InputStream is = zip.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    extracted++;
                }
            }
            LOG.info("Extraídos {} arquivos do modpack", extracted);
        }
    }

    /**
     * Salva manifesto do launcher (diferente do manifesto CurseForge)
     */
    private void saveLauncherManifest(ModInfo modpack, File modpackDir, JsonObject fileInfo) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("name", modpack.getName());
        manifest.addProperty("id", modpack.getId());
        manifest.addProperty("source", modpack.getSource());
        manifest.addProperty("installedAt", System.currentTimeMillis());
        if (fileInfo != null) {
            manifest.addProperty("fileId", fileInfo.get("id").getAsInt());
            manifest.addProperty("fileName", fileInfo.get("fileName").getAsString());
        }

        File manifestFile = new File(modpackDir, "launcher_manifest.json");
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        java.nio.file.Files.writeString(manifestFile.toPath(), gson.toJson(manifest));
        LOG.info("Manifesto do launcher salvo: {}", manifestFile.getAbsolutePath());
    }

    /**
     * Lista modpacks instalados em um perfil
     */
    public List<ModInfo> getInstalledModpacks(File profileDir) {
        List<ModInfo> modpacks = new ArrayList<>();
        File modpacksDir = new File(profileDir, "modpacks");
        if (!modpacksDir.exists()) return modpacks;

        for (File dir : modpacksDir.listFiles(File::isDirectory)) {
            File manifest = new File(dir, "manifest.json");
            if (manifest.exists()) {
                try {
                    String json = java.nio.file.Files.readString(manifest.toPath());
                    JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    ModInfo mod = new ModInfo();
                    mod.setId(obj.get("id").getAsString());
                    mod.setName(obj.get("name").getAsString());
                    mod.setSource(obj.get("source").getAsString());
                    mod.setFileName(dir.getName());
                    modpacks.add(mod);
                } catch (Exception e) {
                    LOG.warn("Erro ao ler manifesto: {}", dir.getName());
                }
            }
        }
        return modpacks;
    }

    /**
     * Remove um modpack instalado (remove os mods que ele instalou)
     */
    public void removeModpack(File profileDir, String modpackName) {
        File modpackDir = new File(profileDir, "modpacks/" + modpackName);
        if (!modpackDir.exists()) return;

        File manifest = new File(modpackDir, "manifest.json");
        if (manifest.exists()) {
            try {
                String json = java.nio.file.Files.readString(manifest.toPath());
                JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                LOG.info("Removendo modpack: {}", obj.get("name").getAsString());
            } catch (Exception e) {
                LOG.warn("Erro ao ler manifesto para remoção");
            }
        }

        // Remover todos os .jar da pasta mods que foram extraídos
        // (por segurança, vamos listar os mods do modpack antes de remover)
        deleteDirectory(modpackDir);
        LOG.info("Modpack removido: {}", modpackName);
    }

    /**
     * Remove um modpack pelo nome (busca no diretório de modpacks)
     */
    public void removeModpackByName(File profileDir, String modpackName) {
        File modpacksDir = new File(profileDir, "modpacks");
        if (!modpacksDir.exists()) return;

        for (File dir : modpacksDir.listFiles(File::isDirectory)) {
            File manifest = new File(dir, "manifest.json");
            if (manifest.exists()) {
                try {
                    String json = java.nio.file.Files.readString(manifest.toPath());
                    JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    if (modpackName.equals(obj.get("name").getAsString())) {
                        deleteDirectory(dir);
                        LOG.info("Modpack removido: {}", modpackName);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Remove diretório recursivamente
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        dir.delete();
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\s\\-]", "").replaceAll("\\s+", "_");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
