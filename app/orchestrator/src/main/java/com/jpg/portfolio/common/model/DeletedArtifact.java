package com.jpg.portfolio.common.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@DiscriminatorValue("DELETED")
public class DeletedArtifact extends Artifact {
    private static final long serialVersionUID = 1L;

    public DeletedArtifact() {}

    public DeletedArtifact(String version, List<String> tags, String downloadLink, String desc, LocalDateTime timestamp, boolean isMajorVersion, UserImpl user) {
        // downloadLink is passed as null since the binary ZIP is deleted from storage
        super(version, tags, null, desc, timestamp, isMajorVersion, user);
    }
}
