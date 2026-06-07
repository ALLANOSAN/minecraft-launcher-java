package com.minelauncher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.models.GameProfile;
import com.minelauncher.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Autenticação Microsoft via Device Code Flow.
 * Compatível com Windows, Linux e macOS sem necessidade de WebView.
 */
public class MicrosoftAuth {

    private static final Logger LOG = LoggerFactory.getLogger(MicrosoftAuth.class);
    private static final String CLIENT_ID = "00000000402b5328"; // App ID do Minecraft Launcher oficial
    private static final String SCOPE = "service::user.auth::SSI offline_access";

    private final HttpClient httpClient;

    public MicrosoftAuth() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Lançada quando o refresh token foi revogado, expirou completamente
     * (>1 ano sem uso), ou a chamada de refresh retornou erro. Indica
     * que o usuário precisa refazer o login Microsoft. CRIT-3.
     */
    public static class RefreshTokenExpiredException extends RuntimeException {
        public RefreshTokenExpiredException(String message) { super(message); }
        public RefreshTokenExpiredException(String message, Throwable cause) { super(message, cause); }
    }

    public record DeviceCodeResponse(
            String user_code,
            String verification_uri,
            String device_code,
            int interval,
            int expires_in
    ) {}

    /**
     * Inicia o fluxo de login Microsoft.
     * @param onCodeReceived Callback chamado quando o código e a URL estão prontos para exibição.
     */
    public CompletableFuture<GameProfile> login(Consumer<DeviceCodeResponse> onCodeReceived) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Solicitar Device Code
                DeviceCodeResponse dcr = requestDeviceCode();
                onCodeReceived.accept(dcr);

                // 2. Polling para obter o MS Token
                TokenResponse msTokens = pollForMicrosoftToken(dcr);

                // 3. Autenticar no Xbox Live (XBL)
                JsonObject xblResponse = authenticateXBL(msTokens.accessToken);
                String xblToken = JsonUtils.getStringOrThrow(xblResponse, "Token", "XBL");
                String xblUserHash = extractUhsFromXbl(xblResponse);

                // 4. Autenticar no XSTS
                JsonObject xstsResponse = authenticateXSTS(xblToken);
                String xstsToken = JsonUtils.getStringOrThrow(xstsResponse, "Token", "XSTS");

                // 5. Obter Minecraft Access Token
                String mcAccessToken = getMinecraftToken(xblUserHash, xstsToken);

                // 6. Buscar Perfil Minecraft
                JsonObject profile = getMinecraftProfile(mcAccessToken);
                String name = JsonUtils.getStringOrThrow(profile, "name", "MinecraftProfile");
                String id = JsonUtils.getStringOrThrow(profile, "id", "MinecraftProfile");
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

                // BUG-1: isMicrosoft = true para contas autenticadas via Microsoft.
                GameProfile gameProfile = new GameProfile(name, uuid, true);
                gameProfile.setAccessToken(mcAccessToken);
                // CRIT-3 do code-review: persistir refresh token + expiry para
                // evitar re-login diário (Mojang access tokens expiram em ~24h).
                gameProfile.setRefreshToken(msTokens.refreshToken);
                gameProfile.setTokenExpiry(System.currentTimeMillis() + msTokens.expiresInMs);

                LOG.info("Login Microsoft concluído: {} (token expira em {}min)",
                        name, msTokens.expiresInMs / 60000);
                return gameProfile;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Login Microsoft interrompido", e);
            } catch (Exception e) {
                LOG.error("Falha no login Microsoft", e);
                throw new RuntimeException(
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
            }
        });
    }

    /**
     * CRIT-3: renova o access token do Minecraft usando o refresh token salvo.
     * Chamado antes de lançar o jogo se {@link GameProfile#isTokenExpired()} for
     * true. Atualiza o profile in-place com os novos tokens.
     *
     * @param profile profile existente (precisa ter refresh token)
     * @return profile atualizado
     * @throws IOException se o refresh falhar (token revogado, expirado, etc.)
     */
    public GameProfile refreshTokens(GameProfile profile) throws IOException, InterruptedException {
        if (profile == null || profile.getRefreshToken() == null || profile.getRefreshToken().isEmpty()) {
            throw new IOException("Profile sem refresh token — re-login necessário");
        }

        String body = "grant_type=refresh_token" +
                "&client_id=" + CLIENT_ID +
                "&refresh_token=" + URLEncoder.encode(profile.getRefreshToken(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json;
        try {
            json = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception parseErr) {
            throw new IOException("Resposta inválida do token endpoint no refresh: " + response.body(), parseErr);
        }

        if (response.statusCode() != 200) {
            // Preserva error_description para diagnóstico
            String error = JsonUtils.getStringOrNull(json, "error");
            String desc = JsonUtils.getStringOrNull(json, "error_description");
            throw new IOException("Refresh token falhou (HTTP " + response.statusCode() + "): "
                    + (error != null ? error : "?")
                    + (desc != null ? " — " + desc : ""));
        }

        String newAccess = JsonUtils.getStringOrThrow(json, "access_token", "RefreshToken");
        String newRefresh = JsonUtils.getStringOrNull(json, "refresh_token");
        long expiresIn = JsonUtils.getIntOrDefault(json, "expires_in", 86400);

        profile.setAccessToken(newAccess);
        if (newRefresh != null) {
            // Microsoft rotaciona o refresh token; sempre usar o novo se vier
            profile.setRefreshToken(newRefresh);
        }
        profile.setTokenExpiry(System.currentTimeMillis() + expiresIn * 1000L);
        LOG.info("Access token renovado para {} (expira em {}s)", profile.getName(), expiresIn);
        return profile;
    }

    /**
     * Encapsula o trio access_token/refresh_token/expires_in do endpoint /token.
     * Só usado durante o fluxo inicial de login.
     */
    private record TokenResponse(String accessToken, String refreshToken, long expiresInMs) {}

    private DeviceCodeResponse requestDeviceCode() throws IOException, InterruptedException {
        String body = "client_id=" + CLIENT_ID + "&scope=" + SCOPE;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro ao pedir device code (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new DeviceCodeResponse(
                JsonUtils.getStringOrThrow(json, "user_code", "DeviceCode"),
                JsonUtils.getStringOrThrow(json, "verification_uri", "DeviceCode"),
                JsonUtils.getStringOrThrow(json, "device_code", "DeviceCode"),
                JsonUtils.getIntOrDefault(json, "interval", 5),
                JsonUtils.getIntOrDefault(json, "expires_in", 900)
        );
    }

    private TokenResponse pollForMicrosoftToken(DeviceCodeResponse dcr) throws IOException, InterruptedException {
        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                "&client_id=" + CLIENT_ID +
                "&device_code=" + dcr.device_code();

        long start = System.currentTimeMillis();
        long expiry = start + (dcr.expires_in() * 1000L);
        int currentInterval = dcr.interval();

        while (System.currentTimeMillis() < expiry) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json;
            try {
                json = JsonParser.parseString(response.body()).getAsJsonObject();
            } catch (Exception parseErr) {
                throw new IOException("Resposta inválida do Microsoft token endpoint: " + response.body(), parseErr);
            }

            if (response.statusCode() == 200) {
                String access = JsonUtils.getStringOrThrow(json, "access_token", "TokenResponse");
                String refresh = JsonUtils.getStringOrNull(json, "refresh_token");
                long expiresIn = JsonUtils.getIntOrDefault(json, "expires_in", 86400);
                return new TokenResponse(access, refresh, expiresIn * 1000L);
            }

            String error = JsonUtils.getStringOrNull(json, "error");
            // Preserva error_description para diagnóstico (antes era perdido)
            String errorDesc = JsonUtils.getStringOrNull(json, "error_description");

            switch (error != null ? error : "") {
                case "authorization_pending":
                    break;
                case "slow_down":
                    currentInterval += 5;
                    LOG.debug("Microsoft solicitou slow_down; novo interval={}s", currentInterval);
                    break;
                case "expired_token":
                    throw new IOException("Device code expirou. Reinicie o login.");
                case "access_denied":
                    throw new IOException("Usuário negou a autorização.");
                case "":
                    throw new IOException("Resposta sem campo 'error' (HTTP " + response.statusCode() + "): " + response.body());
                default:
                    throw new IOException("Erro no polling Microsoft: " + error
                            + (errorDesc != null ? " — " + errorDesc : ""));
            }

            Thread.sleep(currentInterval * 1000L);
        }
        throw new IOException("Tempo de login expirado");
    }

    private JsonObject authenticateXBL(String msAccessToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + msAccessToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("Authenticate", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro XBL (HTTP " + response.statusCode() + "): " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private String extractUhsFromXbl(JsonObject xblResponse) {
        JsonObject displayClaims = xblResponse.getAsJsonObject("DisplayClaims");
        if (displayClaims == null) {
            throw new IllegalStateException("XBL response sem DisplayClaims");
        }
        JsonArray xui = displayClaims.getAsJsonArray("xui");
        if (xui == null || xui.isEmpty()) {
            throw new IllegalStateException("XBL response sem xui array");
        }
        JsonElement first = xui.get(0);
        if (first == null || first.isJsonNull()) {
            throw new IllegalStateException("XBL xui[0] ausente");
        }
        JsonObject firstObj = first.getAsJsonObject();
        JsonElement uhs = firstObj.get("uhs");
        if (uhs == null || uhs.isJsonNull()) {
            throw new IllegalStateException("XBL xui[0] sem campo uhs");
        }
        return uhs.getAsString();
    }

    private JsonObject authenticateXSTS(String xblToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        properties.add("UserTokens", userTokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("Authenticate", "JWT");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro XSTS (HTTP " + response.statusCode() + "): " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private String getMinecraftToken(String uhs, String xstsToken) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro Minecraft Login (HTTP " + response.statusCode() + "): " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return JsonUtils.getStringOrThrow(json, "access_token", "MinecraftToken");
    }

    private JsonObject getMinecraftProfile(String mcAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro Perfil Minecraft (HTTP " + response.statusCode() + "): " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
