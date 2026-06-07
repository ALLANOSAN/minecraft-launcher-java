package com.minelauncher.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para FileUtils (formatBytes, deleteDirectory, sanitizeName,
 * copyDirectory) — cobre código legado + fixes CRIT-7 e HIGH-9 do
 * code-review de jun/2026.
 */
class FileUtilsTest {

    // ---- formatBytes ----

    @Test
    void formatBytes_bytes() {
        assertEquals("512 B", FileUtils.formatBytes(512));
    }

    @Test
    void formatBytes_kilobytes() {
        assertEquals("1.5 KB", FileUtils.formatBytes(1536));
    }

    @Test
    void formatBytes_megabytes() {
        assertEquals("2.0 MB", FileUtils.formatBytes(2L * 1024 * 1024));
    }

    @Test
    void formatBytes_gigabytes() {
        assertEquals("1.50 GB", FileUtils.formatBytes(1610612736L));
    }

    @Test
    void formatBytes_zero() {
        assertEquals("0 B", FileUtils.formatBytes(0));
    }

    // ---- deleteDirectory ----

    @Test
    void deleteDirectory_simpleDir(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.toFile();
        Files.writeString(dir.toPath().resolve("a.txt"), "hello");
        Files.writeString(dir.toPath().resolve("b.txt"), "world");

        FileUtils.deleteDirectory(dir);

        assertFalse(dir.exists(), "Diretório deve ser removido");
    }

    @Test
    void deleteDirectory_nestedDirs(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("a/b/c/d.txt");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "deep");

        FileUtils.deleteDirectory(tempDir.toFile());

        assertFalse(tempDir.toFile().exists());
    }

    @Test
    void deleteDirectory_nonexistent() {
        File ghost = new File("/tmp/this/does/not/exist/12345");
        assertDoesNotThrow(() -> FileUtils.deleteDirectory(ghost));
    }

    @Test
    void deleteDirectory_idempotent(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.toFile();
        Files.writeString(dir.toPath().resolve("a.txt"), "x");

        FileUtils.deleteDirectory(dir);
        assertDoesNotThrow(() -> FileUtils.deleteDirectory(dir),
                "Segunda chamada em dir inexistente deve ser no-op");
    }

    @Test
    void deleteDirectory_preservesSiblings(@TempDir Path tempDir) throws IOException {
        File a = tempDir.resolve("a").toFile();
        File b = tempDir.resolve("b").toFile();
        a.mkdirs(); b.mkdirs();
        Files.writeString(a.toPath().resolve("x.txt"), "1");
        Files.writeString(b.toPath().resolve("y.txt"), "2");

        FileUtils.deleteDirectory(a);

        assertFalse(a.exists());
        assertTrue(b.exists(), "b não deve ser afetado");
        assertTrue(b.listFiles().length > 0);
    }

    // ---- CRIT-7: sanitizeName preserva Unicode (não-EN) ----

    @Test
    void sanitizeName_preservesLatinAccents() {
        // PT-BR: antes virava "Fabriosa do Me" ou similar; agora preserva.
        assertEquals("Fábrica_do_Mé", FileUtils.sanitizeName("Fábrica do Mé"));
    }

    @Test
    void sanitizeName_preservesCJK() {
        // CRIT-7: nome CJK que antes colapsava para "" agora preserva
        // o caractere chinês. Acentos chineses são \p{L}.
        String result = FileUtils.sanitizeName("我的世界");
        assertFalse(result.isEmpty(), "CJK não deve colapsar para vazio");
        assertTrue(result.contains("我"), "Deve preservar pelo menos um caractere CJK");
    }

    @Test
    void sanitizeName_preservesCyrillic() {
        // \p{L} inclui cirílico também.
        String result = FileUtils.sanitizeName("Привет мод");
        assertFalse(result.isEmpty());
        assertTrue(result.contains("П"));
    }

    @Test
    void sanitizeName_stripsSlashes() {
        // Slashes não são \p{L} nem \p{N} — devem ser removidos.
        // (Dots são mantidos porque estão em \-_. no allowlist.)
        String result = FileUtils.sanitizeName("mod/with/slashes");
        assertFalse(result.contains("/"),
                "Slashes devem ser removidos (separadores de path)");
    }

    @Test
    void sanitizeName_stripsShellMetachars() {
        // ; | & $ ` não são \p{L} nem \p{N} — devem ser removidos.
        String result = FileUtils.sanitizeName("foo;rm -rf $HOME | bar");
        assertFalse(result.contains(";"));
        assertFalse(result.contains("|"));
        assertFalse(result.contains("$"));
    }

    @Test
    void sanitizeName_nullReturnsUnnamed() {
        assertEquals("unnamed", FileUtils.sanitizeName(null));
    }

    @Test
    void sanitizeName_pureSymbolsFallbackDeterministic() {
        // String só com símbolos — regex esvazia, fallback usa hashCode.
        // CRIT-7: fallback deve ser determinístico (mesmo input → mesmo output).
        String a = FileUtils.sanitizeName("!!!@@@###");
        String b = FileUtils.sanitizeName("!!!@@@###");
        assertEquals(a, b, "Fallback deve ser determinístico");
        assertFalse(a.isEmpty(), "Não deve retornar vazio");
        assertTrue(a.startsWith("item_"), "Fallback usa prefixo 'item_'");
    }

    @Test
    void sanitizeName_collapsesMixedWhitespace() {
        // \s+ na regex inclui \n e \t. "a\nb\tc" → "a_b_c" (todos viram
        // um único _).
        assertEquals("a_b_c", FileUtils.sanitizeName("a\nb\tc"));
        assertEquals("hello_world", FileUtils.sanitizeName("hello   world"));
    }

    @Test
    void sanitizeName_keepsDotsUnderscoresDashes() {
        // Dots, underscores e dashes estão no allowlist (\\-_.) —
        // extensões comuns ou nomes compostos sobrevivem.
        assertEquals("1.20.1-forge", FileUtils.sanitizeName("1.20.1-forge"));
        assertEquals("mod_pack_v2", FileUtils.sanitizeName("mod_pack_v2"));
    }

    // ---- HIGH-9: copyDirectory recusa symlinks + Zip Slip ----

    @Test
    void copyDirectory_copiesRecursively(@TempDir Path tempDir) throws IOException {
        File src = tempDir.resolve("src").toFile();
        File dst = tempDir.resolve("dst").toFile();
        src.mkdirs();
        Files.writeString(src.toPath().resolve("a.txt"), "hello");
        Files.createDirectories(src.toPath().resolve("sub"));
        Files.writeString(src.toPath().resolve("sub/b.txt"), "world");

        FileUtils.copyDirectory(src, dst);

        assertEquals("hello", Files.readString(dst.toPath().resolve("a.txt")));
        assertEquals("world", Files.readString(dst.toPath().resolve("sub/b.txt")));
    }

    @Test
    void copyDirectory_skipsSymlinkInSource(@TempDir Path tempDir) throws IOException {
        // HIGH-9: symlink em modpack/backup source deve ser IGNORADO,
        // não seguido. Antes seguia e copiava o destino do link,
        // permitindo sobrescrever /etc/passwd se o link apontasse pra lá.
        File src = tempDir.resolve("src").toFile();
        File dst = tempDir.resolve("dst").toFile();
        src.mkdirs();
        Files.writeString(src.toPath().resolve("legit.txt"), "safe");

        // Cria symlink "passwd_leak" → /etc/passwd
        Path link = src.toPath().resolve("passwd_leak");
        try {
            Files.createSymbolicLink(link, Paths.get("/etc/passwd"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows sem permissão para symlink: skip
            return;
        }

        FileUtils.copyDirectory(src, dst);

        // legit.txt copiado
        assertTrue(Files.exists(dst.toPath().resolve("legit.txt")));
        // symlink NÃO copiado (recusado)
        assertFalse(Files.exists(dst.toPath().resolve("passwd_leak")),
                "Symlink deve ser pulado, não seguido");
    }

    @Test
    void copyDirectory_createsNestedDirs(@TempDir Path tempDir) throws IOException {
        File src = tempDir.resolve("src").toFile();
        File dst = tempDir.resolve("dst").toFile();
        src.mkdirs();
        Files.createDirectories(src.toPath().resolve("a/b/c/d"));
        Files.writeString(src.toPath().resolve("a/b/c/d/deep.txt"), "buried");

        FileUtils.copyDirectory(src, dst);

        assertEquals("buried", Files.readString(dst.toPath().resolve("a/b/c/d/deep.txt")));
    }
}
