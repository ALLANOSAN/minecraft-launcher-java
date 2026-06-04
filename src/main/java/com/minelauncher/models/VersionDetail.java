package com.minelauncher.models;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class VersionDetail {
    private static final Logger LOG = LoggerFactory.getLogger(VersionDetail.class);
    @SerializedName("id") private String id;
    @SerializedName("type") private String type;
    @SerializedName("mainClass") private String mainClass;
    @SerializedName("inheritsFrom") private String inheritsFrom;
    @SerializedName("arguments") private Arguments arguments;
    @SerializedName("libraries") private List<Library> libraries;
    @SerializedName("downloads") private Downloads downloads;
    @SerializedName("assetIndex") private AssetIndex assetIndex;
    @SerializedName("assets") private String assets;
    @SerializedName("javaVersion") private JavaVersion javaVersion;

    public String getId() { return id; }
    public String getType() { return type; }
    public String getMainClass() { return mainClass; }
    public void setMainClass(String mc) { this.mainClass = mc; }
    public String getInheritsFrom() { return inheritsFrom; }
    public Arguments getArguments() { return arguments; }
    public void setArguments(Arguments a) { this.arguments = a; }
    public List<Library> getLibraries() { return libraries; }
    public void setLibraries(List<Library> libs) { this.libraries = libs; }
    public Downloads getDownloads() { return downloads; }
    public void setDownloads(Downloads dl) { this.downloads = dl; }
    public AssetIndex getAssetIndex() { return assetIndex; }
    public void setAssetIndex(AssetIndex ai) { this.assetIndex = ai; }
    public String getAssets() { return assets; }
    public void setAssets(String a) { this.assets = a; }
    public JavaVersion getJavaVersion() { return javaVersion; }

    /**
     * Representa um argumento condicional do JSON da versão.
     * Pode ser String simples ou objeto { "rules": [...], "value": "..." | [...] }
     */
    public static class ConditionalArg {
        @SerializedName("rules") private List<Rule> rules;
        @SerializedName("value") private Object value; // String ou List<String>

        public List<Rule> getRules() { return rules; }

        /** Retorna os valores do argumento como lista de strings. */
        public List<String> getValues() {
            if (value == null) return java.util.Collections.emptyList();
            if (value instanceof String) return java.util.Collections.singletonList((String) value);
            if (value instanceof List) {
                List<String> result = new java.util.ArrayList<>();
                for (Object v : (List<?>) value) result.add(v.toString());
                return result;
            }
            return java.util.Collections.singletonList(value.toString());
        }

        public boolean isAllowed() {
            if (rules == null || rules.isEmpty()) return true;
            // Se qualquer rule tem "features" (is_demo_user, has_custom_resolution, etc.),
            // não incluir — nosso launcher não suporta esses modos
            for (Rule rule : rules) {
                if (rule.getFeatures() != null && !rule.getFeatures().isEmpty()) return false;
            }
            boolean allowed = false;
            for (Rule rule : rules) {
                if ("allow".equals(rule.getAction())) {
                    if (rule.getOs() == null) allowed = true;
                    else if (rule.getOs().getName().equals(getCurrentOS())) allowed = true;
                } else if ("disallow".equals(rule.getAction())) {
                    if (rule.getOs() == null) allowed = false;
                    else if (rule.getOs().getName().equals(getCurrentOS())) allowed = false;
                }
            }
            return allowed;
        }

        private String getCurrentOS() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return "windows";
            if (os.contains("mac")) return "osx";
            return "linux";
        }
    }

    public static class Arguments {
        @SerializedName("game") private List<Object> game;
        @SerializedName("jvm") private List<Object> jvm;
        public List<Object> getGame() { return game; }
        public List<Object> getJvm() { return jvm; }
        public void setGame(List<Object> g) { this.game = g; }
        public void setJvm(List<Object> j) { this.jvm = j; }

        /**
         * Mescla os args do pai SEM sobrescrever os do filho.
         * Args do pai são adicionados ao INÍCIO (ex: -Xmx, -Djava.library.path)
         * para que fiquem antes dos args específicos do NeoForge.
         */
        public void mergeParent(Arguments parent) {
            if (parent.jvm != null) {
                if (this.jvm == null) {
                    this.jvm = new java.util.ArrayList<>(parent.jvm);
                } else {
                    // Adicionar args do pai no início que não estejam no filho
                    // NOTA: flags como --add-opens, --add-exports, --add-modules
                    // podem aparecer múltiplas vezes com valores diferentes,
                    // então NÃO deduplicamos esses flags.
                    List<Object> merged = new java.util.ArrayList<>(parent.jvm);
                    for (Object childArg : this.jvm) {
                        if (childArg instanceof String s
                            && (s.equals("--add-opens") || s.equals("--add-exports") || s.equals("--add-modules"))) {
                            merged.add(childArg);
                        } else if (!merged.contains(childArg)) {
                            merged.add(childArg);
                        }
                    }
                    this.jvm = merged;
                }
            }
            if (parent.game != null) {
                if (this.game == null) {
                    this.game = new java.util.ArrayList<>(parent.game);
                } else {
                    // Mesmo padrão do jvm: args do pai primeiro, depois args do filho
                    List<Object> mergedGame = new java.util.ArrayList<>(parent.game);
                    for (Object childArg : this.game) {
                        if (!mergedGame.contains(childArg)) mergedGame.add(childArg);
                    }
                    this.game = mergedGame;
                }
            }
        }

        /**
         * Resolve todos os JVM args (simples e condicionais) para uma lista plana de strings.
         * Usa o Gson passado para deserializar os objetos condicionais.
         */
        public List<String> resolveJvmArgs(com.google.gson.Gson gson) {
            return resolveArgs(jvm, gson);
        }

        public List<String> resolveGameArgs(com.google.gson.Gson gson) {
            return resolveArgs(game, gson);
        }

        private List<String> resolveArgs(List<Object> raw, com.google.gson.Gson gson) {
            List<String> result = new java.util.ArrayList<>();
            if (raw == null) return result;
            for (Object entry : raw) {
                if (entry instanceof String) {
                    result.add((String) entry);
                } else {
                    // Gson desserializa objetos JSON como LinkedTreeMap
                    try {
                        String json = gson.toJson(entry);
                        ConditionalArg carg = gson.fromJson(json, ConditionalArg.class);
                        if (carg.isAllowed()) {
                            result.addAll(carg.getValues());
                        }
                    } catch (Exception e) { LOG.debug("Erro ao resolver arg condicional: {}", e.getMessage()); }
                }
            }
            return result;
        }
    }

    public static class Library {
        @SerializedName("name") private String name;
        @SerializedName("downloads") private LibraryDownloads downloads;
        @SerializedName("rules") private List<Rule> rules;
        @SerializedName("natives") private java.util.Map<String, String> natives;

        public String getName() { return name; }
        public LibraryDownloads getDownloads() { return downloads; }
        public List<Rule> getRules() { return rules; }
        public java.util.Map<String, String> getNatives() { return natives; }

        public boolean isAllowed() {
            if (rules == null || rules.isEmpty()) return true;
            boolean allowed = false;
            for (Rule rule : rules) {
                if ("allow".equals(rule.getAction())) {
                    if (rule.getOs() == null) allowed = true;
                    else if (rule.getOs().getName().equals(getCurrentOS())) allowed = true;
                } else if ("disallow".equals(rule.getAction())) {
                    if (rule.getOs() == null) allowed = false;
                    else if (rule.getOs().getName().equals(getCurrentOS())) allowed = false;
                }
            }
            return allowed;
        }

        private String getCurrentOS() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return "windows";
            if (os.contains("mac")) return "osx";
            return "linux";
        }
    }

    public static class Rule {
        @SerializedName("action") private String action;
        @SerializedName("os") private OsRule os;
        @SerializedName("features") private java.util.Map<String, Boolean> features;
        public String getAction() { return action; }
        public OsRule getOs() { return os; }
        public java.util.Map<String, Boolean> getFeatures() { return features; }
    }

    public static class OsRule {
        @SerializedName("name") private String name;
        public String getName() { return name; }
    }

    public static class LibraryDownloads {
        @SerializedName("artifact") private DownloadFile artifact;
        @SerializedName("classifiers") private java.util.Map<String, DownloadFile> classifiers;
        public DownloadFile getArtifact() { return artifact; }
        public java.util.Map<String, DownloadFile> getClassifiers() { return classifiers; }
    }

    public static class DownloadFile {
        @SerializedName("path") private String path;
        @SerializedName("sha1") private String sha1;
        @SerializedName("size") private long size;
        @SerializedName("url") private String url;
        public String getPath() { return path; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
        public String getUrl() { return url; }
    }

    public static class Downloads {
        @SerializedName("client") private DownloadFile client;
        @SerializedName("client_mappings") private DownloadFile clientMappings;
        @SerializedName("server") private DownloadFile server;
        public DownloadFile getClient() { return client; }
        public DownloadFile getClientMappings() { return clientMappings; }
        public DownloadFile getServer() { return server; }
    }

    public static class AssetIndex {
        @SerializedName("id") private String id;
        @SerializedName("sha1") private String sha1;
        @SerializedName("size") private long size;
        @SerializedName("url") private String url;
        @SerializedName("totalSize") private long totalSize;
        public String getId() { return id; }
        public String getSha1() { return sha1; }
        public long getSize() { return size; }
        public String getUrl() { return url; }
        public long getTotalSize() { return totalSize; }
    }

    public static class JavaVersion {
        @SerializedName("component") private String component;
        @SerializedName("majorVersion") private int majorVersion;
        public String getComponent() { return component; }
        public int getMajorVersion() { return majorVersion; }
    }
}
