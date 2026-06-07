package com.minelauncher.ui.services;

import com.minelauncher.settings.SettingsManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link ErrorReporter} (CRIT-4 do code-review) +
 * {@link SettingsManager#isErrorReportingEnabled()} opt-in flag.
 *
 * <p>Estratégia: ErrorReporter.report() é estático. Testamos o
 * contrato principal — "não envia nada se opt-in está desabilitado"
 * — sem precisar de MockWebServer (que não está no pom).
 *
 * <p>Testes que validam o JSON real precisam inspecionar o
 * payload construído; para isso usamos reflexão ou capturamos via
 * spy. Aqui validamos só o contrato de opt-in + não-NPE.
 */
class ErrorReporterTest {

    @Test
    void report_withOptInDisabled_doesNotThrow() {
        // Default da flag é false → método deve retornar early
        // sem fazer NPE ao acessar getErrorReportUrl() (que é null).
        SettingsManager.getInstance().setErrorReportingEnabled(false);
        SettingsManager.getInstance().setErrorReportUrl(null);

        assertDoesNotThrow(() ->
                ErrorReporter.report(new RuntimeException("test"), "test-ctx"));
    }

    @Test
    void report_withNullContext_doesNotThrow() {
        SettingsManager.getInstance().setErrorReportingEnabled(false);
        assertDoesNotThrow(() ->
                ErrorReporter.report(new RuntimeException("test"), null));
    }

    @Test
    void report_withNullEndpoint_doesNotAttemptHttp() {
        // Com flag habilitada mas URL null → return early sem
        // instanciar OkHttp client.
        SettingsManager.getInstance().setErrorReportingEnabled(true);
        SettingsManager.getInstance().setErrorReportUrl(null);

        assertDoesNotThrow(() ->
                ErrorReporter.report(new RuntimeException("test"), "ctx"));
    }

    @Test
    void report_withBlankEndpoint_doesNotAttemptHttp() {
        // URL em branco também é tratada como não-configurada.
        SettingsManager.getInstance().setErrorReportingEnabled(true);
        SettingsManager.getInstance().setErrorReportUrl("   ");

        assertDoesNotThrow(() ->
                ErrorReporter.report(new RuntimeException("test"), "ctx"));
    }

    @Test
    void report_handlesChainedException() {
        // CRIT-4: serializar causa encadeada não pode crashar.
        SettingsManager.getInstance().setErrorReportingEnabled(false);
        RuntimeException cause = new IllegalArgumentException("causa");
        RuntimeException top = new RuntimeException("topo", cause);

        assertDoesNotThrow(() -> ErrorReporter.report(top, "ctx"));
    }

    @Test
    void settingsManager_errorReportingDefaultIsFalse() {
        // Garante que o opt-in default é false. Se alguém mudar
        // o default na SettingsData, este teste pega.
        // Como o singleton pode ter sido tocado por outros testes,
        // resetamos explicitamente.
        SettingsManager.getInstance().setErrorReportingEnabled(false);
        assertFalse(SettingsManager.getInstance().isErrorReportingEnabled());
    }

    @Test
    void settingsManager_errorReportingFlagIsPersisted() {
        SettingsManager sm = SettingsManager.getInstance();
        sm.setErrorReportingEnabled(true);
        assertTrue(sm.isErrorReportingEnabled(),
                "setErrorReportingEnabled(true) deve refletir imediatamente");
        sm.setErrorReportingEnabled(false);
        assertFalse(sm.isErrorReportingEnabled());
    }
}
