package com.minelauncher.ui.services;

import com.minelauncher.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

public class BackupService {
    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final int MAX_BACKUPS = 3;

    /**
     * Cria um snapshot de um mundo específico.
     *
     * <p><b>HIGH-9 do code-review:</b> a checagem de symlinks + Zip Slip
     * defesa em profundidade é feita dentro de
     * {@link com.minelauncher.utils.FileUtils#copyDirectory(File, File)}.
     * Se um modpack malicioso contiver um symlink
     * {@code config/some_file -> /etc/passwd}, o copyDirectory pula
     * o link (logando warning). BackupService herda essa proteção
     * automaticamente.
     *
     * @param worldName      nome da pasta do mundo (dentro de gameDir/saves/)
     * @param gameDir        diretório-raiz do perfil (.minecraft ou custom)
     * @param backupBaseDir  pasta onde os snapshots são guardados
     */
    public void createSnapshot(String worldName, File gameDir, File backupBaseDir) throws IOException {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName é obrigatório");
        }
        File worldDir = new File(gameDir, "saves/" + worldName);
        if (!worldDir.exists()) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS"));
        File backupDir = new File(backupBaseDir, worldName + "_" + timestamp);

        LOG.info("Iniciando backup de {} para {}", worldName, backupDir.getAbsolutePath());

        // Copy directory
        FileUtils.copyDirectory(worldDir, backupDir);

        // Rotate: keep only last N
        File[] backups = backupBaseDir.listFiles((dir, name) -> name.startsWith(worldName + "_"));
        if (backups != null && backups.length > MAX_BACKUPS) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                FileUtils.deleteDirectory(backups[i]);
            }
        }
        LOG.info("Backup concluído e rotacionado.");
    }

    /**
     * Cria um snapshot de um diretório arbitrário (usado pelo GameLaunchService
     * para fazer backup do diretório {@code gameDir/saves/} inteiro).
     *
     * BUG-5: o GameLaunchService original passava o gameDir inteiro como
     * worldDir, o que fazia o BackupService copiar {@code .minecraft/} completo
     * (mods, versions, assets, etc.). Com o worldDir agora apontando para
     * {@code saves/}, o backup é apenas do conteúdo dessa pasta.
     *
     * @param worldDir       diretório a ser copiado (ex.: gameDir/saves/)
     * @param backupBaseDir  pasta onde os snapshots são guardados
     */
    public void createSnapshot(File worldDir, File backupBaseDir) throws IOException {
        if (worldDir == null || !worldDir.exists()) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS"));
        File backupDir = new File(backupBaseDir, worldDir.getName() + "_" + timestamp);

        LOG.info("Iniciando backup de {} para {}", worldDir.getName(), backupDir.getAbsolutePath());

        FileUtils.copyDirectory(worldDir, backupDir);

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
