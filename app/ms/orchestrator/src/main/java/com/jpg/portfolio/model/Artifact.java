package com.jpg.portfolio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "artifacts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "artifact_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Artifact implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "version")
    private String version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artifact_tags", joinColumns = @JoinColumn(name = "version"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Column(name = "download_link")
    private String downloadLink;

    @Column(name = "description")
    private String desc;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "is_major_version")
    private boolean isMajorVersion;

    /** Bidirectional owning-side association:
     * 1. Configures 'username' FK mapping directly in the artifacts table for single-statement INSERT.
     * 2. Enables high-performance direct child queries in ArtifactRepository without loading the full User aggregate.
     * 3. Jackson @JsonIgnore blocks circular reference serialization loops at runtime.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "username")
    @JsonIgnore
    private UserImpl user;

    public Artifact() {}

    public Artifact(String version, List<String> tags, String downloadLink, String desc, LocalDateTime timestamp, boolean isMajorVersion, UserImpl user) {
        this.version = version;
        this.tags = tags;
        this.downloadLink = downloadLink;
        this.desc = desc;
        this.timestamp = timestamp;
        this.isMajorVersion = isMajorVersion;
        this.user = user;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isMajorVersion() {
        return isMajorVersion;
    }

    public void setMajorVersion(boolean majorVersion) {
        isMajorVersion = majorVersion;
    }

    public UserImpl getUser() {
        return user;
    }

    public void setUser(UserImpl user) {
        this.user = user;
    }
}
