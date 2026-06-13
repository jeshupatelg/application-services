package com.jpg.portfolio.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserImpl implements User, Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "active_version")
    private String active;

    @Column(name = "latest_version")
    private String latest;

    @Column(name = "alias")
    private String alias;

    @Lob
    @Column(name = "photo_blob", length = 5000000) // Up to 5MB photo
    private byte[] photo;

    @Embedded
    private UserSettings userSettings = new UserSettings();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Artifact> artifacts = new ArrayList<>();

    public UserImpl() {}

    public UserImpl(String username) {
        this.username = username;
    }

    @Override
    public UserSettings getUserSettings() {
        return userSettings;
    }

    @Override
    public void setUserSettings(UserSettings settings) {
        this.userSettings = settings;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public byte[] getPhoto() {
        return photo;
    }

    @Override
    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }

    @Override
    public String getUserName() {
        return username;
    }

    @Override
    public void setUserName(String username) {
        this.username = username;
    }

    @Override
    public String getLatest() {
        return latest;
    }

    @Override
    public void setLatest(String latestVersion) {
        this.latest = latestVersion;
    }

    @Override
    public String getActive() {
        return active;
    }

    @Override
    public void setActive(String activeVersion) {
        this.active = activeVersion;
    }

    @Override
    public boolean isLatestVersionActive() {
        return userSettings != null && Boolean.TRUE.equals(userSettings.getIsLatestVersionActive());
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }



    @Override
    public String toString() {
        return "UserImpl{" +
                "username='" + username + '\'' +
                ", active='" + active + '\'' +
                ", latest='" + latest + '\'' +
                ", alias='" + alias + '\'' +
                ", userSettings=" + userSettings +
                '}';
    }
}
