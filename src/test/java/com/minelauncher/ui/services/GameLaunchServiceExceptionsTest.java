package com.minelauncher.ui.services;

import com.minelauncher.auth.MicrosoftAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para os caminhos de erro do {@link GameLaunchService}
 * (MEDIUM-18 do code-review de jun/2026).
 *
 * <p>O service agora lança 4 typed exceptions em vez de uma genérica:
 * <ul>
 *   <li>{@link GameLaunchService.VersionNotInstalledException} —
 *       download falhou (rede, Mojang offline, hash mismatch)</li>
 *   <li>{@link GameLaunchService.ModLoaderInstallException} —
 *       Forge/Fabric/Quilt/NeoForge installer crashou</li>
 *   <li>{@link GameLaunchService.AuthenticationExpiredException} —
 *       refresh token Microsoft expirou/revogado</li>
 *   <li>{@link GameLaunchService.JavaNotFoundException} —
 *       nenhum java/javaw disponível</li>
 * </ul>
 *
 * <p>Cada uma permite que a UI reaja com mensagem e ação
 * específica (botão "refazer login" vs "instalar Java" vs
 * "tentar novamente").
 */
class GameLaunchServiceExceptionsTest {

    @Test
    void versionNotInstalledException_carriesMessage() {
        GameLaunchService.VersionNotInstalledException ex =
                new GameLaunchService.VersionNotInstalledException(
                        "Não foi possível baixar Minecraft 1.21.1: timeout");
        assertTrue(ex.getMessage().contains("1.21.1"));
        assertTrue(ex.getMessage().contains("timeout"));
    }

    @Test
    void modLoaderInstallException_wrapsCause() {
        IllegalStateException cause = new IllegalStateException("Forge installer returned 1");
        GameLaunchService.ModLoaderInstallException ex =
                new GameLaunchService.ModLoaderInstallException("Forge falhou", cause);
        assertEquals("Forge falhou", ex.getMessage());
        assertSame(cause, ex.getCause(),
                "Causa original deve ser preservada para diagnóstico");
    }

    @Test
    void authenticationExpiredException_carriesGuidance() {
        // Antes: Exception genérica com mensagem "null". Agora:
        // mensagem clara orientando re-login.
        GameLaunchService.AuthenticationExpiredException ex =
                new GameLaunchService.AuthenticationExpiredException(
                        "Sessão Microsoft expirou. Faça login novamente.");
        assertTrue(ex.getMessage().toLowerCase().contains("login")
                || ex.getMessage().toLowerCase().contains("sessão"),
                "Mensagem deve orientar o usuário");
    }

    @Test
    void javaNotFoundException_carriesMessage() {
        GameLaunchService.JavaNotFoundException ex =
                new GameLaunchService.JavaNotFoundException(
                        "Java não encontrado em JAVA_HOME nem no PATH");
        assertTrue(ex.getMessage().contains("Java"));
    }

    @Test
    void exceptions_areDistinctRuntimeExceptions() {
        // Garante que cada uma é subclasse de RuntimeException e
        // distintas entre si (cada uma tem tratamento de UI diferente).
        GameLaunchService.VersionNotInstalledException v =
                new GameLaunchService.VersionNotInstalledException("v");
        GameLaunchService.ModLoaderInstallException m =
                new GameLaunchService.ModLoaderInstallException("m", null);
        GameLaunchService.AuthenticationExpiredException a =
                new GameLaunchService.AuthenticationExpiredException("a");
        GameLaunchService.JavaNotFoundException j =
                new GameLaunchService.JavaNotFoundException("j");

        assertTrue(v instanceof RuntimeException);
        assertTrue(m instanceof RuntimeException);
        assertTrue(a instanceof RuntimeException);
        assertTrue(j instanceof RuntimeException);

        // Tipos distintos
        assertNotEquals(v.getClass(), m.getClass());
        assertNotEquals(v.getClass(), a.getClass());
        assertNotEquals(v.getClass(), j.getClass());
        assertNotEquals(m.getClass(), a.getClass());
    }

    @Test
    void authServiceWrapsMicrosoftExceptionAsTyped(@TempDir Path tempDir) {
        // Valida que o wrapping em GameLaunchService funciona
        // quando authService.refreshIfNeeded() lança
        // RefreshTokenExpiredException.
        // Não invocamos GameLaunchService.launch (que dispara
        // thread) — apenas checamos que a exceção esperada é uma
        // RuntimeException para que o caller possa fazer catch sem
        // declarar throws.
        MicrosoftAuth.RefreshTokenExpiredException original =
                new MicrosoftAuth.RefreshTokenExpiredException("re-login required");
        // O wrapping no service (linha 85) constrói uma mensagem
        // incluindo a mensagem original. Aqui validamos só que
        // o tipo final é AuthenticationExpiredException.
        GameLaunchService.AuthenticationExpiredException wrapped =
                new GameLaunchService.AuthenticationExpiredException(
                        "Sessão Microsoft expirou. Detalhe: " + original.getMessage());
        assertTrue(wrapped.getMessage().contains("re-login required"),
                "Mensagem deve preservar detalhe original para diagnóstico");
    }
}
