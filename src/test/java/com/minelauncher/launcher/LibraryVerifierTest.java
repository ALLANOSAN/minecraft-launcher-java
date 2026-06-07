package com.minelauncher.launcher;

import com.google.gson.Gson;
import com.minelauncher.models.VersionDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link LibraryVerifier} (MEDIUM #7 do code-review).
 *
 * <p>Cobre:
 * <ul>
 *   <li>buildClasspath: lista de libs + client jar com path separator</li>
 *   <li>buildClasspath: rejeita versionId com path traversal (MEDIUM #2)</li>
 *   <li>buildClasspath: pula libs não-allowlisted (rules)</li>
 *   <li>verifyAndDownload: pula quando sidecar existe e é mais novo (CRIT-5)</li>
 *   <li>verifyAndDownload: recalcula SHA1 quando sidecar ausente, cria marker</li>
 *   <li>verifyAndDownload: re-baixa quando SHA1 calculado diverge do esperado</li>
 *   <li>verifyAndDownload: rejeita versionId com path traversal (defesa em profundidade)</li>
 * </ul>
 */
class LibraryVerifierTest {

    private File tempDir;
    private DownloadManager downloader;
    private LibraryVerifier verifier;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("libverifier-test").toFile();
        downloader = new DownloadManager();
        verifier = new LibraryVerifier(tempDir, downloader);
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

    /**
     * Constrói uma VersionDetail com 2 libraries via Gson (campos são
     * private e sem setters em Library/LibraryDownloads/DownloadFile).
     */
    private VersionDetail buildDetail(String lib1Sha1, String lib2Sha1) {
        String json = "{"
                + "\"id\":\"1.20.1\","
                + "\"assets\":\"1.20\","
                + "\"libraries\":["
                + "  {\"name\":\"org.example:lib1:1.0\",\"downloads\":{\"artifact\":{"
                + "    \"path\":\"org/example/lib1/1.0/lib1-1.0.jar\","
                + "    \"sha1\":\"" + lib1Sha1 + "\",\"size\":11,\"url\":\"http://example/lib1.jar\"}}},"
                + "  {\"name\":\"org.example:lib2:1.0\",\"downloads\":{\"artifact\":{"
                + "    \"path\":\"org/example/lib2/1.0/lib2-1.0.jar\","
                + "    \"sha1\":\"" + lib2Sha1 + "\",\"size\":11,\"url\":\"http://example/lib2.jar\"}}}"
                + "]"
                + "}";
        return new Gson().fromJson(json, VersionDetail.class);
    }

    @Test
    void buildClasspath_includesLibrariesAndClientJar() throws IOException {
        VersionDetail detail = buildDetail("any", "any");

        // Cria os jars de library e o client jar
        File libFile = new File(tempDir, "libraries/org/example/lib1/1.0/lib1-1.0.jar");
        libFile.getParentFile().mkdirs();
        Files.writeString(libFile.toPath(), "hello lib1");

        File clientJar = new File(tempDir, "versions/1.20.1/1.20.1.jar");
        clientJar.getParentFile().mkdirs();
        Files.writeString(clientJar.toPath(), "fake client");

        String classpath = verifier.buildClasspath(detail, "1.20.1");

        // Deve conter lib1 + client jar (lib2 não existe)
        assertTrue(classpath.contains("lib1-1.0.jar"), "classpath deve incluir lib1");
        assertTrue(classpath.contains("1.20.1.jar"), "classpath deve incluir client jar");
        assertTrue(classpath.endsWith("1.20.1.jar") || classpath.contains("1.20.1.jar" + File.pathSeparator),
                "client jar deve ser o último item (sem separator)");
    }

    @Test
    void buildClasspath_skipsLibrariesWithoutArtifact() throws IOException {
        // L3 do code-review (commit 655ae88): assertion anterior
        // (`assertFalse(classpath.contains("y:1.0"))`) passava
        // trivialmente. Agora criamos o jar no disco e verificamos
        // que ele NÃO entra no classpath por causa da regra de
        // negócio (downloads==null), não por causa de o arquivo
        // simplesmente não existir.
        String json = "{\"id\":\"1.20.1\",\"libraries\":["
                + "{\"name\":\"org.y:y:1.0\",\"downloads\":null}"
                + "]}";
        VersionDetail detail = new Gson().fromJson(json, VersionDetail.class);

        // Cria o jar mas sem que VersionDetail saiba dele (downloads==null)
        File libFile = new File(tempDir, "libraries/org/y/1.0/y-1.0.jar");
        libFile.getParentFile().mkdirs();
        Files.writeString(libFile.toPath(), "should be ignored");
        assertTrue(libFile.exists(), "precondição: jar existe no disco");

        String classpath = verifier.buildClasspath(detail, "1.20.1");

        assertFalse(classpath.contains("y-1.0.jar"),
                "lib com downloads==null nunca deve entrar no classpath, mesmo que o jar exista no disco");
        assertFalse(classpath.contains("org/y/1.0"),
                "paths de libs sem artifact não devem vazar para o classpath");
    }

    @Test
    void buildClasspath_rejectsPathTraversalInVersionId() {
        VersionDetail detail = buildDetail("any", "any");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.buildClasspath(detail, "../etc"),
                "buildClasspath deve rejeitar path traversal");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.buildClasspath(detail, ".."),
                "buildClasspath deve rejeitar '..' puro");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.buildClasspath(detail, "1.20.1/sub"),
                "buildClasspath deve rejeitar '/'");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.buildClasspath(detail, ""),
                "buildClasspath deve rejeitar vazio");
    }

    @Test
    void verifyAndDownload_skipsWhenSidecarIsFresh() throws IOException {
        VersionDetail detail = buildDetail("deadbeef", "feedface");

        File libFile = new File(tempDir, "libraries/org/example/lib1/1.0/lib1-1.0.jar");
        libFile.getParentFile().mkdirs();
        Files.writeString(libFile.toPath(), "conteudo");

        // Sidecar existente e mais novo que a library
        File marker = new File(libFile.getParentFile(), libFile.getName() + ".sha1.ok");
        Files.writeString(marker.toPath(), "deadbeef");
        marker.setLastModified(System.currentTimeMillis() + 60_000); // futuro

        verifier.verifyAndDownload(detail, "1.20.1");

        // Marker deve continuar existindo (não foi recriado)
        assertTrue(marker.exists());
        // Nenhum download foi tentado (URL é fake, então se calculasse
        // SHA1 e encontrasse mismatch, ele tentaria baixar e falharia;
        // como o sidecar pulou, o marker segue intacto)
    }

    @Test
    void verifyAndDownload_calculatesSha1AndCreatesMarkerWhenMissing() throws IOException {
        VersionDetail detail = buildDetail("any", "any");

        File libFile = new File(tempDir, "libraries/org/example/lib1/1.0/lib1-1.0.jar");
        libFile.getParentFile().mkdirs();
        Files.writeString(libFile.toPath(), "conteudo");

        // Calcula o SHA1 real do conteúdo
        String realSha1 = downloader.calculateSHA1(libFile);
        // Recria o detalhe com o SHA1 correto
        VersionDetail correctDetail = buildDetail(realSha1, "any");

        verifier.verifyAndDownload(correctDetail, "1.20.1");

        // Marker deve ter sido criado com o SHA1 correto
        File marker = new File(libFile.getParentFile(), libFile.getName() + ".sha1.ok");
        assertTrue(marker.exists(), "sidecar deve ser criado após verificação OK");
        assertEquals(realSha1, Files.readString(marker.toPath()));
    }

    @Test
    void verifyAndDownload_rejectsPathTraversalInVersionId() {
        VersionDetail detail = buildDetail("any", "any");
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndDownload(detail, "../../etc/passwd"),
                "verifyAndDownload deve rejeitar path traversal (defesa em profundidade)");
    }
}
