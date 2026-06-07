package com.minelauncher.launcher;

import com.minelauncher.models.VersionDetail;
import com.minelauncher.utils.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extrai libraries nativas (.so/.dll/.dylib/.jnilib) dos jars classificados
 * como natives para a versão, e devolve o caminho do diretório de destino.
 *
 * <p>Extraído do {@code GameLauncher} (refactor por decomposição). A
 * otimização HIGH-10 (sidecar marker {@code .extracted} com mtime combinado
 * dos jars-fonte) vive aqui — ela é puramente sobre extração, sem
 * responsabilidade sobre o processo Java em si.
 *
 * <p>Inclui proteção Zip Slip: compara o caminho canônico de cada entry
 * com o canônico do diretório de destino e aborta a extração se houver
 * tentativa de path traversal.
 *
 * <p>State: nenhum (stateless, thread-safe — toda mutação é em disco
 * no diretório de natives da versão).
 */
public class NativesExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(NativesExtractor.class);

    private final File baseDir;

    public NativesExtractor(File baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Garante que o diretório de natives para a versão existe, extrai
     * os arquivos necessários dos jars classificados como natives para o
     * SO atual (pulando re-extração via marker se nada mudou) e devolve
     * o caminho absoluto do diretório.
     *
     * @param detail   detail da versão a ser lançada
     * @param versionId id da versão (vanilla ou modded, mesmo formato
     *                  usado em {@code versions/<id>/natives/})
     */
    public String getNativesDir(VersionDetail detail, String versionId) {
        // MEDIUM do code-review: defesa em profundidade contra path
        // traversal via versionId. GameLauncher.launch() já valida, mas
        // esta classe é pública no package e pode ser chamada de outros
        // pontos no futuro.
        GameLauncher.assertSafeVersionId(versionId);

        File nativesDir = new File(baseDir, "versions/" + versionId + "/natives");
        nativesDir.mkdirs();

        List<File> nativeJars = new ArrayList<>();
        for (VersionDetail.Library lib : detail.getLibraries()) {
            if (!lib.isAllowed() || lib.getNatives() == null) continue;
            if (lib.getDownloads() == null || lib.getDownloads().getClassifiers() == null) continue;

            String osKey = PlatformUtil.getOSKey();
            String classifier = lib.getNatives().get(osKey);
            if (classifier == null) continue;

            VersionDetail.DownloadFile nativeFile = lib.getDownloads().getClassifiers().get(classifier);
            if (nativeFile == null) continue;

            File jarFile = new File(baseDir, "libraries/" + nativeFile.getPath());
            if (jarFile.exists()) nativeJars.add(jarFile);
        }

        // HIGH-10: marker baseado em mtime combinado de todos os jars.
        // Se nada mudou nos jars desde a última extração, pula.
        File marker = new File(nativesDir, ".extracted");
        long combinedMtime = 0L;
        for (File j : nativeJars) combinedMtime += j.lastModified();
        String expected = String.valueOf(combinedMtime) + ":" + nativeJars.size();
        if (marker.exists()) {
            try {
                String existing = Files.readString(marker.toPath());
                if (expected.equals(existing)) {
                    LOG.debug("Natives já extraídos ({} jars, marker válido); pulando re-extração", nativeJars.size());
                    return nativesDir.getAbsolutePath();
                }
            } catch (IOException ignored) {
                // marker corrompido, re-extrair
            }
        }

        for (File jarFile : nativeJars) {
            try (ZipFile zip = new ZipFile(jarFile)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName().replace('\\', '/');
                    if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")
                            || name.endsWith(".jnilib")) {
                        // LOW-10 (code-review): aqui o Zip Slip é defense
                        // in depth — `fileName = new File(name).getName()`
                        // já garante que fileName não tem separadores
                        // (basename do entry do zip). A verificação de
                        // canônico é redundante, mas mantida como
                        // cinto-e-suspensórios: se algum dia o filtro
                        // mudar (ex: aceitar subpastas "linux/x64/") e
                        // o basename não for mais seguro, o canônico
                        // ainda aborta.
                        String fileName = new File(name).getName();
                        File dest = new File(nativesDir, fileName);
                        String canonicalDest = dest.getCanonicalPath();
                        String canonicalNatives = nativesDir.getCanonicalPath();
                        if (!canonicalDest.startsWith(canonicalNatives + File.separator)) {
                            throw new IOException("Entrada de native inválida (Zip Slip): " + entry.getName());
                        }
                        if (!dest.exists()) {
                            try (InputStream is = zip.getInputStream(entry)) {
                                Files.copy(is, dest.toPath());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warn("Erro ao extrair natives de {}", jarFile.getName(), e);
            }
        }

        // HIGH-10: escreve marker para futuros launches pularem
        try {
            Files.writeString(marker.toPath(), expected);
        } catch (IOException e) {
            LOG.debug("Não foi possível escrever marker de natives; próximo launch vai re-extrair", e);
        }

        return nativesDir.getAbsolutePath();
    }
}
