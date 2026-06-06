package com.minelauncher.ui.services;

import com.minelauncher.utils.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private File gameDir;
    private File worldDir;
    private File backupBaseDir;
    private BackupService backupService;

    @BeforeEach
    void setUp() throws IOException {
        // BUG-6: agora o BackupService recebe (worldName, gameDir, backupBaseDir).
        // O teste cria um gameDir/saves/<world> para refletir o layout real.
        gameDir = tempDir.resolve("game").toFile();
        File savesDir = new File(gameDir, "saves");
        worldDir = new File(savesDir, "world");
        worldDir.mkdirs();
        Files.writeString(worldDir.toPath().resolve("level.dat"), "dummy");

        backupBaseDir = tempDir.resolve("backups").toFile();
        backupBaseDir.mkdirs();

        backupService = new BackupService();
    }

    @Test
    void testBackupAndRotation() throws IOException, InterruptedException {
        // 1. Create 4 backups to trigger rotation (max is 3)
        for (int i = 0; i < 4; i++) {
            backupService.createSnapshot("world", gameDir, backupBaseDir);
            // Sleep to ensure different timestamps
            Thread.sleep(100);
        }

        File[] backups = backupBaseDir.listFiles(File::isDirectory);
        assertNotNull(backups);
        assertEquals(3, backups.length, "Should only keep the last 3 backups");
    }

    @Test
    void testBackupContent() throws IOException {
        backupService.createSnapshot("world", gameDir, backupBaseDir);

        File[] backups = backupBaseDir.listFiles(File::isDirectory);
        assertNotNull(backups);
        File firstBackup = backups[0];
        assertTrue(new File(firstBackup, "level.dat").exists(), "Backup should contain world files");
    }

    @Test
    void testRejectsBlankWorldName() {
        assertThrows(IllegalArgumentException.class,
                () -> backupService.createSnapshot("", gameDir, backupBaseDir));
        assertThrows(IllegalArgumentException.class,
                () -> backupService.createSnapshot(null, gameDir, backupBaseDir));
    }

    /**
     * BUG-5: a sobrecarga (File, File) é usada pelo GameLaunchService para
     * fazer backup do diretório saves/ inteiro. Garante que ela continua
     * funcionando após a refatoração.
     */
    @Test
    void testFileOverloadBackupsSavesDir() throws IOException {
        // Cria um segundo world no savesDir para garantir que TUDO é copiado
        File otherWorld = new File(gameDir, "saves/otherWorld");
        otherWorld.mkdirs();
        Files.writeString(otherWorld.toPath().resolve("data"), "x");

        File savesDir = new File(gameDir, "saves");
        backupService.createSnapshot(savesDir, backupBaseDir);

        File[] backups = backupBaseDir.listFiles(File::isDirectory);
        assertNotNull(backups);
        assertEquals(1, backups.length, "Deve criar um único backup do saves/");
        File firstBackup = backups[0];
        assertTrue(firstBackup.getName().startsWith("saves_"),
                "Nome do backup deve começar com 'saves_' (nome do dir)");
        // O backup deve conter ambos os worlds dentro
        assertTrue(new File(firstBackup, "world/level.dat").exists(),
                "Backup deve conter world/level.dat");
        assertTrue(new File(firstBackup, "otherWorld/data").exists(),
                "Backup deve conter otherWorld/data");
    }

    /**
     * A sobrecarga (File, File) deve ser no-op silencioso se o dir não existir
     * (preserva o comportamento original do BackupService).
     */
    @Test
    void testFileOverloadSkipsMissingDir() throws IOException {
        File missing = new File(gameDir, "nao-existe");
        backupService.createSnapshot(missing, backupBaseDir);

        File[] backups = backupBaseDir.listFiles();
        assertNotNull(backups);
        assertEquals(0, backups.length, "Não deve criar backup de diretório inexistente");
    }
}
