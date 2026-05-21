package com.jpg.portfolio.orchestrator.validation;

import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.UserImpl;
import java.util.ArrayList;
import java.util.List;

public class ValidationContext {
    private String username;
    private String version;
    private byte[] zipFileBytes;
    private List<String> tags = new ArrayList<>();
    private String desc;
    private boolean isMajor;
    private boolean setActive;
    private UserImpl user;
    private Artifact targetArtifact;

    public ValidationContext() {}

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public byte[] getZipFileBytes() {
        return zipFileBytes;
    }

    public void setZipFileBytes(byte[] zipFileBytes) {
        this.zipFileBytes = zipFileBytes;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public boolean isMajor() {
        return isMajor;
    }

    public void setMajor(boolean major) {
        this.isMajor = major;
    }

    public boolean isSetActive() {
        return setActive;
    }

    public void setSetActive(boolean setActive) {
        this.setActive = setActive;
    }

    public UserImpl getUser() {
        return user;
    }

    public void setUser(UserImpl user) {
        this.user = user;
    }

    public Artifact getTargetArtifact() {
        return targetArtifact;
    }

    public void setTargetArtifact(Artifact targetArtifact) {
        this.targetArtifact = targetArtifact;
    }
}
