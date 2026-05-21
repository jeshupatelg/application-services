package com.jpg.portfolio.orchestrator.controller;

import com.jpg.portfolio.common.model.ActiveArtifact;
import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.UserImpl;
import com.jpg.portfolio.orchestrator.repository.ArtifactRepository;
import com.jpg.portfolio.orchestrator.repository.UserRepository;
import com.jpg.portfolio.orchestrator.service.PortfolioStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/app/portfolio/display")
public class PublicDisplayController {

    private final UserRepository userRepository;
    private final ArtifactRepository artifactRepository;
    private final PortfolioStorageService portfolioStorageService;

    // Configurable base URL pointing to the read-only sandbox static container
    @Value("${portfolio.sandbox.url}")
    private String sandboxBaseUrl;

    @Autowired
    public PublicDisplayController(UserRepository userRepository,
                                   ArtifactRepository artifactRepository,
                                   PortfolioStorageService portfolioStorageService) {
        this.userRepository = userRepository;
        this.artifactRepository = artifactRepository;
        this.portfolioStorageService = portfolioStorageService;
    }

    // Displays the active version, restoring the static files to the sidecar volume dynamically if missing
    @GetMapping("/{user}")
    public ResponseEntity<?> displayPortfolio(@PathVariable("user") String username) {
        String sanitizedUsername = username.replaceAll("[^a-zA-Z0-9_-]", "");
        
        UserImpl dbUser = userRepository.findByUsername(sanitizedUsername).orElse(null);
        if (dbUser == null || dbUser.getActive() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Active portfolio version not configured for user: " + sanitizedUsername);
        }

        // On-demand orchestration: If the volume folder is missing or cleared, restore it instantly!
        if (!portfolioStorageService.isPortfolioDeployed(sanitizedUsername)) {
            String activeVersion = dbUser.getActive();
            Artifact artifact = artifactRepository.findByVersionAndUserUsername(activeVersion, sanitizedUsername).orElse(null);
            
            if (artifact instanceof ActiveArtifact) {
                try {
                    portfolioStorageService.deployPortfolio(sanitizedUsername, ((ActiveArtifact) artifact).getFile());
                } catch (IOException e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to dynamically orchestrate sandbox files: " + e.getMessage());
                }
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("The active version ZIP is unavailable or deleted from persistence archives");
            }
        }

        String targetUrl = sandboxBaseUrl + "/" + sanitizedUsername + "/index.html";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, targetUrl)
                .build();
    }
}
