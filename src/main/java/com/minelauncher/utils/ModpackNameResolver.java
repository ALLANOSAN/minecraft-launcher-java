package com.minelauncher.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.models.LaunchProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolve o nome "humano" de um diretório de modpack.
 *
 * <p>Ordem de prioridade (do mais amigável pro menos):
 * <ol>
 *   <li>{@code launcher_manifest.json} → campo {@code name} (formato do MineLauncher)</li>
 *   <li>{@code manifest.json} → campo {@code name} (formato CurseForge)</li>
 *   <li>Perfil cujo gameDir aponta para este diretório</li>
 *   <li>Nome do diretório (fallback — pode ser UUID/legível)</li>
 * </ol>
 *
 * <p>Antes vivia como método privado em {@code ModActions} (H-2: god class).
 */
public final class ModpackNameResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ModpackNameResolver.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-fA-F-]{20,}$");

    private static final String[] MANIFEST_FILES = {
            "launcher_manifest.json", "manifest.json"
    };

    private ModpackNameResolver() {}

    public static String resolve(File modpackDir, List<LaunchProfile> profiles) {
        if (modpackDir == null) return null;
        String dirName = modpackDir.getName();
        String canonical = modpackDir.getAbsolutePath();

        // 1 e 2: manifests
        for (String mf : MANIFEST_FILES) {
            File manifest = new File(modpackDir, mf);
            if (!manifest.exists()) continue;
            try {
                String json = Files.readString(manifest.toPath());
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("name")) {
                    String n = obj.get("name").getAsString();
                    if (n != null && !n.isBlank() && !looksLikeGarbage(n)) {
                        return n;
                    }
                }
            } catch (Exception e) {
                LOG.debug("Erro ao ler manifesto {}: {}", manifest.getName(), e.getMessage());
            }
        }

        // 3: profile matching por gameDir
        if (looksLikeGarbage(dirName) && profiles != null) {
            for (LaunchProfile p : profiles) {
                String gd = p.getGameDir();
                if (gd == null) continue;
                if (new File(gd).getAbsolutePath().equals(canonical)
                        && p.getName() != null && !p.getName().isBlank()) {
                    return p.getName();
                }
            }
        }

        return dirName;
    }

    /** Heurística: UUIDs e hashes longos são "lixo" para display. */
    public static boolean looksLikeGarbage(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        if (UUID_PATTERN.matcher(t).matches()) return true;
        return HASH_PATTERN.matcher(t).matches();
    }
}
