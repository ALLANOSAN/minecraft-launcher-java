package com.minelauncher.utils;

import com.minelauncher.models.LaunchProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link ModpackNameResolver}.
 *
 * <p>Cobre o fluxo de resolução de nome:
 * <ul>
 *   <li>launcher_manifest.json com nome válido</li>
 *   <li>manifest.json (CurseForge) com nome válido</li>
 *   <li>manifest com nome "lixo" (UUID) → fallback</li>
 *   <li>sem manifests → fallback para nome do diretório</li>
 *   <li>profile matching por gameDir</li>
 * </ul>
 */
class ModpackNameResolverTest {

    @Test
    void resolve_launcherManifestName(@TempDir Path tempDir) throws IOException {
        Path modpack = tempDir.resolve("MyCoolPack");
        Files.createDirectories(modpack);
        Files.writeString(modpack.resolve("launcher_manifest.json"),
                "{\"name\":\"Meu Modpack Incrível\"}");

        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("Meu Modpack Incrível", name);
    }

    @Test
    void resolve_curseForgeManifestName(@TempDir Path tempDir) throws IOException {
        Path modpack = tempDir.resolve("somepack");
        Files.createDirectories(modpack);
        Files.writeString(modpack.resolve("manifest.json"),
                "{\"name\":\"All the Mods 10\"}");

        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("All the Mods 10", name);
    }

    @Test
    void resolve_launcherManifestPreferredOverCurseForge(@TempDir Path tempDir) throws IOException {
        // Se ambos existirem, launcher_manifest tem prioridade
        Path modpack = tempDir.resolve("pack");
        Files.createDirectories(modpack);
        Files.writeString(modpack.resolve("launcher_manifest.json"),
                "{\"name\":\"Nome MineLauncher\"}");
        Files.writeString(modpack.resolve("manifest.json"),
                "{\"name\":\"Nome CurseForge\"}");

        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("Nome MineLauncher", name);
    }

    @Test
    void resolve_fallbackToDirName(@TempDir Path tempDir) {
        Path modpack = tempDir.resolve("JustAName");
        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("JustAName", name);
    }

    @Test
    void resolve_uuidDirName_usesProfileName(@TempDir Path tempDir) {
        Path modpack = tempDir.resolve("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        LaunchProfile profile = new LaunchProfile("MeuPerfil", "1.20.4");
        profile.setGameDir(modpack.toAbsolutePath().toString());

        String name = ModpackNameResolver.resolve(modpack.toFile(), List.of(profile));
        assertEquals("MeuPerfil", name);
    }

    @Test
    void resolve_uuidDirNameNoProfile_keepsUuid(@TempDir Path tempDir) {
        Path modpack = tempDir.resolve("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", name);
    }

    @Test
    void resolve_garbageNameInManifest_fallbackToDirName(@TempDir Path tempDir) throws IOException {
        // Manifest com nome "lixo" (hash) → deve cair no fallback
        Path modpack = tempDir.resolve("legitname");
        Files.createDirectories(modpack);
        Files.writeString(modpack.resolve("launcher_manifest.json"),
                "{\"name\":\"a1b2c3d4e5f6a1b2c3d4e5f6\"}");

        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("legitname", name);
    }

    @Test
    void resolve_nullDir_returnsNull() {
        assertNull(ModpackNameResolver.resolve(null, Collections.emptyList()));
    }

    @Test
    void resolve_invalidManifestJson_keepsDirName(@TempDir Path tempDir) throws IOException {
        Path modpack = tempDir.resolve("pack");
        Files.createDirectories(modpack);
        Files.writeString(modpack.resolve("launcher_manifest.json"),
                "{ isto não é JSON válido }");

        String name = ModpackNameResolver.resolve(modpack.toFile(), Collections.emptyList());
        assertEquals("pack", name, "Manifest corrompido deve cair no fallback");
    }

    @Test
    void looksLikeGarbage_uuid() {
        assertTrue(ModpackNameResolver.looksLikeGarbage("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
    }

    @Test
    void looksLikeGarbage_longHash() {
        assertTrue(ModpackNameResolver.looksLikeGarbage("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"));
    }

    @Test
    void looksLikeGarbage_normalName() {
        assertFalse(ModpackNameResolver.looksLikeGarbage("Meu Modpack"));
        assertFalse(ModpackNameResolver.looksLikeGarbage("All the Mods 10"));
        assertFalse(ModpackNameResolver.looksLikeGarbage("RLCraft"));
    }

    @Test
    void looksLikeGarbage_nullEmpty() {
        assertTrue(ModpackNameResolver.looksLikeGarbage(null));
        assertTrue(ModpackNameResolver.looksLikeGarbage(""));
        assertTrue(ModpackNameResolver.looksLikeGarbage("   "));
    }
}
