package com.minelauncher.launcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link ProcessSpawner} (MEDIUM #7 do code-review).
 *
 * <p>Cobre:
 * <ul>
 *   <li>spawn: processo inicia, captura stdout via callback</li>
 *   <li>spawn: processo é registrado e removido ao terminar (isRunning=false após exit)</li>
 *   <li>kill: no-op em processo já morto / null; não quebra</li>
 *   <li>killAll: no-op quando não há processos vivos; limpa set</li>
 *   <li>registerShutdownHook: idempotente (segunda chamada é no-op)</li>
 *   <li>spawn: callback de log que joga exceção não derruba o spawn (comportamento documentado)</li>
 * </ul>
 *
 * <h2>Por que `java -version` em todos os testes</h2>
 *
 * <p>Todos os testes usam {@code java -version} como processo alvo. Decisão
 * tomada porque (M3 do code-review, commit 655ae88):
 * <ul>
 *   <li><b>Portabilidade</b>: existe em todo JRE/JDK, em todo OS, sem
 *       dependência de shell ou binário externo;</li>
 *   <li><b>Determinismo</b>: termina em ~50-200ms;</li>
 *   <li><b>Saída garantida</b>: stderr com versão do Java — redirecionado
 *       para stdout via {@code redirectErrorStream=true} no spawner.</li>
 * </ul>
 *
 * <p>Limitação aceita: testes que precisariam de um processo vivo por
 * mais de ~200ms (ex: verificar isRunning==true durante execução, ou
 * kill em processo vivo) não são portáveis. Os testes abaixo verificam
 * os caminhos "depois do exit" (kill no-op, isRunning=false), que são
 * os que importam para o cleanup hook e o ciclo de vida do set. O nome
 * de cada teste reflete exatamente o que ele cobre.
 */
class ProcessSpawnerTest {

    private ProcessSpawner spawner;
    private File tempDir;

    @BeforeEach
    void setUp() throws IOException {
        spawner = new ProcessSpawner();
        tempDir = Files.createTempDirectory("procspawner-test").toFile();
    }

    @AfterEach
    void tearDown() {
        deleteRecursively(tempDir);
        spawner.killAll();
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    private static String javaBinary() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    @Test
    void spawn_startsProcessAndCapturesStdout() throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        Process p = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, lines::add);

        assertNotNull(p);
        assertTrue(p.pid() > 0, "PID deve ser positivo");

        assertTrue(p.waitFor(10, TimeUnit.SECONDS), "java -version deve terminar em 10s");
        // Drena buffer da thread daemon de log
        Thread.sleep(200);

        assertFalse(lines.isEmpty(),
                "callback de log deve ter recebido linhas (stderr redirecionado para stdout)");
    }

    @Test
    void spawn_isRunningReturnsFalseAfterProcessExits() throws Exception {
        // L2 do code-review (commit 655ae88): nome antigo _isRunningReflectsProcessState
        // era enganoso — só verificava o estado pós-exit. Nome novo reflete
        // exatamente o que o teste cobre.
        List<String> lines = new CopyOnWriteArrayList<>();
        Process p = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, lines::add);

        assertTrue(p.waitFor(10, TimeUnit.SECONDS));
        // Pequeno delay para onExit().thenRun executar e remover do set
        Thread.sleep(100);
        assertFalse(spawner.isRunning(), "isRunning deve retornar false após exit");
    }

    @Test
    void kill_doesNotThrowOnNullOrDeadProcess() throws Exception {
        // L2 do code-review (commit 655ae88): nome antigo _terminatesRunningProcess
        // era enganoso — `java -version` termina antes do kill() rodar. O
        // caminho real testado é o defensivo (kill de processo morto / null).
        List<String> lines = new CopyOnWriteArrayList<>();
        Process p = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, lines::add);

        assertDoesNotThrow(() -> spawner.kill(null), "kill(null) deve ser no-op");
        assertTrue(p.waitFor(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> spawner.kill(p), "kill em processo já morto deve ser no-op");
    }

    @Test
    void killAll_clearsActiveProcesses() throws Exception {
        // Spawna 2 processos que terminam rápido; killAll após exit não pode explodir
        List<String> lines = new CopyOnWriteArrayList<>();
        Process p1 = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, lines::add);
        Process p2 = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, lines::add);

        assertTrue(p1.waitFor(5, TimeUnit.SECONDS));
        assertTrue(p2.waitFor(5, TimeUnit.SECONDS));

        assertDoesNotThrow(spawner::killAll);
        assertFalse(spawner.isRunning());
    }

    @Test
    void registerShutdownHook_isIdempotent() {
        // Usa instância isolada para não poluir o test runner
        ProcessSpawner isolated = new ProcessSpawner();
        assertDoesNotThrow(isolated::registerShutdownHook);
        assertDoesNotThrow(isolated::registerShutdownHook,
                "segunda chamada deve ser no-op (flag synchronized)");
    }

    @Test
    void spawn_logCallbackExceptionsDoNotPropagate() throws Exception {
        // Documenta o comportamento atual: se o callback de log joga,
        // a exceção pode aparecer como uncaught na thread daemon.
        // O importante: o spawn() em si não falha por causa disso.
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> { /* swallow */ });
            Process p = spawner.spawn(List.of(javaBinary(), "-version"), tempDir, line -> {
                throw new RuntimeException("callback boom: " + line);
            });
            assertNotNull(p, "spawn não pode falhar por causa do callback de log");
            assertTrue(p.waitFor(5, TimeUnit.SECONDS));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(prev);
        }
    }
}
