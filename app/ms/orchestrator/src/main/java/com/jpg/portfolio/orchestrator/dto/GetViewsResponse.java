package com.jpg.portfolio.orchestrator.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GetViewsResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String active;
    private List<VersionInfo> versions = new ArrayList<>();

    public GetViewsResponse() {}

    public GetViewsResponse(String active, List<VersionInfo> versions) {
        this.active = active;
        this.versions = versions;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public List<VersionInfo> getVersions() {
        return versions;
    }

    public void setVersions(List<VersionInfo> versions) {
        this.versions = versions;
    }

    public static class VersionInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String version;
        private boolean isMajor;
        private List<String> tags = new ArrayList<>();
        private String downloadLink;

        public VersionInfo() {}

        public VersionInfo(String version, boolean isMajor, List<String> tags, String downloadLink) {
            this.version = version;
            this.isMajor = isMajor;
            this.tags = tags;
            this.downloadLink = downloadLink;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isIsMajor() {
            return isMajor;
        }

        public void setIsMajor(boolean major) {
            isMajor = major;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getDownloadLink() {
            return downloadLink;
        }

        public void setDownloadLink(String downloadLink) {
            this.downloadLink = downloadLink;
        }
    }
}
