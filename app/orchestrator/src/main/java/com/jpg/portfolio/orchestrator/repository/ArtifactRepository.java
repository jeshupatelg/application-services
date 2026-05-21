package com.jpg.portfolio.orchestrator.repository;

import com.jpg.portfolio.common.model.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, String> {
    Optional<Artifact> findByVersionAndUserUsername(String version, String username);
}
