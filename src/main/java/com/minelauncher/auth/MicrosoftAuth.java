package com.minelauncher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.models.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
                String msAccessToken = pollForMicrosoftToken(dcr);

                // 3. Autenticar no Xbox Live (XBL)
                JsonObject xblResponse = authenticateXBL(msAccessToken);
                String xblToken = getStringOrThrow(xblResponse, "Token", "XBL");
                String xblUserHash = extractUhsFromXbl(xblResponse);

                // 4. Autenticar no XSTS
                JsonObject xstsResponse = authenticateXSTS(xblToken);
                String xstsToken = getStringOrThrow(xstsResponse, "Token", "XSTS");

                // 5. Obter Minecraft Access Token
                String mcAccessToken = getMinecraftToken(xblUserHash, xstsToken);

                // 6. Buscar Perfil Minecraft
                JsonObject profile = getMinecraftProfile(mcAccessToken);
                String name = getStringOrThrow(profile, "name", "MinecraftProfile");
                String id = getStringOrThrow(profile, "id", "MinecraftProfile");
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

                // BUG-1: isMicrosoft = true para contas autenticadas via Microsoft.
                // O construtor define isOffline = !isMicrosoft, então era crucial corrigir isso.
                GameProfile gameProfile = new GameProfile(name, uuid, true);
                gameProfile.setAccessToken(mcAccessToken);

                LOG.info("Login Microsoft concluído: {}", name);
                return gameProfile;

            } catch (InterruptedException e) {
                // Restaura flag de interrupção e propaga como exceção checked-friendly
                Thread.currentThread().interrupt();
                throw new RuntimeException("Login Microsoft interrompido", e);
            } catch (Exception e) {
                // FIX C-6: preserva stack trace original via 'cause' (antes era
                // 'throw new RuntimeException(e.getMessage())' que perdia a stack)
                LOG.error("Falha no login Microsoft", e);
                throw new RuntimeException(
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), e);
            }
        });
    }

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
                getStringOrThrow(json, "user_code", "DeviceCode"),
                getStringOrThrow(json, "verification_uri", "DeviceCode"),
                getStringOrThrow(json, "device_code", "DeviceCode"),
                getIntOrDefault(json, "interval", 5),
                getIntOrDefault(json, "expires_in", 900)
        );
    }

    private String pollForMicrosoftToken(DeviceCodeResponse dcr) throws IOException, InterruptedException {
        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                "&client_id=" + CLIENT_ID +
                "&device_code=" + dcr.device_code();

        long start = System.currentTimeMillis();
        long expiry = start + (dcr.expires_in() * 1000L);

        // FIX C-5: tracking de interval adaptativo (slow_down aumenta, authorization_pending mantém)
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
                return getStringOrThrow(json, "access_token", "TokenResponse");
            }

            String error = json.has("error") && !json.get("error").isJsonNull()
                    ? json.get("error").getAsString() : "";

            switch (error) {
                case "authorization_pending":
                    // Estado normal: usuário ainda não autorizou. Aguarda e tenta de novo.
                    break;
                case "slow_down":
                    // FIX C-5: Microsoft pediu pra esperar mais. Aumenta interval em 5s.
                    // Antes o código abortava o login nesse erro.
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
                    throw new IOException("Erro no polling Microsoft: " + error);
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

    /**
     * FIX C-4 (parte 1): extrai uhs com null check defensivo.
     * Antes: chain de .get(0).getAsJsonObject().get("uhs").getAsString() sem validação.
     */
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
        return getStringOrThrow(json, "access_token", "MinecraftToken");
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

    // =============== Helpers JSON defensivos (FIX C-4 parte 2) ===============

    private static String getStringOrThrow(JsonObject obj, String field, String context) {
        if (obj == null) {
            throw new IllegalStateException(context + ": resposta JSON nula");
        }
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull()) {
            throw new IllegalStateException(context + ": campo '" + field + "' ausente");
        }
        if (!el.isJsonPrimitive()) {
            throw new IllegalStateException(context + ": campo '" + field + "' não é primitivo");
        }
        return el.getAsString();
    }

    private static int getIntOrDefault(JsonObject obj, String field, int defaultValue) {
        if (obj == null) return defaultValue;
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return defaultValue;
        try { return el.getAsInt(); } catch (Exception e) { return defaultValue; }
    }
}
