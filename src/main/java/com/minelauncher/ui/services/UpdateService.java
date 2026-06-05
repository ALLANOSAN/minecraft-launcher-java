package com.minelauncher.ui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * Service para orquestrar atualizações do launcher a partir do código fonte.
 */
public class UpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

    public void checkForUpdatesAndRestart() {
        LOG.info("Verificando atualizações no repositório...");
        
        try {
            // Executa script de atualização (pull + rebuild)
            ProcessBuilder pb = new ProcessBuilder("./update.sh");
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();
            
            if (exitCode == 0) {
                LOG.info("Atualização concluída. Reiniciando...");
                System.exit(0); // O launcher deve ser reiniciado pelo seu gerenciador/script
            } else {
                LOG.error("Falha na atualização. Exit code: {}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            ErrorReporter.report(e, "UpdateService: update");
        }
    }
}
