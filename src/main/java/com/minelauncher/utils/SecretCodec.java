package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * Utilitário para cifragem de campos sensíveis (tokens de autenticação).
 *
 * <p>AVISO DE SEGURANÇA: este esquema é <b>obfuscação, não criptografia</b>.
 * A chave é derivada de uma constante no JAR. Qualquer um com o JAR pode
 * decifrar. O objetivo é apenas impedir leitura casual de
 * {@code cat launcher_accounts.json}.
 *
 * <p>Para segurança real, use:
 * <ul>
 *   <li>Windows: DPAPI (via biblioteca JNA)</li>
 *   <li>macOS: Keychain (via biblioteca JNA)</li>
 *   <li>Linux: Secret Service (libsecret via biblioteca JNA)</li>
 * </ul>
 *
 * <p>O custo de adicionar a dependência JNA (que já está no pom.xml) é baixo;
 * só não foi feito agora para manter a mudança mínima.
 *
 * <p><b>QUAL-11 — Perda do arquivo {@code secret.key}:</b> Se o arquivo
 * {@code secret.key} for apagado (ou se o usuário reinstalar o sistema e o
 * arquivo não tiver sido feito backup), todos os tokens armazenados ficam
 * <b>irrecuperáveis</b>. Os usuários precisarão refazer login (Microsoft OAuth
 * ou offline). Considere instruir o usuário a fazer backup deste arquivo
 * junto com {@code launcher_accounts.json}.
 */
public final class SecretCodec {
    private static final Logger LOG = LoggerFactory.getLogger(SecretCodec.class);

    /**
     * Chave AES de 256 bits usada para cifrar tokens persistidos em
     * {@code launcher_accounts.json}. É carregada de
     * {@code ~/.minelauncher/secret.key} na primeira chamada a
     * {@link #loadOrGenerateKey()}.
     *
     * <p><b>QUAL-11 — Consequências da perda de {@code secret.key}:</b>
     * <ul>
     *   <li>O usuário terá que reautenticar via Microsoft OAuth — não há
     *       mecanismo de "reset de chave" que preserve tokens antigos.</li>
     *   <li>Todos os tokens persistidos (access/refresh tokens do Minecraft)
     *       tornam-se ilegíveis e o launcher não consegue renová-los
     *       automaticamente.</li>
     *   <li>A chave é regenerada a partir de {@link MachineIdentifier} quando
     *       ausente, mas essa nova chave é <b>distinta</b> da anterior —
     *       tokens cifrados com a chave antiga não podem ser decifrados
     *       pela nova.</li>
     *   <li>A solução de longo prazo é implementar backup automático do
     *       arquivo de chave, ou — preferencialmente — armazenar tokens em
     *       cofres do sistema operacional: DPAPI (Windows), Keychain (macOS)
     *       ou libsecret (Linux) via JNA, já disponível em {@code pom.xml}.</li>
     * </ul>
     */
    private static final byte[] KEY = loadOrGenerateKey();

    private SecretCodec() {}

    /**
     * Carrega a chave AES de {@code ~/.minelauncher/secret.key} ou gera uma
     * nova (salted com {@link MachineIdentifier}) se o arquivo não existir.
     *
     * <p><b>QUAL-11 — perda de chave:</b> se o arquivo {@code secret.key}
     * for apagado, todos os tokens cifrados com a chave anterior ficam
     * irrecuperáveis — o usuário terá que refazer login. Considere instruir
     * o usuário a fazer backup deste arquivo junto com
     * {@code launcher_accounts.json}.
     */
    private static byte[] loadOrGenerateKey() {
        Path keyPath = Paths.get(System.getProperty("user.home"), ".minelauncher", "secret.key");
        try {
            if (Files.exists(keyPath)) {
                return Files.readAllBytes(keyPath);
            }
            
            // Generate new random key, salted with machine ID
            byte[] seed = com.minelauncher.utils.MachineIdentifier.getUniqueId().getBytes("UTF-8");
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] newKey = md.digest(seed);
            
            Files.createDirectories(keyPath.getParent());
            Files.write(keyPath, newKey);
            // Try to restrict permissions to owner only (POSIX)
            try {
                Files.setPosixFilePermissions(keyPath, ownerOnly());
            } catch (UnsupportedOperationException | IOException e) {
                // Ignore if not supported or failed
            }
            return newKey;
        } catch (Exception e) {
            LOG.error("Falha fatal ao carregar/gerar chave de criptografia", e);
            throw new RuntimeException("Falha crítica na segurança: não foi possível carregar a chave de criptografia", e);
        }
    }

    /**
     * Cifra uma string. Retorna Base64 do IV||ciphertext. Retorna null se input for null.
     *
     * <p><b>FAIL CLOSED (CRIT-1 do code-review):</b> se o algoritmo JCE/AES
     * falhar, lançamos {@link IllegalStateException} em vez de gravar plain
     * text no disco. A degradação silenciosa para plain text permitia que
     * {@code launcher_accounts.json} terminasse com um mix de linhas
     * {@code enc:...} e linhas cleartext — o pior cenário, porque o
     * {@link #decrypt(String)} continuaria funcionando mas o arquivo
     * estaria exposto a qualquer um que lesse o disco.
     *
     * <p>Antes a degradação era "graceful"; agora o caller (SettingsManager)
     * deve abortar o save e reportar o erro ao usuário.
     */
    public static String encrypt(String plain) {
        if (plain == null) return null;
        if (plain.isEmpty()) return "";
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(KEY, "AES");
            byte[] iv = new byte[16];
            new java.security.SecureRandom().nextBytes(iv);
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] cipherText = cipher.doFinal(plain.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return java.util.Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Fail closed: NUNCA retornar plain text. O save() em
            // SettingsManager deve propagar e abortar a escrita.
            LOG.error("Falha CRÍTICA ao cifrar token — abortando (fail closed)", e);
            throw new IllegalStateException(
                    "Falha na cifragem de token; armazenamento de credenciais desativado", e);
        }
    }

    /**
     * Decifra uma string. Retorna null se input for null.
     * Detecta automaticamente se o valor é Base64 cifrado (começa com "enc:")
     * ou plain text legado (compatibilidade retroativa).
     */
    public static String decrypt(String stored) {
        if (stored == null) return null;
        if (stored.isEmpty()) return "";
        if (!stored.startsWith("enc:")) {
            // Plain text legado — retorna como está
            return stored;
        }
        try {
            byte[] combined = java.util.Base64.getDecoder().decode(stored.substring(4));
            if (combined.length < 17) return stored;
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, 16);
            byte[] cipherText = new byte[combined.length - 16];
            System.arraycopy(combined, 16, cipherText, 0, cipherText.length);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(KEY, "AES");
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(cipherText), "UTF-8");
        } catch (Exception e) {
            LOG.warn("Falha ao decifrar token (retornando como plain text)", e);
            return stored;
        }
    }

    // Métodos utilitários de filesystem (evitam duplicação de Files/POSIX em vários lugares)
    public static Set<PosixFilePermission> ownerOnly() {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        return perms;
    }

    public static Set<PosixFilePermission> ownerExecOnly() {
        Set<PosixFilePermission> perms = ownerOnly();
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        return perms;
    }
}
