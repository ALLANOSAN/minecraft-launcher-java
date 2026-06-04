package com.minelauncher.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link FileUtils}.
 *
 * <p>Cobre:
 * <ul>
 *   <li>formatBytes em todos os limites (B, KB, MB, GB)</li>
 *   <li>deleteDirectory em diretórios aninhados</li>
 *   <li>deleteDirectory idempotente (rodar 2x não dá exception)</li>
 *   <li>deleteDirectory em diretório inexistente</li>
 * </ul>
 */
class FileUtilsTest {

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
        // Não deve lançar — silently skip
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
}
