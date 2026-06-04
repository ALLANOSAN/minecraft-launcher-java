package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
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
     */
    public static String formatBytes(long bytes) {
        if (bytes < KB) return bytes + " B";
        if (bytes < MB) return String.format("%.1f KB", bytes / (double) KB);
        if (bytes < GB) {
            double mb = bytes / (double) MB;
            return (mb < 10 ? String.format("%.1f", mb) : String.format("%.0f", mb)) + " MB";
        }
        double gb = bytes / (double) GB;
        return (gb < 10 ? String.format("%.2f", gb) : String.format("%.1f", gb)) + " GB";
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
}
