package com.minelauncher.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Helpers defensivos para parsing de JSON de APIs externas.
 *
 * <p><b>MEDIUM (code-review):</b> {@code MicrosoftAuth.getStringOrThrow}
 * e {@code getIntOrDefault} (private static) foram extraídos para cá
 * para reuso entre MicrosoftAuth, ModManager e qualquer novo cliente
 * de API. Reduz duplicação e centraliza a política de "campo ausente =
 * exceção tipada com contexto" — útil para diagnóstico de falhas em
 * runtime.
 */
public final class JsonUtils {

    /**
     * Instância compartilhada de Gson com pretty-print desabilitado
     * (compacta, ideal para serialização de payloads HTTP). Disponível
     * para callers que precisam serializar JSON de forma segura —
     * ModManager usa para construir facets do Modrinth (HIGH-13) sem
     * ter que montar strings concatenadas.
     */
    public static final Gson GSON = new Gson();

    private JsonUtils() {}

    /**
     * Lê uma string de um JsonObject. Lança IllegalStateException com
     * contexto se o objeto for nulo, o campo ausente/null, ou não
     * primitivo. O parâmetro {@code context} é incluído na mensagem
     * para facilitar diagnóstico ("MinecraftProfile: campo 'name'
     * ausente").
     */
    public static String getStringOrThrow(JsonObject obj, String field, String context) {
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

    /**
     * Lê um int de um JsonObject com default se o campo estiver
     * ausente/null/não-primitivo/parse-error. Não lança.
     */
    public static int getIntOrDefault(JsonObject obj, String field, int defaultValue) {
        if (obj == null) return defaultValue;
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return defaultValue;
        try { return el.getAsInt(); } catch (Exception e) { return defaultValue; }
    }

    /**
     * Lê uma string de um JsonObject retornando null se ausente. Não
     * lança — útil para campos opcionais.
     */
    public static String getStringOrNull(JsonObject obj, String field) {
        if (obj == null) return null;
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }
}
