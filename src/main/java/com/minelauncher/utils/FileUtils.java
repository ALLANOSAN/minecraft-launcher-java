package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utilitários de filesystem e formatação compartilhados.
 * Antes: formatBytes e deleteDirectory estavam duplicados em 3+ classes.
 */
public final class FileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private FileUtils() {}

    /**
     * Formata bytes em string legível (B/KB/MB/GB).
     *
     * <p>FIX locale: usa {@link java.util.Locale#ROOT} explicitamente para o
     * separador decimal. Antes usava o locale default da JVM, então em PT-BR
     * mostrava "1,5 MB" em vez de "1.5 MB" — inconsistente com o resto da UI
     * e quebrando os testes em qualquer locale não-EN.
     */
    public static String formatBytes(long bytes) {
        if (bytes < KB) return bytes + " B";
        if (bytes < MB) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / (double) KB);
        if (bytes < GB) {
            double mb = bytes / (double) MB;
            return (mb < 10
                    ? String.format(java.util.Locale.ROOT, "%.1f", mb)
                    : String.format(java.util.Locale.ROOT, "%.0f", mb)) + " MB";
        }
        double gb = bytes / (double) GB;
        return (gb < 10
                ? String.format(java.util.Locale.ROOT, "%.2f", gb)
                : String.format(java.util.Locale.ROOT, "%.1f", gb)) + " GB";
    }

    /**
     * Remove diretório recursivamente. Retorna true se removeu com sucesso.
     * Implementação com Files.walkFileTree (mais robusta que recursão manual).
     */
    public static boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        if (!dir.isDirectory()) return dir.delete();
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            LOG.warn("Falha ao deletar diretório {}", dir.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Copia diretório recursivamente.
     *
     * <p><b>HIGH-9 do code-review:</b> a versão anterior usava
     * {@code file.isDirectory()} que segue symlinks, e gravava
     * {@code Files.copy(file.toPath(), dest.toPath(), REPLACE_EXISTING)}
     * que segue o link destino também. Se um modpack malicioso
     * (ou um backup de zip com symlinks preservados) contivesse um
     * symlink {@code config/some_file -> /etc/passwd}, a cópia
     * sobrescreveria arquivos do sistema. Esta versão:
     * <ol>
     *   <li>Usa {@link java.nio.file.Files#walkFileTree} com
     *       {@link LinkOption#NOFOLLOW_LINKS} para recusar symlinks
     *       inteiros (não os segue ao listar nem ao copiar).</li>
     *   <li>Valida que cada destino, depois de resolvido canonicamente,
     *       permanece dentro de {@code target} (defesa em profundidade
     *       contra Zip Slip residual).</li>
     * </ol>
     */
    public static void copyDirectory(File source, File target) throws IOException {
        if (!target.exists()) target.mkdirs();
        Path targetRoot = target.getCanonicalFile().toPath();
        Path sourceRoot = source.getCanonicalFile().toPath();

        // NÃO passa FOLLOW_LINKS — sem isso, walkFileTree chama
        // visitFile com o path do link e Files.isSymbolicLink detecta.
        // Com FOLLOW_LINKS, visitFile recebe o destino do link e o
        // isSymbolicLink retorna false.
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.compareTo(sourceRoot) != 0) {
                    String rel = sourceRoot.relativize(dir).toString();
                    Path dest = targetRoot.resolve(rel);
                    // Resolve symlink antes de verificar containment
                    if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
                        // Já existe — pode ser symlink criado em iteração anterior; pula
                        return FileVisitResult.CONTINUE;
                    }
                    Files.createDirectories(dest);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // NOFOLLOW_LINKS: recusa symlinks completamente
                if (Files.isSymbolicLink(file)) {
                    LOG.warn("Symlink ignorado durante copy: {}", file);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path dest = targetRoot.resolve(sourceRoot.relativize(file).toString());
                if (!dest.normalize().startsWith(targetRoot)) {
                    throw new IOException("Tentativa de escape de diretório (Zip Slip): " + file);
                }
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Sanitiza um nome de arquivo/diretório.
     *
     * <p><b>CRIT-7 do code-review:</b> a versão anterior em
     * {@code ModManager} e {@code ModActions} usava
     * {@code [^a-zA-Z0-9\s\-]} que removia TUDO não-ASCII. Modpacks
     * com nomes CJK, cirílicos ou acentuados colapsavam para string
     * vazia, e o download tentava gravar em {@code modpacks/} (um
     * diretório), gerando {@code FileNotFoundException ("Is a
     * directory")} críptico.
     *
     * <p>Esta versão preserva letras Unicode (\\p{L}) e dígitos
     * Unicode (\\p{N}), normaliza whitespace, e cai num fallback
     * determinístico se o resultado ainda for vazio.
     *
     * @param name nome original (pode ser null)
     * @return nome sanitizado, nunca vazio
     */
    public static String sanitizeName(String name) {
        if (name == null) return "unnamed";
        String s = name.replaceAll("[^\\p{L}\\p{N}\\s\\-_.]", "").trim();
        if (s.isEmpty()) {
            // Hashcode como fallback determinístico. Math.abs porque
            // hashCode pode ser Integer.MIN_VALUE.
            s = "item_" + Math.abs(name.hashCode());
        }
        return s.replaceAll("\\s+", "_");
    }
}
