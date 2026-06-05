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
        return (version != null) ? version : "1.1.1"; // Fallback para dev
    }

    public String checkVersion() {
        String currentVersion = getCurrentVersion();
        try {
            Request request = new Request.Builder().url(VERSION_URL).build();
            try (Response response = HttpClient.getInstance().newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                String remoteVersion = json.get("version").getAsString();
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
            File newJar = new File("minecraft-launcher-new.jar");
            Request request = new Request.Builder().url(downloadUrl).build();
            try (Response response = HttpClient.getInstance().newCall(request).execute()) {
                Files.copy(response.body().byteStream(), newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Criar script de swap (multiplataforma)
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                createWindowsSwapper();
            } else {
                createUnixSwapper();
            }
            
            System.exit(0);
        } catch (IOException e) {
            ErrorReporter.report(e, "UpdateService: download");
        }
    }

    private void createUnixSwapper() throws IOException {
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
        
        String script = "#!/bin/bash\nsleep 2\nmv minecraft-launcher-new.jar minecraft-launcher.jar\njava " + javaArgs + " -jar minecraft-launcher.jar > update_log.txt 2>&1 &";
        Files.writeString(new File("update_swap.sh").toPath(), script);
        new ProcessBuilder("chmod", "+x", "update_swap.sh").start();
        new ProcessBuilder("./update_swap.sh").start();
    }

    private void createWindowsSwapper() throws IOException {
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

        String script = "@echo off\ntimeout /t 2\nmove /y minecraft-launcher-new.jar minecraft-launcher.jar\nstart java " + javaArgs + " -jar minecraft-launcher.jar";
        Files.writeString(new File("update_swap.bat").toPath(), script);
        new ProcessBuilder("cmd", "/c", "start", "update_swap.bat").start();
    }
}
