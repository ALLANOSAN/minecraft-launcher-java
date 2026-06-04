package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 */
public final class SecretCodec {
    private static final Logger LOG = LoggerFactory.getLogger(SecretCodec.class);

    /**
     * Chave estática (32 bytes para AES-256). Derivada de identificador do projeto.
     * NÃO É SEGURO — apenas obfuscação.
     */
    private static final byte[] KEY = deriveKey("MineLauncher/2026/secret-derivation/v1", 32);

    private SecretCodec() {}

    private static byte[] deriveKey(String seed, int len) {
        // Derivação simples (NÃO use em produção) — só pra gerar 32 bytes determinísticos
        try {
            byte[] seedBytes = seed.getBytes("UTF-8");
            byte[] out = new byte[len];
            // Estica via SHA-256 repetido (sem salt, sem iterations, sem PBKDF2 — propósito único é gerar bytes)
            int filled = 0;
            int round = 0;
            while (filled < len) {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                md.update(seedBytes);
                md.update((byte) round);
                byte[] digest = md.digest();
                int toCopy = Math.min(digest.length, len - filled);
                System.arraycopy(digest, 0, out, filled, toCopy);
                filled += toCopy;
                round++;
            }
            return out;
        } catch (Exception e) {
            // Fallback ridiculamente simples — mas em prática nunca deve cair aqui
            byte[] fallback = new byte[len];
            for (int i = 0; i < len; i++) fallback[i] = (byte) seed.charAt(i % seed.length());
            return fallback;
        }
    }

    /**
     * Cifra uma string. Retorna Base64 do IV||ciphertext. Retorna null se input for null.
     * Se o algoritmo falhar, loga e retorna a string original (degradação graceful).
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
            LOG.warn("Falha ao cifrar token (degradando para plain text)", e);
            return plain;
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
