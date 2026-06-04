package com.minelauncher.launcher;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.util.function.BiConsumer;

public class DownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadManager.class);
    private final OkHttpClient client;

    public DownloadManager() {
        this.client = HttpClient.getInstance();
    }

    private static final int MAX_RETRIES = 3;

    /**
     * Baixa um arquivo e verifica SHA1, com retry em caso de falha.
     * @param url URL do arquivo
     * @param dest Caminho de destino
     * @param expectedSha1 SHA1 esperado (null para pular verificação)
     * @param progressCallback Callback (bytesBaixados, totalBytes)
     */
    public void download(String url, File dest, String expectedSha1,
                         BiConsumer<Long, Long> progressCallback) throws IOException {

        // Verificar se já existe e SHA1 confere
        if (dest.exists() && expectedSha1 != null) {
            String existingSha1 = calculateSHA1(dest);
            if (expectedSha1.equals(existingSha1)) {
                LOG.debug("Arquivo já válido: {}", dest.getName());
                if (progressCallback != null) progressCallback.accept(dest.length(), dest.length());
                return;
            }
        }

        // Criar diretórios pai se necessário
        dest.getParentFile().mkdirs();

        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                attemptDownload(url, dest, expectedSha1, progressCallback);
                LOG.debug("Download concluído: {} (tentativa {})", dest.getName(), attempt);
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000; // 1s, 2s, 4s
                    LOG.warn("Tentativa {}/{} falhou para {}: {}. Retry em {}ms...",
                            attempt, MAX_RETRIES, dest.getName(), e.getMessage(), backoffMs);
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrompido", ie);
                    }
                }
            }
        }
        throw new IOException("Download falhou após " + MAX_RETRIES + " tentativas: " + dest.getName(), lastException);
    }

    private void attemptDownload(String url, File dest, String expectedSha1,
                                  BiConsumer<Long, Long> progressCallback) throws IOException {
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao baixar " + url + ": HTTP " + response.code());
            }

            long totalBytes = response.body().contentLength();
            long downloadedBytes = 0;

            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(dest)) {

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;

                    if (progressCallback != null) {
                        progressCallback.accept(downloadedBytes, totalBytes);
                    }
                }
            }

            // Verificar SHA1 após download
            if (expectedSha1 != null) {
                String actualSha1 = calculateSHA1(dest);
                if (!expectedSha1.equals(actualSha1)) {
                    dest.delete();
                    throw new IOException("SHA1 mismatch para " + dest.getName() +
                            ": esperado " + expectedSha1 + ", obtido " + actualSha1);
                }
            }
        }
    }

    /**
     * Calcula SHA1 de um arquivo
     */
    public String calculateSHA1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Erro ao calcular SHA1", e);
        }
    }
}
