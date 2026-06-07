package com.minelauncher.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Responsável por efetivamente dar {@code start()} no processo do jogo,
 * encaminhar seu stdout para um callback de log, e manter o registro dos
 * processos vivos para encerramento coordenado.
 *
 * <p>Extraído do {@code GameLauncher} (refactor por decomposição). Encapsula:
 * <ul>
 *     <li>criação do {@link Process} via {@link ProcessBuilder} com
 *     env {@code INST_LAUNCHER=MineLauncher} e {@code redirectErrorStream};</li>
 *     <li>thread daemon que lê o stdout linha-a-linha e entrega ao
 *     callback de log (sem bloquear a thread de launch);</li>
 *     <li>conjunto thread-safe de processos ativos para
 *     {@link #killAll()} no shutdown da JVM;</li>
 *     <li>registro opcional do shutdown hook (HIGH-11 — só UMA vez, via
 *     entry point).</li>
 * </ul>
 *
 * <p>State: o set de processos ativos (mutável, sincronizado). Cada
 * instância é independente — não compartilhar entre instâncias de
 * {@code GameLauncher} se quiser isolar o ciclo de vida.
 */
public class ProcessSpawner {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessSpawner.class);

    private final Set<Process> activeProcesses = Collections.synchronizedSet(new HashSet<>());

    /**
     * Inicia o processo com os argumentos fornecidos, encaminha stdout
     * para {@code logCallback} em uma thread daemon, e registra o
     * processo no set de ativos.
     *
     * <p>O {@code workDir} é definido como {@code processBuilder.directory}.
     * O env var {@code INST_LAUNCHER=MineLauncher} é injetado em
     * {@code processBuilder.environment()}.
     *
     * @param args          argumentos completos do comando (índice 0 = executável)
     * @param workDir       diretório de trabalho do processo
     * @param logCallback   callback chamado para cada linha de stdout
     * @return o {@link Process} iniciado (você pode usar
     *         {@link #kill(Process)} ou {@link Process#pid()} depois)
     */
    public Process spawn(List<String> args, File workDir, Consumer<String> logCallback) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        pb.environment().put("INST_LAUNCHER", "MineLauncher");

        Process p = pb.start();
        activeProcesses.add(p);
        p.onExit().thenRun(() -> activeProcesses.remove(p));

        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logCallback.accept(line);
                }
            } catch (IOException e) {
                LOG.debug("Stream de log encerrado");
            }
        });
        logThread.setDaemon(true);
        logThread.setName("Minecraft-Log");
        logThread.start();

        LOG.info("Minecraft iniciado (PID: {})", p.pid());
        return p;
    }

    /**
     * HIGH-11: deve ser chamado UMA VEZ pelo entry point (não pelo
     * construtor) para registrar a cleanup em caso de crash. Caso
     * contrário, testes de unidade que instanciam {@code GameLauncher}
     * poluem a sequência de shutdown da JVM, e em produção rodar mais
     * de uma vez acarreta hooks duplicados tentando matar os mesmos
     * processos.
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::killAll, "MineLauncher-Cleanup"));
    }

    /**
     * HIGH-11: mata UM processo específico e remove do set de ativos.
     * Usado quando o usuário clica em "Encerrar jogo" — não afeta
     * outros jogos que porventura estejam rodando.
     */
    public void kill(Process p) {
        if (p == null) return;
        try {
            if (p.isAlive()) {
                p.destroyForcibly();
                LOG.info("Processo do jogo {} encerrado", p.pid());
            }
        } finally {
            activeProcesses.remove(p);
        }
    }

    /**
     * HIGH-11: mata TODOS os processos ativos. Só deve ser chamado pelo
     * shutdown hook registrado em {@link #registerShutdownHook()}, ou
     * explicitamente pelo usuário via menu "Encerrar tudo".
     */
    public void killAll() {
        synchronized (activeProcesses) {
            for (Process p : activeProcesses) {
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
            activeProcesses.clear();
        }
        LOG.info("Processos do jogo finalizados");
    }

    public boolean isRunning() {
        synchronized (activeProcesses) {
            return activeProcesses.stream().anyMatch(Process::isAlive);
        }
    }
}
