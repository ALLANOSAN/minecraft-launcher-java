package com.minelauncher.launcher;

import com.google.gson.Gson;
import com.minelauncher.models.VersionDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link NativesExtractor} (MEDIUM #7 do code-review).
 *
 * <p>Cobre:
 * <ul>
 *   <li>marker hit: sidecar válido + mtime compatível → sem re-extração</li>
 *   <li>marker miss: sem marker → extrai jars nativos + cria marker</li>
 *   <li>marker mismatch: marker existe mas com valor diferente → re-extrai</li>
 *   <li>Zip Slip: entry com path traversal é neutralizado via basename</li>
 *   <li>Sem natives para SO atual: lista vazia, marker ainda é gravado</li>
 *   <li>Defesa em profundidade: versionId com path traversal é rejeitado</li>
 * </ul>
 */
class NativesExtractorTest {

    private File tempDir;
    private NativesExtractor extractor;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("natives-test").toFile();
        extractor = new NativesExtractor(tempDir);
    }

    @AfterEach
    void tearDown() {
        deleteRecursively(tempDir);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    private VersionDetail buildDetailWithNative(Map<String, String> natives, Map<String, VersionDetail.DownloadFile> classifiers) {
        Gson gson = new Gson();
        String json = "{"
                + "\"id\":\"1.20.1\","
                + "\"libraries\":[{"
                + "  \"name\":\"org.lwjgl:lwjgl:3.3.1\","
                + "  \"natives\":" + gson.toJson(natives) + ","
                + "  \"downloads\":{\"classifiers\":" + gson.toJson(classifiers) + "}"
                + "}]"
                + "}";
        return gson.fromJson(json, VersionDetail.class);
    }

    /** Cria um jar com entries nativos em tempDir/libraries/<path>. */
    private File createNativeJar(String pathFromLibraries, String... entries) throws IOException {
        File jar = new File(tempDir, "libraries/" + pathFromLibraries);
        jar.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(jar);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (String e : entries) {
                ZipEntry entry = new ZipEntry(e);
                zos.putNextEntry(entry);
                // Conteúdo determinístico mas curto
                zos.write(("content-of-" + e).getBytes());
                zos.closeEntry();
            }
        }
        return jar;
    }

    private VersionDetail.DownloadFile dlFile(String path) {
        VersionDetail.DownloadFile df = new VersionDetail.DownloadFile();
        // campos são private sem setters — usa Gson round-trip
        return new Gson().fromJson(
                "{\"path\":\"" + path + "\",\"sha1\":\"x\",\"size\":1,\"url\":\"http://x\"}",
                VersionDetail.DownloadFile.class);
    }

    @Test
    void getNativesDir_extractsNativesOnFirstRun() throws IOException {
        File jar = createNativeJar("lwjgl/natives-linux/lwjgl-natives-linux-1.0.jar",
                "liblwjgl.so");
        VersionDetail detail = buildDetailWithNative(
                Map.of("linux", "natives-linux"),
                Map.of("natives-linux", dlFile("lwjgl/natives-linux/lwjgl-natives-linux-1.0.jar")));

        String path = extractor.getNativesDir(detail, "1.20.1");
        File nativesDir = new File(path);

        assertTrue(nativesDir.exists(), "nativesDir deve ser criado");
        File lib = new File(nativesDir, "liblwjgl.so");
        assertTrue(lib.exists(), "liblwjgl.so deve ter sido extraído");
        assertTrue(new File(nativesDir, ".extracted").exists(), "marker deve ser escrito");
    }

    @Test
    void getNativesDir_skipsExtractionWhenMarkerValid() throws IOException {
        File jar = createNativeJar("lwjgl/natives-linux/lwjgl-natives-linux-2.0.jar",
                "liblwjgl.so");
        VersionDetail detail = buildDetailWithNative(
                Map.of("linux", "natives-linux"),
                Map.of("natives-linux", dlFile("lwjgl/natives-linux/lwjgl-natives-linux-2.0.jar")));

        File nativesDir = new File(tempDir, "versions/1.20.1/natives");
        nativesDir.mkdirs();
        long mtimeSum = jar.lastModified();
        String expected = mtimeSum + ":1";
        File marker = new File(nativesDir, ".extracted");
        Files.writeString(marker.toPath(), expected);

        File lib = new File(nativesDir, "liblwjgl.so");
        if (lib.exists()) lib.delete();

        String path = extractor.getNativesDir(detail, "1.20.1");
        assertEquals(nativesDir.getAbsolutePath(), new File(path).getAbsolutePath());
        assertFalse(lib.exists(), "lib não deve ser re-extraída quando marker bate");
    }

    @Test
    void getNativesDir_reExtractsWhenMarkerMismatches() throws IOException {
        File jar = createNativeJar("lwjgl/natives-linux/lwjgl-natives-linux-3.0.jar",
                "liblwjgl.so");
        VersionDetail detail = buildDetailWithNative(
                Map.of("linux", "natives-linux"),
                Map.of("natives-linux", dlFile("lwjgl/natives-linux/lwjgl-natives-linux-3.0.jar")));

        File nativesDir = new File(tempDir, "versions/1.20.1/natives");
        nativesDir.mkdirs();
        Files.writeString(new File(nativesDir, ".extracted").toPath(), "wrong-value");

        String path = extractor.getNativesDir(detail, "1.20.1");
        File lib = new File(path, "liblwjgl.so");
        assertTrue(lib.exists(), "lib deve ser re-extraída quando marker não bate");
    }

    @Test
    void getNativesDir_neutralizesZipSlipEntry() throws IOException {
        File jar = createNativeJar("lwjgl/natives-linux/malicious.jar",
                "../../../etc/passwd.so", "legit.so");
        VersionDetail detail = buildDetailWithNative(
                Map.of("linux", "natives-linux"),
                Map.of("natives-linux", dlFile("lwjgl/natives-linux/malicious.jar")));

        String path = extractor.getNativesDir(detail, "1.20.1");
        File nativesDir = new File(path);

        assertTrue(new File(nativesDir, "passwd.so").exists(),
                "basename do entry malicioso deve acabar no nativesDir (Zip Slip neutralizado)");
        assertTrue(new File(nativesDir, "legit.so").exists());
        // Garante que nada escapou (best-effort: /etc/passwd.so pode
        // ter dono root e não poder checar, mas o nativesDir está
        // dentro de tempDir, então qualquer escape seria visível)
        File[] leaked = nativesDir.getParentFile().getParentFile().listFiles();
        if (leaked != null) {
            for (File f : leaked) {
                if (f.isDirectory() && f.getName().equals("etc")) {
                    fail("Zip Slip deixou diretório etc/ em " + f.getAbsolutePath());
                }
            }
        }
    }

    @Test
    void getNativesDir_writesMarkerEvenWithEmptyNatives() throws IOException {
        // Detalhe sem natives para o SO atual (natives map não tem chave "linux")
        VersionDetail detail = buildDetailWithNative(
                Map.of("windows", "natives-windows"),
                Map.of("natives-windows", dlFile("foo/windows.jar")));

        String path = extractor.getNativesDir(detail, "1.20.1");
        File nativesDir = new File(path);
        assertTrue(nativesDir.exists());
        // Marker é escrito mesmo com lista vazia (count=0)
        File marker = new File(nativesDir, ".extracted");
        assertTrue(marker.exists(), "marker deve existir para indicar 'extraído (0 libs)'");
        String content = Files.readString(marker.toPath());
        assertTrue(content.endsWith(":0"), "marker deve indicar count=0, foi: " + content);
    }

    @Test
    void getNativesDir_rejectsPathTraversalInVersionId() {
        VersionDetail detail = buildDetailWithNative(
                Map.of("linux", "natives-linux"),
                Map.of("natives-linux", dlFile("a.jar")));
        assertThrows(IllegalArgumentException.class,
                () -> extractor.getNativesDir(detail, "../etc"),
                "defesa em profundidade: versionId com path traversal deve abortar");
    }
}
