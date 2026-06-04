package com.minelauncher.ui.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service para centralizar o reporte de erros.
 * Permite integração futura com ferramentas de observabilidade (ex: Sentry).
 */
public class ErrorReporter {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorReporter.class);

    public static void report(Throwable e, String context) {
        LOG.error("Contexto: {} | Erro: {}", context, e.getMessage(), e);
        
        // Aqui seria o hook para envio para serviço externo:
        // Sentry.captureException(e);
    }
}
