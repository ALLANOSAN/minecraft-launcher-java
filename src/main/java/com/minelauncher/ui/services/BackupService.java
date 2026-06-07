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
     * Regex defensiva para worldName: aceita letras Unicode, dígitos,
     * espaços, hífen, sublinhado e ponto. Rejeita separadores de path
     * (/, \, NUL) e qualquer caractere fora desse conjunto.
     * <p>INFO-1 do security-review: o caller atual ({@code MainController.backupWorld})
     * já passa apenas nomes vindos de {@code File.listFiles()}, então
     * esta validação é redundante na prática — mas fecha a API surface
     * contra um caller futuro que receba {@code worldName} de input externo.
     */
    private static final java.util.regex.Pattern SAFE_WORLD_NAME =
            java.util.regex.Pattern.compile("[\\p{L}\\p{N} \\-_.]+");

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
     * @throws IllegalArgumentException se {@code worldName} for null, blank,
     *         contiver separador de path, NUL ou caractere fora do conjunto
     *         seguro (defesa contra path traversal — INFO-1 do security-review)
     */
    public void createSnapshot(String worldName, File gameDir, File backupBaseDir) throws IOException {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName é obrigatório");
        }
        // INFO-1: rejeita path traversal e chars de controle antes de montar
        // o File. Defesa em profundidade: o caller já passa nomes seguros
        // (vindos de File.listFiles()), mas a API pública não pode confiar nisso.
        // "." e ".." são casos especiais — a regex abaixo os aceitaria (são dots),
        // mas semanticamente eles escapam do diretório saves/ (new File("saves/..")
        // resolve para o gameDir pai).
        if (worldName.equals(".") || worldName.equals("..")
                || worldName.contains("/") || worldName.contains("\\")
                || worldName.indexOf('\0') >= 0
                || !SAFE_WORLD_NAME.matcher(worldName).matches()) {
            throw new IllegalArgumentException(
                    "worldName inválido (path traversal ou caractere de controle): \""
                            + worldName.replace("\0", "?") + "\"");
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
