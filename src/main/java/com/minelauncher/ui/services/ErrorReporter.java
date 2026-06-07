package com.minelauncher.ui.services;

import com.google.gson.Gson;
import com.minelauncher.launcher.HttpClient;
import com.minelauncher.settings.SettingsManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service para centralizar o reporte de erros.
 *
 * <p><b>CRIT-4 do code-review:</b> a versão anterior montava JSON com
 * {@code String.format} escapando apenas aspas duplas. Stack traces
 * contém {@code \n}, {@code \t}, {@code \\}, e caracteres de controle
 * Unicode que tornavam o JSON inválido e o reporte era silenciosamente
 * descartado pelo servidor. PII (user.home, java.version, paths
 * absolutos) era enviada sem opt-in.
 *
 * <p>Esta versão:
 * <ul>
 *   <li>Usa Gson (escapa corretamente todos os caracteres de controle).</li>
 *   <li>Envia o stack trace como array de strings (Gson quebra em linhas
 *       no servidor, melhor para parsing).</li>
 *   <li>Não inclui PII: java.version + os.name são suficientes para
 *       triagem; user.home e paths NÃO são enviados.</li>
 *   <li>Só envia se {@code errorReportUrl} estiver configurado E o
 *       usuário tiver habilitado o opt-in (desabilitado por default).</li>
 * </ul>
 */
public class ErrorReporter {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorReporter.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    public static void report(Throwable e, String context) {
        LOG.error("Contexto: {} | Erro: {}", context, e.getMessage(), e);

        SettingsManager sm = SettingsManager.getInstance();
        // CRIT-4: opt-in duplo. URL configurada + flag habilitada.
        // Default da flag é false → sem reporte mesmo com URL setada.
        if (!sm.isErrorReportingEnabled()) return;
        String endpoint = sm.getErrorReportUrl();
        if (endpoint == null || endpoint.isBlank()) return;

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("context", context == null ? "" : context);
            payload.put("message", e.getMessage() == null ? "" : e.getMessage());
            payload.put("type", e.getClass().getName());

            // PII filtrada: NÃO enviar user.home, paths absolutos, user.name.
            // Apenas info genérica suficiente para triagem.
            payload.put("java", System.getProperty("java.version"));
            payload.put("os", System.getProperty("os.name"));
            payload.put("arch", System.getProperty("os.arch"));

            // Stack trace como array. StringWriter/PrintWriter para preservar
            // a stack completa, depois Gson escapa \n/\t/Unicode corretamente
            // em cada elemento do array.
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            payload.put("stacktrace", sw.toString().split("\n"));

            // Causa encadeada (se houver)
            Throwable cause = e.getCause();
            if (cause != null) {
                Map<String, Object> causeMap = new LinkedHashMap<>();
                causeMap.put("type", cause.getClass().getName());
                causeMap.put("message", cause.getMessage() == null ? "" : cause.getMessage());
                StringWriter csw = new StringWriter();
                cause.printStackTrace(new PrintWriter(csw));
                causeMap.put("stacktrace", csw.toString().split("\n"));
                payload.put("cause", causeMap);
            }

            String json = GSON.toJson(payload);

            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build();

            HttpClient.getInstance().newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) {
                    LOG.warn("Falha ao enviar reporte de erro");
                }
                @Override public void onResponse(Call call, Response response) { response.close(); }
            });
        } catch (Exception ex) {
            LOG.warn("Falha ao preparar reporte de erro", ex);
        }
    }
}
