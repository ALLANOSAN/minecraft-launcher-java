package com.minelauncher.launcher;

import com.minelauncher.models.VersionDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Responsável por montar o classpath e garantir que todas as libraries
 * de uma {@link VersionDetail} estejam presentes e íntegras em disco.
 *
 * <p>Extraído do {@code GameLauncher} (refactor por decomposição). Encapsula
 * duas responsabilidades correlatas:
 * <ul>
 *     <li>verificar SHA1 das libraries via sidecar marker
 *     {@code <lib>.sha1.ok} (CRIT-5 — pula verificação quando o marker
 *     existe e é mais novo que a library, evitando 1-5s por login em
 *     modpacks com 30+ libs);</li>
 *     <li>montar o classpath final (libs allowlisted + client jar).</li>
 * </ul>
 *
 * <p>State: nenhum (stateless, thread-safe).
 */
public class LibraryVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(LibraryVerifier.class);

    private final File baseDir;
    private final DownloadManager downloader;

    public LibraryVerifier(File baseDir, DownloadManager downloader) {
        this.baseDir = baseDir;
        this.downloader = downloader;
    }

    /**
     * Constrói o classpath juntando os caminhos absolutos de todas as
     * libraries allowlisted e do client jar (separados por
     * {@link File#pathSeparator}).
     */
    public String buildClasspath(VersionDetail detail, String versionId) throws IOException {
        // MEDIUM do code-review: defesa em profundidade contra path traversal.
        GameLauncher.assertSafeVersionId(versionId);

        StringBuilder classpath = new StringBuilder();

        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed()) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getArtifact() == null) continue;

            VersionDetail.DownloadFile artifact = lib.getDownloads().getArtifact();
            File libFile = new File(baseDir, "libraries/" + VersionManager.resolveLibraryPath(artifact));
            if (libFile.exists()) {
                classpath.append(libFile.getAbsolutePath()).append(File.pathSeparator);
            }
        }

        File clientJar = new File(baseDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (clientJar.exists()) {
            classpath.append(clientJar.getAbsolutePath());
        }

        return classpath.toString();
    }

    /**
     * Verifica e baixa libraries que estão faltando localmente.
     *
     * <p>CRIT-5 do code-review: a versão anterior chamava
     * {@code calculateSHA1()} em cada library em todo launch, gerando
     * um custo de 1-5s por login (proporcional à quantidade de libs).
     * Agora usamos um sidecar marker {@code <lib>.sha1.ok} criado pelo
     * {@link DownloadManager} quando um download com SHA1 esperado
     * conclui com sucesso. Pula a verificação se o marker existe E
     * é mais novo que a library. A verificação completa só é feita
     * quando o marker está ausente ou desatualizado.
     */
    public void verifyAndDownload(VersionDetail detail, String versionId) {
        // MEDIUM do code-review: defesa em profundidade contra path traversal.
        GameLauncher.assertSafeVersionId(versionId);

        int missing = 0;
        int downloaded = 0;
        int verifiedSkipped = 0;

        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed()) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getArtifact() == null) continue;

            VersionDetail.DownloadFile artifact = lib.getDownloads().getArtifact();
            File libFile = new File(baseDir, "libraries/" + VersionManager.resolveLibraryPath(artifact));

            boolean needsDownload = !libFile.exists();

            if (!needsDownload && artifact.getSha1() != null && !artifact.getSha1().isEmpty()) {
                // CRIT-5: sidecar marker. Se existe e é mais novo que a library,
                // confia no SHA1 sem recalcular.
                File marker = new File(libFile.getParentFile(), libFile.getName() + ".sha1.ok");
                if (marker.exists() && marker.lastModified() >= libFile.lastModified()) {
                    verifiedSkipped++;
                    continue;
                }
                try {
                    String actualSha1 = downloader.calculateSHA1(libFile);
                    if (!artifact.getSha1().equals(actualSha1)) {
                        LOG.warn("SHA1 mismatch para {}: esperado {}, obtido {}. Re-download...",
                                libFile.getName(), artifact.getSha1(), actualSha1);
                        needsDownload = true;
                    } else {
                        // Cria o sidecar para futuros launches pularem
                        Files.writeString(marker.toPath(), artifact.getSha1());
                        marker.setLastModified(libFile.lastModified());
                    }
                } catch (Exception e) {
                    LOG.warn("Erro ao verificar SHA1 de {}: {}", libFile.getName(), e.getMessage());
                    needsDownload = true;
                }
            }

            if (needsDownload) {
                missing++;
                try {
                    libFile.getParentFile().mkdirs();
                    downloader.download(artifact.getUrl(), libFile, artifact.getSha1(), null);
                    // CRIT-5: o downloader valida SHA1 quando expectedSha1 != null,
                    // então só escrevemos o sidecar se o download terminou OK.
                    if (artifact.getSha1() != null && !artifact.getSha1().isEmpty() && libFile.exists()) {
                        File marker = new File(libFile.getParentFile(), libFile.getName() + ".sha1.ok");
                        Files.writeString(marker.toPath(), artifact.getSha1());
                        marker.setLastModified(libFile.lastModified());
                    }
                    downloaded++;
                    LOG.info("Library baixada: {}", libFile.getName());
                } catch (Exception e) {
                    LOG.warn("Falha ao baixar library {}: {}", libFile.getName(), e.getMessage());
                }
            }
        }

        File clientJar = new File(baseDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (!clientJar.exists()) {
            LOG.warn("JAR do cliente não encontrado: {}", clientJar.getAbsolutePath());
        }

        if (missing > 0) {
            LOG.info("Libraries: {} faltantes, {} baixadas de {} ({} já verificadas via sidecar)",
                    missing, downloaded, detail.getLibraries().size(), verifiedSkipped);
        } else if (verifiedSkipped > 0) {
            LOG.debug("Libraries: todas presentes; {} puladas via sidecar", verifiedSkipped);
        }
    }
}
