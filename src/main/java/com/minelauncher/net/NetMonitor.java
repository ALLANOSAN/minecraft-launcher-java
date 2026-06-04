package com.minelauncher.net;

import com.minelauncher.launcher.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitor assíncrono de conectividade de rede.
 * Substitui o antigo `checkNetAsync/pingMojang` que vivia dentro do MainController.
 *
 * <p>Características:
 * <ul>
 *   <li>Re-entrância protegida: chamadas concorrentes viram no-op.</li>
 *   <li>Thread daemon (não impede shutdown da JVM).</li>
 *   <li>Usa singleton HttpClient.</li>
 *   <li>Último status acessível via {@link #isOnline()}.</li>
 * </ul>
 */
public class NetMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(NetMonitor.class);

    /** HEAD na manifest URL da Mojang — leve, cacheado pelo CDN. */
    private static final String PROBE_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final long TIMEOUT_SECONDS = 3;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean online = true;
    private final Runnable onChange;

    /**
     * @param onChange callback executado na thread que detectar mudança
     *                 (tipicamente dentro de Platform.runLater pelo chamador).
     */
    public NetMonitor(Runnable onChange) {
        this.onChange = onChange;
    }

    public boolean isOnline() {
        return online;
    }

    /**
     * Dispara uma checagem assíncrona. Se já houver uma em curso, é no-op.
     */
    public void checkAsync() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                boolean prev = online;
                online = pingMojang();
                if (prev != online && onChange != null) {
                    onChange.run();
                }
            } finally {
                running.set(false);
            }
        }, "net-check");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Faz um HEAD request com timeout curto. Retorna true se o servidor
     * respondeu com 2xx ou 3xx.
     */
    private boolean pingMojang() {
        try {
            Request request = new Request.Builder()
                    .url(PROBE_URL)
                    .head()
                    .build();
            try (Response response = HttpClient.getInstance().newCall(request).execute()) {
                int code = response.code();
                return code >= 200 && code < 400;
            }
        } catch (Exception e) {
            LOG.debug("Ping Mojang falhou: {}", e.getMessage());
            return false;
        }
    }

    /** Apenas para testes — força o estado sem fazer request. */
    void setOnlineForTest(boolean value) {
        this.online = value;
    }
}
