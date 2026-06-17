package com.minelauncher.launcher;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Instância singleton do OkHttpClient compartilhada entre os módulos.
 *
 * <p><b>HIGH-15 do code-review:</b> a versão anterior não identificava
 * o launcher nas chamadas HTTP. O Mojang, Modrinth, CurseForge-proxy e
 * GitHub Releases não conseguem distinguir "MineLauncher 1.0" de "bot
 * scraping genérico", e podem aplicar rate limit mais agressivo ou
 * bloquear. Adicionamos:
 * <ol>
 *   <li>Interceptor que adiciona {@code User-Agent: MineLauncher/<version>}
 *       em TODA requisição (não só nas chamadas em ModManager, que já
 *       faziam manualmente).</li>
 *   <li>Hook para CertificatePinner via variável de sistema
 *       {@code minelauncher.pin.hashes} (formato {@code host1=hash1,host2=hash2}).
 *       Em produção, deixa-se sem pinning para não quebrar com rotação
 *       de certificados; operadores podem habilitar via env var.</li>
 * </ol>
 */
public final class HttpClient {

    /** Versão do launcher injetada no User-Agent. Bump aqui a cada release. */
    public static final String LAUNCHER_VERSION = com.minelauncher.utils.AppConstants.APP_VERSION;
    public static final String USER_AGENT = "MineLauncher/" + LAUNCHER_VERSION;

    private static final OkHttpClient INSTANCE = build();

    private static OkHttpClient build() {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(new UserAgentInterceptor());

        // HIGH-15: pinning opcional via env var. Exemplo:
        //   minelauncher.pin.hashes=api.minecraftservices.com=spki/xxx,api.modrinth.com=spki/yyy
        // Em produção, deixe desligado. Em redes com proxy MITM controlado,
        // habilite para defender contra CA compromise.
        String pinSpec = System.getProperty("minelauncher.pin.hashes",
                System.getenv("MINELAUNCHER_PIN_HASHES"));
        if (pinSpec != null && !pinSpec.isBlank()) {
            try {
                okhttp3.CertificatePinner.Builder pb = new okhttp3.CertificatePinner.Builder();
                for (String entry : pinSpec.split(",")) {
                    String[] parts = entry.trim().split("=", 2);
                    if (parts.length == 2) {
                        pb.add(parts[0].trim(), parts[1].trim());
                    }
                }
                b.certificatePinner(pb.build());
            } catch (Exception e) {
                // Não deixa um pinning mal-formatado derrubar o launcher
                System.err.println("[HttpClient] Falha ao aplicar certificate pinner: " + e.getMessage());
            }
        }

        return b.build();
    }

    private HttpClient() {}

    public static OkHttpClient getInstance() {
        return INSTANCE;
    }

    /** Adiciona User-Agent em toda requisição. */
    private static final class UserAgentInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            okhttp3.Request original = chain.request();
            // Se o caller já setou um User-Agent, respeita (override explícito).
            if (original.header("User-Agent") != null) {
                return chain.proceed(original);
            }
            return chain.proceed(original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build());
        }
    }
}
