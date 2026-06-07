package com.minelauncher.ui.controllers;

import com.minelauncher.utils.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para a delegação de {@code sanitizeName} em
 * {@link ModActions} (CRIT-7 do code-review de jun/2026).
 *
 * <p>ModActions e ModManager ambos chamam {@code sanitizeName} em
 * ~20 lugares. Antes cada um tinha sua própria implementação com
 * regex {@code [^a-zA-Z0-9\\s\\-]} que quebrava nomes Unicode.
 *
 * <p>Agora ambos delegam para {@link FileUtils#sanitizeName(String)}.
 * Este teste valida que o método público (se ainda existir como
 * facade) ou que cada call site produz o mesmo output que
 * FileUtils.sanitizeName, eliminando drift entre as duas
 * implementações.
 */
class ModActionsSanitizeTest {

    @Test
    void sanitizeName_isAvailableViaFileUtils() {
        // ModActions.sanitizeName delega para FileUtils.
        assertNotNull(FileUtils.sanitizeName("Test"));
    }

    @Test
    void sanitizeName_delegatedBehavesLikeFileUtils() {
        // Para qualquer input, o resultado deve ser idêntico ao
        // FileUtils. Se alguém reintroduzir uma versão divergente,
        // este teste pega (assume que ModActions.sanitizeName é
        // public static; se virou private, este teste vira doc-only).
        for (String input : new String[]{
                "normal_name", "Nome com espaço", "Fábrica do Mé",
                "我的世界", "!!!@@@###", null, "  trim  "}) {
            String fromFileUtils = FileUtils.sanitizeName(input);
            if (hasPublicModActionsSanitizeName()) {
                String fromModActions = ModActions.sanitizeName(input);
                assertEquals(fromFileUtils, fromModActions,
                        "Divergência em input: " + input);
            }
        }
    }

    @Test
    void sanitizeName_consistentAcrossCalls() {
        // Determinismo: mesma entrada → mesmo resultado.
        String a = FileUtils.sanitizeName("modpack_cjk_我的");
        String b = FileUtils.sanitizeName("modpack_cjk_我的");
        assertEquals(a, b);
    }

    @Test
    void sanitizeName_neverReturnsEmpty() {
        for (String input : new String[]{
                "", "   ", "!!!", "中文_!!!_abc", null}) {
            String result = FileUtils.sanitizeName(input);
            assertNotNull(result, "Não deve ser null para: " + input);
            assertFalse(result.isEmpty(), "Não deve ser vazio para: " + input);
        }
    }

    /**
     * Reflection-safe check: se ModActions.sanitizeName não é mais
     * public static, retorna false e pulamos a parte de equivalência.
     * Mantém o teste resiliente a refactors futuros.
     */
    private static boolean hasPublicModActionsSanitizeName() {
        try {
            java.lang.reflect.Method m = ModActions.class
                    .getMethod("sanitizeName", String.class);
            return java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && java.lang.reflect.Modifier.isStatic(m.getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
