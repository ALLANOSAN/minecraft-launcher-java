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

    private File worldDir;
    private File backupBaseDir;
    private BackupService backupService;

    @BeforeEach
    void setUp() throws IOException {
        worldDir = tempDir.resolve("world").toFile();
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
            backupService.createSnapshot(worldDir, backupBaseDir);
            // Sleep to ensure different timestamps
            Thread.sleep(100);
        }

        File[] backups = backupBaseDir.listFiles(File::isDirectory);
        assertNotNull(backups);
        assertEquals(3, backups.length, "Should only keep the last 3 backups");
    }

    @Test
    void testBackupContent() throws IOException {
        backupService.createSnapshot(worldDir, backupBaseDir);
        
        File[] backups = backupBaseDir.listFiles(File::isDirectory);
        assertNotNull(backups);
        File firstBackup = backups[0];
        assertTrue(new File(firstBackup, "level.dat").exists(), "Backup should contain world files");
    }
}
