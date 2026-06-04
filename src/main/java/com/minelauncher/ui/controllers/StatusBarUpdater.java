package com.minelauncher.ui.controllers;

import com.minelauncher.net.NetMonitor;
import javafx.animation.AnimationTimer;
import javafx.scene.control.Label;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Encapsula o loop de animação que atualiza clock, RAM e net
 * no rodapé da UI (status bar).
 *
 * <p>Extraído do MainController (H-1: god class) — antes vivia
 * inline com 4 timer-related fields, agora é componente separado
 * com ciclo de vida próprio (start/stop).
 */
public class StatusBarUpdater {

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME_FMT_FULL =
            DateTimeFormatter.ofPattern("EEE dd/MM HH:mm:ss");
    private static final long RAM_UPDATE_INTERVAL_NS = 2_000_000_000L;  // 2s
    private static final long NET_UPDATE_INTERVAL_NS = 30_000_000_000L; // 30s

    private final Label statusClockLabel;
    private final Label sessionTimeLabel;
    private final Label statusRamLabel;
    private final Label statusNetLabel;
    private final NetMonitor netMonitor;
    private final Runnable initialNetCheck;

    private final long sessionStartMs = System.currentTimeMillis();
    private AnimationTimer timer;
    private long lastRamUpdateNs = 0;
    private long lastNetUpdateNs = 0;

    public StatusBarUpdater(Label statusClockLabel,
                            Label sessionTimeLabel,
                            Label statusRamLabel,
                            Label statusNetLabel,
                            NetMonitor netMonitor,
                            Runnable initialNetCheck) {
        this.statusClockLabel = statusClockLabel;
        this.sessionTimeLabel = sessionTimeLabel;
        this.statusRamLabel = statusRamLabel;
        this.statusNetLabel = statusNetLabel;
        this.netMonitor = netMonitor;
        this.initialNetCheck = initialNetCheck;
    }

    public void start() {
        updateClock();
        updateRam();
        if (initialNetCheck != null) {
            initialNetCheck.run();
        }
        if (timer == null) {
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    updateClock();
                    if (now - lastRamUpdateNs >= RAM_UPDATE_INTERVAL_NS) {
                        updateRam();
                        lastRamUpdateNs = now;
                    }
                    if (now - lastNetUpdateNs >= NET_UPDATE_INTERVAL_NS) {
                        if (netMonitor != null) netMonitor.checkAsync();
                        lastNetUpdateNs = now;
                    }
                }
            };
            timer.start();
        }
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    public void updateClock() {
        ZonedDateTime now = ZonedDateTime.now(BRAZIL_ZONE);
        if (statusClockLabel != null) {
            statusClockLabel.setText(now.format(TIME_FMT_FULL));
        }
        if (sessionTimeLabel != null) {
            sessionTimeLabel.setText(formatElapsedCompact(System.currentTimeMillis() - sessionStartMs));
        }
    }

    public void updateRam() {
        if (statusRamLabel == null) return;
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        statusRamLabel.setText(
                com.minelauncher.utils.FileUtils.formatBytes(usedBytes) + " / " +
                com.minelauncher.utils.FileUtils.formatBytes(maxBytes));
    }

    public void updateNetLabel(boolean online) {
        if (statusNetLabel == null) return;
        statusNetLabel.setText(online ? "ONLINE" : "OFFLINE");
    }

    private static String formatElapsedCompact(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, sec);
        return sec + "s";
    }
}
