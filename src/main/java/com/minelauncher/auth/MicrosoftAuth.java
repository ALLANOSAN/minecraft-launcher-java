package com.minelauncher.auth;

import com.google.gson.JsonArray;
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

    /**
     * Resposta do pedido de Device Code
     */
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
                String xblToken = xblResponse.get("Token").getAsString();
                String xblUserHash = xblResponse.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

                // 4. Autenticar no XSTS
                JsonObject xstsResponse = authenticateXSTS(xblToken);
                String xstsToken = xstsResponse.get("Token").getAsString();

                // 5. Obter Minecraft Access Token
                String mcAccessToken = getMinecraftToken(xblUserHash, xstsToken);

                // 6. Buscar Perfil Minecraft
                JsonObject profile = getMinecraftProfile(mcAccessToken);
                String name = profile.get("name").getAsString();
                String id = profile.get("id").getAsString();
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

                GameProfile gameProfile = new GameProfile(name, uuid, false);
                gameProfile.setAccessToken(mcAccessToken);
                
                LOG.info("Login Microsoft concluído: {}", name);
                return gameProfile;

            } catch (Exception e) {
                LOG.error("Falha no login Microsoft", e);
                throw new RuntimeException(e.getMessage());
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
        if (response.statusCode() != 200) throw new IOException("Erro ao pedir device code: " + response.body());
        
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new DeviceCodeResponse(
                json.get("user_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("device_code").getAsString(),
                json.get("interval").getAsInt(),
                json.get("expires_in").getAsInt()
        );
    }

    private String pollForMicrosoftToken(DeviceCodeResponse dcr) throws IOException, InterruptedException {
        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                "&client_id=" + CLIENT_ID +
                "&device_code=" + dcr.device_code();

        long start = System.currentTimeMillis();
        long expiry = start + (dcr.expires_in() * 1000L);

        while (System.currentTimeMillis() < expiry) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (response.statusCode() == 200) {
                return json.get("access_token").getAsString();
            }

            String error = json.has("error") ? json.get("error").getAsString() : "";
            if ("authorization_pending".equals(error)) {
                Thread.sleep(dcr.interval() * 1000L);
            } else {
                throw new IOException("Erro no polling Microsoft: " + error);
            }
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
        if (response.statusCode() != 200) throw new IOException("Erro XBL: " + response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
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
        if (response.statusCode() != 200) throw new IOException("Erro XSTS: " + response.body());
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
        if (response.statusCode() != 200) throw new IOException("Erro Minecraft Login: " + response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject().get("access_token").getAsString();
    }

    private JsonObject getMinecraftProfile(String mcAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Erro Perfil Minecraft: " + response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
