package com.minelauncher.launcher;

import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

/**
 * Instância singleton do OkHttpClient compartilhada entre os módulos.
 * Reduz consumo de memória e pool de conexões.
 */
public final class HttpClient {

    private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private HttpClient() {}

    public static OkHttpClient getInstance() {
        return INSTANCE;
    }
}
