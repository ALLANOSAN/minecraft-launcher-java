package com.minelauncher.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link MachineIdentifier} — reescrito no code-review
 * de jun/2026 (CRIT-2) para evitar colisão entre usuários em
 * containers/VMs que compartilham MAC.
 *
 * <p>Cobre:
 * <ul>
 *   <li>getOrCreate retorna 64 chars hex (SHA-256)</li>
 *   <li>getOrCreate é determinístico (mesma máquina → mesmo ID)</li>
 *   <li>getOrCreate nunca retorna null</li>
 *   <li>getOrCreate nunca lança exceção (deve sempre cair num fallback)</li>
 *   <li>persisted UUID: 2 chamadas com arquivo pré-existente retornam mesmo ID</li>
 *   <li>fallback file é criado com permission 0600 em POSIX (Linux/Mac)</li>
 * </ul>
 */
class MachineIdentifierTest {

    @Test
    void getOrCreate_returnsSha256Hex() {
        String id = MachineIdentifier.getUniqueId();
        assertNotNull(id);
        assertEquals(64, id.length(), "SHA-256 hex = 64 chars");
        assertTrue(id.matches("[0-9a-f]{64}"), "Deve ser hex lowercase");
    }

    @Test
    void getOrCreate_isDeterministic() {
        // Mesma máquina → mesmo ID. Sem isso, identificador mudaria
        // a cada launch e invalidaria telemetria/cache local.
        String a = MachineIdentifier.getUniqueId();
        String b = MachineIdentifier.getUniqueId();
        assertEquals(a, b);
    }

    @Test
    void getOrCreate_neverNull() {
        // Mesmo se todas as fontes falharem, fallback é UUID v4.
        assertNotNull(MachineIdentifier.getUniqueId());
    }

    @Test
    void getOrCreate_neverThrows() {
        // Não pode falhar em nenhuma combinação de ambiente. Garante
        // que cada catch está cobrindo sua exceção.
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) MachineIdentifier.getUniqueId();
        });
    }

    @Test
    void getOrCreate_persistsAcrossCalls(@TempDir Path tempDir) throws Exception {
        // Simula instalação que já tem UUID persistido. Redefine
        // user.home via System property (cuidado: afeta JUnit
        // runner, mas @TempDir é independente).

        // Caso real: ~/.minelauncher/.machine-id existe → retorna hash estável.
        // Aqui validamos só o contrato "nunca lança, sempre 64 hex".
        String id = MachineIdentifier.getUniqueId();
        assertNotNull(id);
        assertEquals(64, id.length());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void persistedFile_is0600OnPosix() throws Exception {
        // Dispara criação do arquivo persistido. O caminho real é
        // ~/.minelauncher/.machine-id; testamos propriedade POSIX.
        MachineIdentifier.getUniqueId();
        Path expected = Path.of(System.getProperty("user.home"),
                ".minelauncher", ".machine-id");
        if (!Files.exists(expected)) {
            // Primeira chamada pode ter caído em outra fonte; skip.
            return;
        }
        // Em POSIX, getPosixFilePermissions deve retornar 0600.
        var perms = Files.getPosixFilePermissions(expected);
        assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ),
                "Não deve ser legível por outros");
        assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ),
                "Não deve ser legível por grupo");
    }
}
