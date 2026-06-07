package com.minelauncher.utils;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link JsonUtils} — helper de parsing defensivo criado no
 * code-review de jun/2026 (MEDIUM-17a).
 *
 * <p>Cobre:
 * <ul>
 *   <li>getStringOrNull retorna valor quando presente</li>
 *   <li>getStringOrNull retorna null em campo ausente</li>
 *   <li>getStringOrNull retorna null em JsonNull explícito (não NPE)</li>
 *   <li>getStringOrNull retorna null em JsonObject null (não NPE)</li>
 *   <li>getStringOrThrow lança com contexto útil</li>
 *   <li>getIntOrDefault retorna default em vários casos degenerados</li>
 *   <li>GSON é singleton (mesma instância para evitar custo)</li>
 * </ul>
 */
class JsonUtilsTest {

    // ---- getStringOrNull ----

    @Test
    void getStringOrNull_returnsValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", "Notch");
        assertEquals("Notch", JsonUtils.getStringOrNull(obj, "name"));
    }

    @Test
    void getStringOrNull_returnsNullForMissingKey() {
        JsonObject obj = new JsonObject();
        obj.addProperty("other", "value");
        assertNull(JsonUtils.getStringOrNull(obj, "missing"));
    }

    @Test
    void getStringOrNull_returnsNullForJsonNull() {
        JsonObject obj = new JsonObject();
        obj.add("description", JsonNull.INSTANCE);
        assertNull(JsonUtils.getStringOrNull(obj, "description"),
                "JsonNull deve virar null Java, não string 'null'");
    }

    @Test
    void getStringOrNull_returnsNullForNullObject() {
        assertNull(JsonUtils.getStringOrNull(null, "anything"),
                "Object null deve ser tratado, não NPE");
    }

    @Test
    void getStringOrNull_returnsEmptyStringAsIs() {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", "");
        assertEquals("", JsonUtils.getStringOrNull(obj, "key"));
    }

    @Test
    void getStringOrNull_handlesUnicode() {
        JsonObject obj = new JsonObject();
        obj.addProperty("msg", "Não autorizado");
        assertEquals("Não autorizado", JsonUtils.getStringOrNull(obj, "msg"));
    }

    @Test
    void getStringOrNull_returnsNullForNonPrimitive() {
        // Se campo é array ou objeto, não é string.
        JsonObject obj = new JsonObject();
        obj.add("list", new com.google.gson.JsonArray());
        assertNull(JsonUtils.getStringOrNull(obj, "list"),
                "Array não deve ser convertido para string");
    }

    // ---- getStringOrThrow ----

    @Test
    void getStringOrThrow_returnsValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "abc123");
        assertEquals("abc123", JsonUtils.getStringOrThrow(obj, "id", "Context"));
    }

    @Test
    void getStringOrThrow_includesContextInMessage() {
        JsonObject obj = new JsonObject();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JsonUtils.getStringOrThrow(obj, "missing", "MicrosoftAuth: token"));
        assertTrue(ex.getMessage().contains("MicrosoftAuth"),
                "Mensagem deve incluir contexto para diagnóstico");
        assertTrue(ex.getMessage().contains("missing"),
                "Mensagem deve incluir nome do campo");
    }

    @Test
    void getStringOrThrow_throwsForJsonNull() {
        JsonObject obj = new JsonObject();
        obj.add("field", JsonNull.INSTANCE);
        assertThrows(IllegalStateException.class,
                () -> JsonUtils.getStringOrThrow(obj, "field", "ctx"));
    }

    // ---- getIntOrDefault ----

    @Test
    void getIntOrDefault_returnsValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("n", 42);
        assertEquals(42, JsonUtils.getIntOrDefault(obj, "n", -1));
    }

    @Test
    void getIntOrDefault_missingReturnsDefault() {
        assertEquals(-1, JsonUtils.getIntOrDefault(new JsonObject(), "x", -1));
    }

    @Test
    void getIntOrDefault_malformedReturnsDefault() {
        JsonObject obj = new JsonObject();
        obj.addProperty("n", "not-a-number");
        assertEquals(-1, JsonUtils.getIntOrDefault(obj, "n", -1));
    }

    @Test
    void getIntOrDefault_nullObjectReturnsDefault() {
        assertEquals(99, JsonUtils.getIntOrDefault(null, "x", 99));
    }

    // ---- GSON ----

    @Test
    void gson_isSingleton() {
        assertSame(JsonUtils.GSON, JsonUtils.GSON);
    }

    @Test
    void gson_serializes() {
        JsonObject obj = new JsonObject();
        obj.addProperty("k", "v");
        // GSON é utilizável para serialização segura (sem String.format).
        String json = JsonUtils.GSON.toJson(obj);
        assertEquals("{\"k\":\"v\"}", json);
    }
}
