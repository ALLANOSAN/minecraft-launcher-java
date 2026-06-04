package com.minelauncher.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VersionInfo {
    @SerializedName("id")
    private String id;

    @SerializedName("type")
    private String type; // release, snapshot, old_beta, old_alpha

    @SerializedName("url")
    private String url;

    @SerializedName("time")
    private String time;

    @SerializedName("releaseTime")
    private String releaseTime;

    public String getId() { return id; }
    public String getType() { return type; }
    public String getUrl() { return url; }
    public String getTime() { return time; }
    public String getReleaseTime() { return releaseTime; }

    @Override
    public String toString() { return id; }

    public static class VersionManifest {
        @SerializedName("latest")
        private Latest latest;

        @SerializedName("versions")
        private List<VersionInfo> versions;

        public Latest getLatest() { return latest; }
        public List<VersionInfo> getVersions() { return versions; }
    }

    public static class Latest {
        @SerializedName("release")
        private String release;

        @SerializedName("snapshot")
        private String snapshot;

        public String getRelease() { return release; }
        public String getSnapshot() { return snapshot; }
    }
}
