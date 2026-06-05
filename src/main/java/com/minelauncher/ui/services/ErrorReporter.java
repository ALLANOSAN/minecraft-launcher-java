package com.minelauncher.ui.services;

import com.minelauncher.launcher.HttpClient;
import com.minelauncher.settings.SettingsManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service para centralizar o reporte de erros.
 * Envia relatórios de exceções críticas para um endpoint configurado.
 */
public class ErrorReporter {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorReporter.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void report(Throwable e, String context) {
        LOG.error("Contexto: {} | Erro: {}", context, e.getMessage(), e);
        
        String endpoint = SettingsManager.getInstance().getErrorReportUrl();
        if (endpoint == null || endpoint.isBlank()) return;

        try {
            String json = String.format("{\"context\":\"%s\", \"message\":\"%s\", \"stacktrace\":\"%s\"}", 
                                        context, e.getMessage().replace("\"", "\\\""), 
                                        java.util.Arrays.toString(e.getStackTrace()).replace("\"", "\\\""));
            
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build();
            
            HttpClient.getInstance().newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) { LOG.warn("Falha ao enviar reporte de erro"); }
                @Override public void onResponse(Call call, Response response) { response.close(); }
            });
        } catch (Exception ex) {
            LOG.warn("Falha ao preparar reporte de erro", ex);
        }
    }
}
