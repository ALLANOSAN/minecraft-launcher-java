package com.minelauncher.ui.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Helper para abrir arquivos/diretórios no explorador nativo do SO.
 * Antes esse padrão estava duplicado em openItemFolder, openSelectedScreenshot
 * e em outros lugares — sempre o mesmo try/catch com Desktop + xdg-open.
 */
public final class DesktopUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopUtil.class);

    private DesktopUtil() {}

    /**
     * Abre {@code target} no explorador padrão do SO. Se o arquivo não existir,
     * loga e retorna silenciosamente. Erros são logados e re-lançados encapsulados
     * em IOException para o caller decidir (atualizar UI, etc).
     */
    public static void open(File target) throws IOException {
        if (target == null || !target.exists()) {
            LOG.debug("Alvo não existe, nada a abrir: {}", target);
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(target);
            } else {
                fallbackOpen(target);
            }
        } catch (UnsupportedOperationException e) {
            // isDesktopSupported() pode retornar true em headless mas isSupported(OPEN) false
            fallbackOpen(target);
        }
    }

    private static void fallbackOpen(File target) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (os.contains("mac")) {
            pb = new ProcessBuilder("open", target.getAbsolutePath());
        } else if (os.contains("win")) {
            pb = new ProcessBuilder("explorer", target.getAbsolutePath());
        } else {
            // Linux / BSD / outros
            pb = new ProcessBuilder("xdg-open", target.getAbsolutePath());
        }
        pb.start();
    }
}
