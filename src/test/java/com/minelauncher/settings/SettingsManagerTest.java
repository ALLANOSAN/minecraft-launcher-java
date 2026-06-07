package com.minelauncher.settings;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de regressão para o NPE do {@link SettingsManager} (BUG-4).
 *
 * <p>O bug original era uma violação da ordem de inicialização estática
 * (JLS §12.4.1): o campo {@code INSTANCE} era declarado antes de
 * {@code GSON} e {@code ACCOUNTS_TYPE}, e o construtor chamava
 * {@code load()} (que usa GSON). Resultado: NPE ao acessar
 * {@code SettingsManager.getInstance()} de qualquer thread que
 * "vencesse" o {@code <clinit>} parcial.
 *
 * <p>Fix: reordenar os 3 campos para que GSON e ACCOUNTS_TYPE vençam
 * INSTANCE em ordem textual.
 *
 * <p>O teste de regressão não pode garantir a ordem diretamente sem
 * reflection (e mesmo assim seria frágil), mas pode simular a pressão
 * concorrente que expunha o NPE: dispara N threads pedindo o
 * singleton simultaneamente e verifica que TODAS recebem uma instância
 * não-nula, sem exceções.
 */
class SettingsManagerTest {

    /**
     * Garante que o singleton já foi inicializado uma vez antes dos
     * testes — caso contrário, o primeiro teste pagaria o custo do
     * {@code <clinit>} e poderia dar falso positivo em condições
     * específicas. Mas como a JVM é a mesma do test runner, o
     * {@code ErrorReporterTest} provavelmente já disparou.
     */
    @BeforeAll
    static void warmUp() {
        // Força a inicialização aqui
        assertNotNull(SettingsManager.getInstance(),
                "SettingsManager.getInstance() deve retornar instância não-nula");
    }

    @Test
    void getInstance_doesNotThrowNpe() {
        // Regressão direta: pegar a instância não pode explodir com NPE
        SettingsManager m = SettingsManager.getInstance();
        assertNotNull(m);
    }

    @Test
    void getInstance_concurrentAccessDoesNotThrowNpe() throws Exception {
        // Simula a race condition que expunha o bug: N threads pedindo
        // o singleton simultaneamente. Antes do fix, o <clinit> podia
        // ser interrompido no meio, deixando GSON=null quando o
        // construtor rodasse.
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<SettingsManager>> tasks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                tasks.add(SettingsManager::getInstance);
            }
            List<Future<SettingsManager>> results = pool.invokeAll(tasks, 10, TimeUnit.SECONDS);

            // Todas as N threads devem ter recebido a MESMA instância
            // (singleton) e nenhuma deve ter lançado exceção.
            SettingsManager first = null;
            for (Future<SettingsManager> f : results) {
                SettingsManager m = f.get();
                assertNotNull(m, "instância não pode ser null");
                if (first == null) first = m;
                else assertSame(first, m, "singleton deve ser a mesma instância");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void getInstance_defaultValuesAreReadable() {
        // Se o bug tivesse voltado, getLanguage() poderia NPE ao tentar
        // serializar com GSON=null. Verifica que getters simples funcionam.
        SettingsManager m = SettingsManager.getInstance();
        assertNotNull(m.getLanguage());
        assertNotNull(m.getTheme());
        assertFalse(m.getAccounts() == null, "accounts não pode ser null");
    }
}
