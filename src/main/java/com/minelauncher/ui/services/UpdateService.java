package com.minelauncher.ui.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.launcher.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);
    private static final String VERSION_URL = "https://raw.githubusercontent.com/allanosan/minecraft-launcher-java/main/version.json";

    private String getCurrentVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return (version != null) ? version : com.minelauncher.utils.AppConstants.APP_VERSION;
    }

    public String checkVersion() {
        String currentVersion = getCurrentVersion();
        LOG.info("Versão atual detectada: {}", currentVersion);
        try {
            Request request = new Request.Builder().url(VERSION_URL).build();
            try (Response response = HttpClient.getInstance().newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                String remoteVersion = json.get("version").getAsString();
                LOG.info("Versão remota disponível: {}", remoteVersion);
                if (!remoteVersion.equals(currentVersion)) {
                    return json.get("downloadUrl").getAsString();
                }
            }
        } catch (Exception e) {
            LOG.warn("Falha ao checar atualizações", e);
        }
        return null;
    }

    public void downloadAndUpdate(String downloadUrl) {
        LOG.info("Baixando atualização: {}", downloadUrl);
        try {
            // Detectar o JAR atual
            File currentJar;
            try {
                currentJar = new File(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (Exception e) {
                // Fallback se não conseguir detectar (ex: rodando em IDE)
                LOG.warn("Não foi possível detectar o caminho do JAR atual, usando fallback");
                currentJar = new File("minecraft-launcher.jar");
            }

            File newJar = new File(currentJar.getParentFile(), currentJar.getName() + ".new");
            
            Request request = new Request.Builder().url(downloadUrl).build();
            try (Response response = HttpClient.getInstance().newCall(request).execute()) {
                Files.copy(response.body().byteStream(), newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Criar script de swap (multiplataforma)
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                createWindowsSwapper(currentJar, newJar);
            } else {
                createUnixSwapper(currentJar, newJar);
            }
            
            System.exit(0);
        } catch (IOException e) {
            ErrorReporter.report(e, "UpdateService: download");
        }
    }

    private void createUnixSwapper(File currentJar, File newJar) throws IOException {
        String javaArgs = "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.collections=ALL-UNNAMED " +
                         "--add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED " +
                         "--add-opens javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED " +
                         "--add-opens javafx.fxml/com.sun.javafx.fxml=ALL-UNNAMED";
        
        String script = "#!/bin/bash\n" +
                        "sleep 2\n" +
                        "mv \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"\n" +
                        "java " + javaArgs + " -jar \"" + currentJar.getAbsolutePath() + "\" > update_log.txt 2>&1 &";
        
        File scriptFile = new File(currentJar.getParentFile(), "update_swap.sh");
        Files.writeString(scriptFile.toPath(), script);
        new ProcessBuilder("chmod", "+x", scriptFile.getAbsolutePath()).start();
        new ProcessBuilder("./" + scriptFile.getName()).directory(currentJar.getParentFile()).start();
    }

    private void createWindowsSwapper(File currentJar, File newJar) throws IOException {
        String javaArgs = "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.glass.ui=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED " +
                         "--add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED " +
                         "--add-opens javafx.base/com.sun.javafx.collections=ALL-UNNAMED " +
                         "--add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED " +
                         "--add-opens javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED " +
                         "--add-opens javafx.fxml/com.sun.javafx.fxml=ALL-UNNAMED";

        String script = "@echo off\n" +
                        "timeout /t 2 /nobreak > nul\n" +
                        "move /y \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"\n" +
                        "start java " + javaArgs + " -jar \"" + currentJar.getAbsolutePath() + "\"";
        
        File scriptFile = new File(currentJar.getParentFile(), "update_swap.bat");
        Files.writeString(scriptFile.toPath(), script);
        new ProcessBuilder("cmd", "/c", "start", scriptFile.getName()).directory(currentJar.getParentFile()).start();
    }
}
