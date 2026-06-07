package com.minelauncher.ui.services;

import com.minelauncher.auth.MicrosoftAuth;
import com.minelauncher.models.GameProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link AuthService#refreshIfNeeded(GameProfile)} — CRIT-3
 * do code-review de jun/2026.
 *
 * <p>Cobre os contratos testáveis sem rede:
 * <ul>
 *   <li>profile null retorna null</li>
 *   <li>conta offline (isMicrosoft=false) é retornada sem modificação</li>
 *   <li>conta Microsoft com token válido (não expirado) → não tenta refresh</li>
 *   <li>conta Microsoft expirada SEM refresh token → exceção tipada</li>
 * </ul>
 *
 * <p>Casos que exigem rede (refresh bem-sucedido, IOException) não
 * são testados aqui — precisariam de MockWebServer, fora do escopo.
 */
class AuthServiceRefreshTest {

    private static GameProfile offlineAccount(String name) {
        GameProfile p = new GameProfile(name, UUID.randomUUID(), false);
        p.setOffline(true);
        return p;
    }

    private static GameProfile microsoftAccount(String name, String access, String refresh,
                                                long expiryMs) {
        GameProfile p = new GameProfile(name, UUID.randomUUID(), true);
        p.setMicrosoft(true);
        p.setAccessToken(access);
        p.setRefreshToken(refresh);
        p.setTokenExpiry(expiryMs);
        return p;
    }

    @Test
    void refreshIfNeeded_nullReturnsNull() {
        AuthService auth = new AuthService();
        assertNull(auth.refreshIfNeeded(null));
    }

    @Test
    void refreshIfNeeded_offlineAccountReturnsSame() {
        // Conta offline: isMicrosoft() == false → return same.
        AuthService auth = new AuthService();
        GameProfile offline = offlineAccount("Steve");
        assertSame(offline, auth.refreshIfNeeded(offline),
                "Conta offline deve ser retornada inalterada (sem refresh)");
    }

    @Test
    void refreshIfNeeded_microsoftWithValidTokenReturnsSame() {
        // Token ainda válido (expiry no futuro) → não tenta refresh.
        AuthService auth = new AuthService();
        GameProfile ms = microsoftAccount(
                "uuid-name", "access_token_valid", "refresh_token",
                System.currentTimeMillis() + 3600_000L);

        assertSame(ms, auth.refreshIfNeeded(ms),
                "Token válido não deve disparar refresh");
    }

    @Test
    void refreshIfNeeded_microsoftExpiredWithoutRefreshTokenThrows() {
        // CRIT-3: conta Microsoft expirada SEM refresh token salvo
        // (ex: migração de versão antiga) deve lançar exceção tipada,
        // não silenciosamente continuar com token expirado.
        AuthService auth = new AuthService();
        GameProfile ms = microsoftAccount(
                "uuid-name", "access_token_expired", null,
                System.currentTimeMillis() - 1000L);

        MicrosoftAuth.RefreshTokenExpiredException ex = assertThrows(
                MicrosoftAuth.RefreshTokenExpiredException.class,
                () -> auth.refreshIfNeeded(ms));
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isBlank());
    }

    @Test
    void refreshIfNeeded_microsoftExpiredWithEmptyRefreshTokenThrows() {
        AuthService auth = new AuthService();
        GameProfile ms = microsoftAccount(
                "uuid-name", "access_token_expired", "",
                System.currentTimeMillis() - 1000L);

        assertThrows(MicrosoftAuth.RefreshTokenExpiredException.class,
                () -> auth.refreshIfNeeded(ms));
    }

    @Test
    void refreshIfNeeded_microsoftZeroExpiryIsExpired() {
        // tokenExpiry=0 → isTokenExpired() é true. Sem refresh token
        // → throw.
        AuthService auth = new AuthService();
        GameProfile ms = microsoftAccount(
                "uuid-name", "access_token", null, 0L);

        assertTrue(ms.isTokenExpired());
        assertThrows(MicrosoftAuth.RefreshTokenExpiredException.class,
                () -> auth.refreshIfNeeded(ms));
    }
}
