package com.minelauncher.ui.services;

import com.minelauncher.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

public class BackupService {
    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final int MAX_BACKUPS = 3;

    public void createSnapshot(File worldDir, File backupBaseDir) throws IOException {
        if (!worldDir.exists()) return;
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        File backupDir = new File(backupBaseDir, worldDir.getName() + "_" + timestamp);
        
        LOG.info("Iniciando backup de {} para {}", worldDir.getName(), backupDir.getAbsolutePath());
        
        // Copy directory
        FileUtils.copyDirectory(worldDir, backupDir);
        
        // Rotate: keep only last N
        File[] backups = backupBaseDir.listFiles((dir, name) -> name.startsWith(worldDir.getName() + "_"));
        if (backups != null && backups.length > MAX_BACKUPS) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                FileUtils.deleteDirectory(backups[i]);
            }
        }
        LOG.info("Backup concluído e rotacionado.");
    }
}
