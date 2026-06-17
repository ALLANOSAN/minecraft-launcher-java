package com.minelauncher.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.utils.JsonUtils;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço central para buscar, baixar e cachear skins do Minecraft.
 * <p>
 * Fontes suportadas:
 * <ul>
 *   <li>Mojang API (sessionserver) — qualquer jogador, sem autenticação</li>
 *   <li>NameMC — lookup por username/UUID</li>
 *   <li>URL direta de imagem PNG</li>
 *   <li>Arquivo local PNG</li>
 * </ul>
 */
public class SkinManager {

    private static final Logger LOG = LoggerFactory.getLogger(SkinManager.class);

    // Mojang API — pública, sem auth
    private static final String MOJANG_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    // NameMC API (não-oficial)
    private static final String NAMEMC_PROFILE_API = "https://api.namemc.com/profile/";

    // Cache em disco (~/.minelauncher/skins/)
    private static final Path CACHE_DIR = Paths.get(
        System.getProperty("user.home"), ".minelauncher", "skins"
    );

    private final java.net.http.HttpClient httpClient;

    // Skin default (Steve) como fallback
    private static Image defaultSkin;

    public SkinManager() {
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        initCache();
    }

    private void initCache() {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            LOG.warn("Não foi possível criar cache de skins: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  APIs públicas
    // ─────────────────────────────────────────────────────────────────

    /**
     * Busca a skin de um jogador pelo nome de usuário (Mojang API pública).
     * Funciona para qualquer jogador Minecraft, sem autenticação.
     */
    public CompletableFuture<SkinData> fetchByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. UUID → nome
                String uuidHex = resolveUUID(username);
                if (uuidHex == null) {
                    LOG.warn("Username '{}' não encontrado na Mojang", username);
                    return null;
                }
                return fetchByUUID(UUID.fromString(uuidHex.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"))).join();
            } catch (Exception e) {
                LOG.error("Erro ao buscar skin por username '{}'", username, e);
                return null;
            }
        });
    }

    /**
     * Busca a skin de um jogador pelo UUID (Mojang API pública).
     */
    public CompletableFuture<SkinData> fetchByUUID(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Tenta cache primeiro
                SkinData cached = loadCached(uuid);
                if (cached != null) return cached;

                String uuidNoDash = uuid.toString().replace("-", "");
                String profileJson = getJson(SESSION_PROFILE_URL + uuidNoDash + "?unsigned=false");

                if (profileJson == null || profileJson.isBlank()) {
                    LOG.warn("Profile vazio/204 para UUID {}", uuid);
                    return null;
                }
                JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
                String name = JsonUtils.getStringOrThrow(profile, "name", "SessionProfile");
                JsonArray properties = profile.getAsJsonArray("properties");

                if (properties == null || properties.isEmpty()) {
                    LOG.warn("Profile sem properties para UUID {}", uuid);
                    return null;
                }

                // Extrair texturas da propriedade "textures" (base64)
                for (var elem : properties) {
                    JsonObject prop = elem.getAsJsonObject();
                    if ("textures".equals(JsonUtils.getStringOrNull(prop, "name"))) {
                        String encoded = JsonUtils.getStringOrThrow(prop, "value", "Textures");
                        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                        JsonObject texturesObj = JsonParser.parseString(decoded).getAsJsonObject();
                        JsonObject textures = texturesObj.getAsJsonObject("textures");
                        if (textures != null && textures.has("SKIN")) {
                            String skinUrl = textures.getAsJsonObject("SKIN")
                                    .get("url").getAsString();
                            Image img = downloadImage(skinUrl);
                            if (img != null) {
                                SkinData data = new SkinData(SkinData.Source.MOJANG, name, skinUrl, img);
                                saveToCache(uuid, data);
                                return data;
                            }
                        }
                    }
                }

                LOG.warn("Nenhuma textura de skin encontrada para UUID {}", uuid);
                return null;
            } catch (Exception e) {
                LOG.error("Erro ao buscar skin por UUID {}", uuid, e);
                return null;
            }
        });
    }

    /**
     * Busca skin via NameMC (pelo nome do jogador).
     * A API pública do NameMC retorna dados do perfil incluindo skin.
     */
    public CompletableFuture<SkinData> fetchFromNameMC(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Primeiro resolve UUID
                String uuidHex = resolveUUID(username);
                if (uuidHex == null) return null;

                // NameMC profile API
                String json = getJson(NAMEMC_PROFILE_API + uuidHex);
                if (json == null || json.isBlank()) {
                    LOG.warn("NameMC profile vazio para {}", username);
                    return null;
                }
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                // NameMC devolve "skinUrl" no objeto raiz
                String skinUrl = JsonUtils.getStringOrNull(obj, "skinUrl");
                if (skinUrl == null || skinUrl.isEmpty()) return null;

                // Preferir textura do NameMC (geralmente 64x64 com overlay)
                Image img = downloadImage(skinUrl);
                if (img != null) {
                    return new SkinData(SkinData.Source.NAMEMC, username, skinUrl, img);
                }
            } catch (Exception e) {
                LOG.warn("NameMC lookup falhou para '{}': {}", username, e.getMessage());
            }
            return null;
        });
    }

    /**
     * Carrega uma skin de uma URL direta de imagem.
     */
    public CompletableFuture<SkinData> loadFromURL(String imageUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Image img = downloadImage(imageUrl);
                if (img != null) {
                    return new SkinData(SkinData.Source.URL, imageUrl, img, null);
                }
            } catch (Exception e) {
                LOG.error("Erro ao carregar skin de URL '{}'", imageUrl, e);
            }
            return null;
        });
    }

    /**
     * Carrega uma skin de um arquivo PNG local.
     */
    public CompletableFuture<SkinData> loadFromFile(Path filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(filePath)) return null;
                try (InputStream is = Files.newInputStream(filePath)) {
                    Image img = new Image(is);
                    if (!img.isError()) {
                        return new SkinData(SkinData.Source.LOCAL_FILE, null, img, filePath.toString());
                    }
                }
            } catch (Exception e) {
                LOG.error("Erro ao carregar skin de arquivo '{}'", filePath, e);
            }
            return null;
        });
    }

    /**
     * Retorna a skin padrão (Steve) como fallback.
     */
    public Image getDefaultSkin() {
        if (defaultSkin == null) {
            // Tenta carregar do classpath ou cria uma imagem simples
            try (InputStream is = getClass().getResourceAsStream("/images/steve.png")) {
                if (is != null) {
                    defaultSkin = new Image(is);
                }
            } catch (Exception ignored) {}
            // Se não achou, cria uma imagem placeholder 64×64
            if (defaultSkin == null || defaultSkin.isError()) {
                defaultSkin = createPlaceholderSkin();
            }
        }
        return defaultSkin;
    }

    /**
     * Obtém a skin da conta atualmente logada.
     * Se for conta Microsoft, pega pelo UUID dela.
     */
    public CompletableFuture<SkinData> fetchForCurrentAccount(
            com.minelauncher.models.GameProfile profile) {
        if (profile == null || profile.getUuid() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return fetchByUUID(profile.getUuid());
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internals
    // ─────────────────────────────────────────────────────────────────

    /**
     * Resolve um username Minecraft para UUID hex (sem traços).
     * Usa a API pública da Mojang (sem auth).
     */
    private String resolveUUID(String username) throws IOException, InterruptedException {
        String json = getJson(MOJANG_UUID_URL + URLEncoder.encode(username, StandardCharsets.UTF_8));
        if (json == null || json.isBlank()) return null;
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return JsonUtils.getStringOrNull(obj, "id");
    }

    private String getJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            return resp.body();
        }
        if (resp.statusCode() == 404) {
            return null; // não encontrado
        }
        LOG.warn("GET {} → HTTP {}", url, resp.statusCode());
        return null;
    }

    private Image downloadImage(String url) {
        try {
            // Verifica cache primeiro (por URL)
            String cacheName = url.hashCode() + ".png";
            Path cached = CACHE_DIR.resolve(cacheName);
            if (Files.exists(cached)) {
                try (InputStream is = Files.newInputStream(cached)) {
                    Image img = new Image(is);
                    if (!img.isError()) return img;
                }
                // Corrompido → deleta e baixa de novo
                Files.delete(cached);
            }

            // Baixa
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                LOG.warn("downloadImage {} → HTTP {}", url, resp.statusCode());
                return null;
            }

            byte[] bytes = resp.body();
            // Salva cache
            try {
                Files.createDirectories(CACHE_DIR);
                Files.write(cached, bytes);
            } catch (IOException e) {
                LOG.debug("Cache de skin não disponível: {}", e.getMessage());
            }

            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            LOG.error("Falha ao baixar imagem '{}'", url, e);
            return null;
        }
    }

    private SkinData loadCached(UUID uuid) {
        Path cacheFile = CACHE_DIR.resolve(uuid.toString() + ".dat");
        if (!Files.exists(cacheFile)) return null;
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(cacheFile))) {
            String name = dis.readUTF();
            String url = dis.readUTF();
            String imgPath = dis.readUTF();
            Path imgFile = CACHE_DIR.resolve(imgPath);
            if (Files.exists(imgFile)) {
                try (InputStream is = Files.newInputStream(imgFile)) {
                    Image img = new Image(is);
                    if (!img.isError()) {
                        return new SkinData(SkinData.Source.MOJANG, name, url, img);
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("Cache de skin inválido para {}", uuid);
        }
        return null;
    }

    private void saveToCache(UUID uuid, SkinData data) {
        try {
            Files.createDirectories(CACHE_DIR);
            String imgName = data.getTextureUrl().hashCode() + ".png";
            // Já salvamos a imagem no downloadImage; aqui salvamos só o metadado
            try (DataOutputStream dos = new DataOutputStream(
                    Files.newOutputStream(CACHE_DIR.resolve(uuid.toString() + ".dat")))) {
                dos.writeUTF(data.getOwnerName() != null ? data.getOwnerName() : "unknown");
                dos.writeUTF(data.getTextureUrl());
                dos.writeUTF(imgName);
            }
        } catch (IOException e) {
            LOG.debug("Não foi possível salvar cache de skin: {}", e.getMessage());
        }
    }

    private static final String MINESKIN_API_URL = "https://api.mineskin.org/generate/upload";

    /**
     * Faz upload de uma imagem local para a MineSkin v2 API.
     */
    public CompletableFuture<SkinData> uploadToMineSkin(Path imagePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String boundary = "Boundary" + System.currentTimeMillis();
                byte[] fileBytes = Files.readAllBytes(imagePath);
                
                String body = "--" + boundary + "\r\n" +
                              "Content-Disposition: form-data; name=\"file\"; filename=\"" + imagePath.getFileName() + "\"\r\n" +
                              "Content-Type: image/png\r\n\r\n";
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(body.getBytes(StandardCharsets.UTF_8));
                baos.write(fileBytes);
                baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(MINESKIN_API_URL))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonObject data = json.getAsJsonObject("data");
                    JsonObject texture = data.getAsJsonObject("texture");
                    String skinUrl = texture.get("url").getAsString();
                    String uuid = data.get("uuid").getAsString();
                    
                    Image img = downloadImage(skinUrl);
                    return new SkinData(SkinData.Source.UPLOAD, "Mineskin", skinUrl, img);
                }
                LOG.warn("MineSkin upload falhou: HTTP {}", resp.statusCode());
            } catch (Exception e) {
                LOG.error("Falha no upload para MineSkin", e);
            }
            return null;
        });
    }
    public boolean exportSkin(SkinData skin, Path targetPath) {
        if (skin == null || skin.getImage() == null) return false;
        try {
            Image fxImg = skin.getImage();
            int w = (int) fxImg.getWidth();
            int h = (int) fxImg.getHeight();
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bi.setRGB(x, y, fxImg.getPixelReader().getArgb(x, y));
                }
            }
            javax.imageio.ImageIO.write(bi, "png", targetPath.toFile());
            LOG.info("Skin exportada para {}", targetPath);
            return true;
        } catch (Exception e) {
            LOG.warn("Falha ao exportar skin: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Lista todas as skins cacheadas no disco.
     * Lê os metadados (.dat) e verifica se as imagens existem.
     */
    public List<CachedSkinEntry> listCachedSkins() {
        List<CachedSkinEntry> result = new ArrayList<>();
        java.io.File[] files = CACHE_DIR.toFile().listFiles((d, name) -> name.endsWith(".dat"));
        if (files == null) return result;
        for (java.io.File f : files) {
            String uuid = f.getName().replace(".dat", "");
            try (java.io.DataInputStream dis = new java.io.DataInputStream(
                    new java.io.FileInputStream(f))) {
                String name = dis.readUTF();
                String url = dis.readUTF();
                String imgName = dis.readUTF();
                Path imgFile = CACHE_DIR.resolve(imgName);
                Image img = null;
                if (java.nio.file.Files.exists(imgFile)) {
                    try (var is = java.nio.file.Files.newInputStream(imgFile)) {
                        img = new Image(is);
                    }
                }
                result.add(new CachedSkinEntry(uuid, name, img, url));
            } catch (Exception e) {
                LOG.debug("Cache inválido: {}", f.getName());
            }
        }
        return result;
    }

    /**
     * Abre a pasta de cache de skins no gerenciador de arquivos do SO.
     */
    public void openCacheFolder() {
        try {
            java.awt.Desktop.getDesktop().open(CACHE_DIR.toFile());
        } catch (Exception e) {
            LOG.warn("Não foi possível abrir pasta de skins: {}", e.getMessage());
        }
    }

    public static class CachedSkinEntry {
        private final String uuid;
        private final String playerName;
        private final Image thumbnail;
        private final String textureUrl;

        public CachedSkinEntry(String uuid, String playerName, Image thumbnail, String textureUrl) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.thumbnail = thumbnail;
            this.textureUrl = textureUrl;
        }

        public String getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public Image getThumbnail() { return thumbnail; }
        public String getTextureUrl() { return textureUrl; }

        /**
         * Retorna uma label amigável para exibir na UI.
         */
        public String getDisplayLabel() {
            return playerName != null ? playerName : uuid.substring(0, 8) + "...";
        }
    }

    /**
     * Cria uma imagem placeholder 64×64 (Steve simplificado) quando não há
     * skin padrão no classpath.
     */
    private static Image createPlaceholderSkin() {
        var img = new javafx.scene.image.WritableImage(64, 64);
        var pw = img.getPixelWriter();
        // Cor da pele (base) e roupa (azul claro)
        int skin = 0xFFC8A060;   // marrom claro
        int pants = 0xFF204060;  // azul escuro
        int shirt = 0xFF4090C0;  // azul claro
        int eye  = 0xFF202020;

        // Cabeça: pixels 8-16 em x, 8-16 em y
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int c = skin;
                // Corpo (20-28 x, 20-32 y) — camisa
                if (x >= 20 && x < 28 && y >= 20 && y < 32) c = shirt;
                // Calças (20-28 x, 32-48 y) 
                if (x >= 20 && x < 28 && y >= 32 && y < 48) c = pants;
                // Braço esquerdo (36-40 x, 20-32 y) — pele
                if (x >= 36 && x < 40 && y >= 20 && y < 32) c = skin;
                // Braço direito (44-48 x, 20-32 y) — pele
                if (x >= 44 && x < 48 && y >= 20 && y < 32) c = skin;
                // Perna esquerda (20-24 x, 48-64 y) 
                if (x >= 20 && x < 24 && y >= 48 && y < 64) c = pants;
                // Perna direita (28-32 x, 48-64 y) 
                if (x >= 28 && x < 32 && y >= 48 && y < 64) c = pants;
                pw.setArgb(x, y, c);
            }
        }
        // Olhos na textura
        pw.setArgb(12, 12, eye);
        pw.setArgb(13, 12, eye);
        pw.setArgb(16, 12, eye);
        pw.setArgb(17, 12, eye);
        return img;
    }
}
