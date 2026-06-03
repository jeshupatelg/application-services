package com.jpg.portfolio.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@DiscriminatorValue("ACTIVE")
public class ActiveArtifact extends Artifact {
    private static final long serialVersionUID = 1L;

    @Lob
    @Column(name = "file_blob", length = 100000000)
    @JsonIgnore
    private byte[] file;

    public ActiveArtifact() {}

    public ActiveArtifact(String version, List<String> tags, byte[] file, String downloadLink, String desc, LocalDateTime timestamp, boolean isMajorVersion, UserImpl user) {
        super(version, tags, downloadLink, desc, timestamp, isMajorVersion, user);
        this.file = file;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }
}
