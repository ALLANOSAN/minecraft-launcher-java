package com.minelauncher.launcher;

import com.minelauncher.models.GameProfile;
import com.minelauncher.models.LaunchProfile;
import com.minelauncher.models.VersionDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para {@link GameLauncher} cobrindo achados do code-review.
 *
 * <p>Foco:
 * <ul>
 *   <li>{@link GameLauncher#assertSafeVersionId} — defesa contra path
 *       traversal (MEDIUM #2)</li>
 *   <li>{@code replacePlaceholders} — single-pass via Matcher, sem
 *       substituição recursiva, tokens desconhecidos preservados
 *       (MEDIUM #6). Acessado via reflection por ser private.</li>
 *   <li>{@link GameLauncher#resolveVersionId} — fallback com match
 *       exato preferencial sobre substring matching (MEDIUM #3)</li>
 * </ul>
 */
class GameLauncherTest {

    private File tempDir;
    private GameLauncher launcher;
    private VersionManager versionManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("gamelauncher-test").toFile();
        versionManager = new VersionManager(tempDir);
        launcher = new GameLauncher(tempDir, versionManager);
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

    // --- assertSafeVersionId (package-private) ---

    @Test
    void assertSafeVersionId_acceptsValidIds() {
        assertEquals("1.20.1", GameLauncher.assertSafeVersionId("1.20.1"));
        assertEquals("1.20.1-forge-47.3.0", GameLauncher.assertSafeVersionId("1.20.1-forge-47.3.0"));
        assertEquals("fabric-loader-0.16.5-1.21.4", GameLauncher.assertSafeVersionId("fabric-loader-0.16.5-1.21.4"));
        assertEquals("neoforge-21.0.0", GameLauncher.assertSafeVersionId("neoforge-21.0.0"));
    }

    @Test
    void assertSafeVersionId_rejectsNullAndEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId(null));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId(""));
    }

    @Test
    void assertSafeVersionId_rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("../etc"));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId(".."));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("1.20.1/../etc"));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("1.20.1/etc"));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("1.20.1\\etc"));
        assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("1.20.1\0etc"));
    }

    // --- M2: context-aware error messages (commit 655ae88) ---

    @Test
    void assertSafeVersionId_includesContextInErrorMessage() {
        // M2 do code-review: mensagens devem identificar o campo
        // (ex: "mcVersion") para facilitar debug de perfis legados
        // corrompidos salvos por versão antiga do launcher.
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId(null, "mcVersion"));
        assertTrue(ex1.getMessage().contains("mcVersion"),
                "erro deve mencionar o campo: " + ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> GameLauncher.assertSafeVersionId("../etc", "candidate (loader=forge)"));
        assertTrue(ex2.getMessage().contains("candidate"),
                "erro deve mencionar o campo: " + ex2.getMessage());
        assertTrue(ex2.getMessage().contains("../etc"),
                "erro deve incluir o valor recusado para debug: " + ex2.getMessage());
    }

    @Test
    void resolveVersionId_legacyProfileWithNullMcVersionThrowsClearError() {
        // M2 do code-review: regressão para o cenário "perfil salvo por
        // versão antiga do launcher com gameVersion=null". Antes do
        // fix, retornava null e quebrava com NPE em getVersionDetail.
        // Agora, joga IAE com mensagem que identifica o perfil.
        LaunchProfile legacy = new LaunchProfile("meuPerfilAntigo", null);
        legacy.setModLoader("vanilla");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> launcher.resolveVersionId(legacy));
        assertTrue(ex.getMessage().contains("mcVersion"),
                "erro deve mencionar o campo mcVersion: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("meuPerfilAntigo"),
                "erro deve mencionar o nome do perfil: " + ex.getMessage());
    }

    @Test
    void resolveVersionId_moddedProfileWithPathTraversalThrowsClearError() {
        // M2 do code-review: profile com modloader + mcVersion com
        // path traversal deve ser rejeitado com mensagem clara.
        LaunchProfile malicious = new LaunchProfile("evil", "../etc/passwd");
        malicious.setModLoader("forge");
        malicious.setModLoaderVersion("47.3.0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> launcher.resolveVersionId(malicious));
        assertTrue(ex.getMessage().contains("../etc/passwd"),
                "erro deve incluir o valor recusado: " + ex.getMessage());
    }

    // --- replacePlaceholders (private, via reflection) ---

    @Test
    void replacePlaceholders_singlePassNoRecursion() throws Exception {
        // Se o placeholder "key1" tem valor "${key2}", o valor NÃO
        // deve ser re-substituído pela próxima iteração. Antes do
        // fix (String.replace em loop), era recursivo.
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("a", "${b}");
        placeholders.put("b", "final");
        String result = invokeReplacePlaceholders("hello ${a} world", placeholders);
        assertEquals("hello ${b} world", result, "substituição recursiva deve ser evitada");
    }

    @Test
    void replacePlaceholders_preservesUnknownTokens() throws Exception {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("known", "ok");
        String result = invokeReplacePlaceholders("${known} and ${unknown}", placeholders);
        assertEquals("ok and ${unknown}", result,
                "tokens desconhecidos devem ser preservados como ${unknown}");
    }

    @Test
    void replacePlaceholders_escapesSpecialCharsInValues() throws Exception {
        // Valor com $ ou \ não pode quebrar o regex replacement
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("path", "C:\\Users\\$USER\\bin");
        String result = invokeReplacePlaceholders("-cp=${path}", placeholders);
        assertEquals("-cp=C:\\Users\\$USER\\bin", result);
    }

    @Test
    void replacePlaceholders_replacesAllOccurrences() throws Exception {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("x", "Y");
        String result = invokeReplacePlaceholders("${x}-${x}-${x}", placeholders);
        assertEquals("Y-Y-Y", result);
    }

    @Test
    void buildLaunchArgs_updatesForgeIgnoreList() throws Exception {
        // Mock do VersionDetail com args de Forge
        VersionDetail detail = new VersionDetail();
        detail.setMainClass("cpw.mods.bootstraplauncher.BootstrapLauncher");
        // Precisamos setar o ID para o teste
        java.lang.reflect.Field idField = VersionDetail.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(detail, "1.20.1-forge-47.4.10");

        VersionDetail.Arguments argsObj = new VersionDetail.Arguments();
        argsObj.setJvm(java.util.Arrays.asList("-DignoreList=bootstraplauncher,forge-", "-DsomeOther=value"));
        detail.setArguments(argsObj);
        detail.setLibraries(java.util.Collections.emptyList());

        LaunchProfile profile = new LaunchProfile("test", "1.20.1");
        profile.setMaxRam(2048);
        profile.setMinRam(512);

        GameProfile account = new GameProfile("Player", java.util.UUID.randomUUID(), false);
        account.setAccessToken("token123");

        Method m = GameLauncher.class.getDeclaredMethod("buildLaunchArgs",
                VersionDetail.class, LaunchProfile.class, GameProfile.class,
                String.class, String.class, File.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) m.invoke(launcher,
                detail, profile, account, "cp1:cp2", "/usr/bin/java", tempDir);

        // Verifica se o ignoreList foi atualizado corretamente
        boolean foundUpdatedIgnoreList = false;
        for (String arg : result) {
            if (arg.startsWith("-DignoreList=")) {
                foundUpdatedIgnoreList = true;
                assertTrue(arg.contains("1.20.1-forge-47.4.10"), "Deve conter o ID da versão: " + arg);
                assertTrue(arg.contains("_1."), "Deve conter o prefixo de módulo automático: " + arg);
                assertTrue(arg.contains("bootstraplauncher"), "Deve manter os originais: " + arg);
            }
        }
        assertTrue(foundUpdatedIgnoreList, "Argumento -DignoreList não encontrado no comando gerado");
    }

    private String invokeReplacePlaceholders(String input, Map<String, String> placeholders)
            throws Exception {
        Method m = GameLauncher.class.getDeclaredMethod("replacePlaceholders", String.class, Map.class);
        m.setAccessible(true);
        return (String) m.invoke(launcher, input, placeholders);
    }

    // --- resolveVersionId (public) ---

    @Test
    void resolveVersionId_vanillaReturnsMcVersion() {
        LaunchProfile p = new LaunchProfile("test", "1.20.1");
        p.setModLoader("vanilla");
        assertEquals("1.20.1", launcher.resolveVersionId(p));
    }

    @Test
    void resolveVersionId_nullLoaderReturnsMcVersion() {
        LaunchProfile p = new LaunchProfile("test", "1.20.1");
        p.setModLoader(null);
        assertEquals("1.20.1", launcher.resolveVersionId(p));
    }

    @Test
    void resolveVersionId_buildsCandidateAndReturnsItFromVersions() throws IOException {
        // L1 do code-review (commit 655ae88): os 2 testes antigos
        // (_forgeBuildsExpectedCandidate e _fabricBuildsExpectedCandidate)
        // tinham nomes enganosos — o assertion era "retorna mcVersion
        // (fallback)", não "retorna o candidate construído". Aqui
        // criamos o candidate exato em versions/ e verificamos que o
        // método o retorna. Cobre forge E fabric no mesmo teste
        // (eram redundantes).
        assertCandidateReturned("forge", "47.3.0", "1.20.1", "1.20.1-forge-47.3.0");
        assertCandidateReturned("fabric", "0.16.5", "1.21.4", "fabric-loader-0.16.5-1.21.4");
    }

    /** Helper: cria o candidate em versions/<id>/<id>.json e resolve. */
    private void assertCandidateReturned(String loader, String loaderVersion,
                                          String mcVersion, String candidate) throws IOException {
        File dir = new File(tempDir, "versions/" + candidate);
        dir.mkdirs();
        new File(dir, candidate + ".json").createNewFile();

        LaunchProfile p = new LaunchProfile("test", mcVersion);
        p.setModLoader(loader);
        p.setModLoaderVersion(loaderVersion);

        assertEquals(candidate, launcher.resolveVersionId(p),
                "loader=" + loader + " deve construir candidate " + candidate);
    }

    @Test
    void resolveVersionId_primaryMatchReturnsImmediately() throws IOException {
        // Cria o candidate exato com .json — primary match deve retornar direto
        File primary = new File(tempDir, "versions/1.20.1-forge-47.3.0");
        primary.mkdirs();
        new File(primary, "1.20.1-forge-47.3.0.json").createNewFile();

        // Cria também um candidato mais recente, mas irrelevante (primary tem prioridade)
        File decoy = new File(tempDir, "versions/1.20.1-forge-99.0.0");
        decoy.mkdirs();
        new File(decoy, "1.20.1-forge-99.0.0.json").createNewFile();
        decoy.setLastModified(System.currentTimeMillis() + 60_000);

        LaunchProfile p = new LaunchProfile("test", "1.20.1");
        p.setModLoader("forge");
        p.setModLoaderVersion("47.3.0");

        String result = launcher.resolveVersionId(p);
        assertEquals("1.20.1-forge-47.3.0", result,
                "primary match deve retornar antes do fallback");
    }

    @Test
    void resolveVersionId_fallbackPicksMostRecentByMtime() throws IOException {
        // Cria 2 candidatos substring-match. O candidate exato (1.20.1-forge-47.3.0)
        // NÃO existe, então primary check falha e cai no fallback.
        File older = new File(tempDir, "versions/1.20.1-forge-46.0.0");
        older.mkdirs();
        new File(older, "1.20.1-forge-46.0.0.json").createNewFile();
        older.setLastModified(1_000_000_000_000L);

        File newer = new File(tempDir, "versions/1.20.1-forge-45.0.0");
        newer.mkdirs();
        new File(newer, "1.20.1-forge-45.0.0.json").createNewFile();
        newer.setLastModified(2_000_000_000_000L);

        LaunchProfile p = new LaunchProfile("test", "1.20.1");
        p.setModLoader("forge");
        p.setModLoaderVersion("47.3.0");  // candidate = "1.20.1-forge-47.3.0" — não existe

        String result = launcher.resolveVersionId(p);
        assertEquals("1.20.1-forge-45.0.0", result,
                "fallback sem match exato deve escolher o mais recente (mtime)");
    }

    @Test
    void resolveVersionId_fallbackReturnsVanillaWhenNoCandidates() throws IOException {
        // Nenhum diretório em versions/ que case com forge+1.20.1
        File unrelated = new File(tempDir, "versions/1.21-fabric-loader-0.16.5");
        unrelated.mkdirs();
        new File(unrelated, "1.21-fabric-loader-0.16.5.json").createNewFile();

        LaunchProfile p = new LaunchProfile("test", "1.20.1");
        p.setModLoader("forge");
        p.setModLoaderVersion("47.3.0");

        // Sem candidatos, fallback loga warning e retorna mcVersion vanilla
        String result = launcher.resolveVersionId(p);
        assertEquals("1.20.1", result);
    }

    @Test
    void resolveVersionId_rejectsPathTraversalInMcVersion() {
        LaunchProfile p = new LaunchProfile("test", "../etc/passwd");
        p.setModLoader("vanilla");
        assertThrows(IllegalArgumentException.class,
                () -> launcher.resolveVersionId(p),
                "resolveVersionId deve rejeitar mcVersion com path traversal");
    }
}
