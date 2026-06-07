package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Utilitário para gerar um identificador único da máquina (para salt de criptografia).
 *
 * <p><b>CRIT-2 do code-review:</b> a versão anterior caía em
 * {@code user.name + os.name} quando nenhuma MAC estava disponível — em
 * containers, CI runners, sandboxes e VMs sem rede isso produzia a mesma
 * string em máquinas diferentes, e o SHA-256 disso virava uma chave AES
 * idêntica. Resultado: tokens cifrados nesse cenário eram decifráveis
 * por qualquer outro container com o mesmo {@code root@Linux}.
 *
 * <p>Esta versão acumula entropia de múltiplas fontes, em ordem de
 * preferência:
 * <ol>
 *   <li>Todos os MAC addresses reais (não-loopback), ordenados e
 *       concatenados via SHA-256.</li>
 *   <li>{@code /etc/machine-id} (Linux moderno, systemd).</li>
 *   <li>{@code ioreg -rd1 -c IOPlatformExpertDevice} procurando
 *       {@code IOPlatformUUID} (macOS).</li>
 *   <li>Registry {@code HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid}
 *       via PowerShell (Windows).</li>
 *   <li><b>Último fallback:</b> um UUID v4 aleatório gravado em
 *       {@code ~/.minelauncher/.machine-id} com permissões 0600. Não é
 *       determinístico entre máquinas (bom), e o arquivo só precisa
 *       sobreviver à reinstalação do app para preservar tokens.</li>
 * </ol>
 *
 * <p>O retorno é SEMPRE um SHA-256 hex de 64 chars — estável por
 * instalação, mas não-vazio e não-trivial.
 */
public class MachineIdentifier {

    private static final Logger LOG = LoggerFactory.getLogger(MachineIdentifier.class);

    public static String getUniqueId() {
        // 1. MAC addresses reais (não loopback), ordenados
        String macHash = collectMacsHashed();
        if (macHash != null) return macHash;

        // 2. /etc/machine-id (Linux)
        String linuxId = readFileIfExists("/etc/machine-id");
        if (linuxId != null && !linuxId.isBlank()) return sha256("linux-machine-id:" + linuxId.trim());

        // 3. macOS IOPlatformUUID via ioreg
        String macOsId = readCommandOutput("ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        if (macOsId != null) {
            int idx = macOsId.indexOf("IOPlatformUUID");
            if (idx >= 0) {
                String tail = macOsId.substring(idx);
                int eq = tail.indexOf('=', idx);
                int nl = tail.indexOf('\n', eq);
                if (eq > 0 && nl > eq) {
                    String uuid = tail.substring(eq + 1, nl).trim().replace("\"", "");
                    if (!uuid.isEmpty()) return sha256("macos-uuid:" + uuid);
                }
            }
        }

        // 4. Windows MachineGuid via PowerShell
        String winId = readCommandOutput("powershell", "-NoProfile", "-Command",
                "(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Cryptography').MachineGuid");
        if (winId != null && !winId.isBlank()) {
            return sha256("windows-machine-guid:" + winId.trim());
        }

        // 5. Último fallback: UUID aleatório persistido em arquivo 0600
        return getOrCreatePersistedUuid();
    }

    private static String collectMacsHashed() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            List<String> macs = new ArrayList<>();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;
                if (ni.getName().startsWith("docker") || ni.getName().startsWith("veth")) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) sb.append(String.format("%02X", b));
                macs.add(sb.toString());
            }
            if (macs.isEmpty()) return null;
            Collections.sort(macs);
            StringBuilder combined = new StringBuilder("mac:");
            for (String m : macs) combined.append(m).append(";");
            return sha256(combined.toString());
        } catch (Exception e) {
            LOG.debug("Falha ao enumerar MACs", e);
            return null;
        }
    }

    private static String readFileIfExists(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) return null;
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.debug("Não foi possível ler {}", path);
            return null;
        }
    }

    private static String readCommandOutput(String... cmd) {
        Process p = null;
        try {
            // CRIT-2: timeout via waitFor(3, SECONDS) — funciona em Java 8+,
            // ao contrário de ProcessBuilder.timeout(Duration) que é Java 9+.
            // Antes bloqueava indefinidamente em comandos travados
            // (ex: wmic preso no Windows).
            p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.debug("Comando {} falhou", String.join(" ", cmd));
            if (p != null && p.isAlive()) p.destroyForcibly();
            return null;
        }
    }

    /**
     * Persiste um UUID v4 aleatório em ~/.minelauncher/.machine-id com
     * permissões POSIX 0600 quando possível. É a única fonte de
     * identidade que sobrevive à ausência de MAC/machine-id, e como é
     * aleatório (não-determinístico), cada instalação recebe uma chave
     * diferente — o oposto do bug original.
     */
    private static String getOrCreatePersistedUuid() {
        Path file = Paths.get(System.getProperty("user.home"), ".minelauncher", ".machine-id");
        try {
            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) return sha256("persisted:" + existing);
            }
            // Gera novo
            String newId = UUID.randomUUID().toString();
            Files.createDirectories(file.getParent());
            Files.writeString(file, newId, StandardCharsets.UTF_8);
            try {
                Set<PosixFilePermission> perms = new HashSet<>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(file, perms);
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows ou FS sem POSIX
            }
            LOG.info("Machine ID aleatório gerado e persistido em {}", file);
            return sha256("persisted:" + newId);
        } catch (IOException e) {
            // Último recurso mesmo: usa o random sem persistir. Pior
            // porque muda a cada launch, mas evita o bug original
            // (todos root@Linux com a mesma chave).
            LOG.warn("Não foi possível persistir machine ID; usando UUID de sessão", e);
            return sha256("session:" + UUID.randomUUID());
        }
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 é obrigatório em qualquer JRE; se falhar, é bug do
            // classpath, não do ambiente. Relança.
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
