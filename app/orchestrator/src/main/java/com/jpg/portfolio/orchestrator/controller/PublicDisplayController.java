package com.jpg.portfolio.orchestrator.controller;

import com.jpg.portfolio.common.model.ActiveArtifact;
import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.UserImpl;
import com.jpg.portfolio.orchestrator.repository.ArtifactRepository;
import com.jpg.portfolio.orchestrator.repository.UserRepository;
import com.jpg.portfolio.orchestrator.service.PortfolioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/display")
public class PublicDisplayController {

    private static final Logger log = LoggerFactory.getLogger(PublicDisplayController.class);

    private final UserRepository userRepository;
    private final ArtifactRepository artifactRepository;
    private final PortfolioStorageService portfolioStorageService;

    // Class-level thread-safe map holding active display validation cookie session values
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    public PublicDisplayController(UserRepository userRepository,
                                   ArtifactRepository artifactRepository,
                                   PortfolioStorageService portfolioStorageService) {
        this.userRepository = userRepository;
        this.artifactRepository = artifactRepository;
        this.portfolioStorageService = portfolioStorageService;
    }

    // Displays the active version, restoring the static files to the sidecar volume dynamically if missing
    @GetMapping("/{user}")
    public ResponseEntity<?> displayPortfolio(
            @PathVariable("user") String username,
            HttpServletRequest request,
            HttpServletResponse response) {
        String sanitizedUsername = username.replaceAll("[^a-zA-Z0-9_-]", "");
        
        log.info("displayPortfolio: Request received for user '{}'. Scheme: {}, Secure: {}, Host: {}, X-Forwarded-Proto: {}",
                sanitizedUsername, request.getScheme(), request.isSecure(), request.getHeader(HttpHeaders.HOST), request.getHeader("X-Forwarded-Proto"));

        UserImpl dbUser = userRepository.findByUsername(sanitizedUsername).orElse(null);
        if (dbUser == null || dbUser.getActive() == null) {
            log.warn("displayPortfolio: Active portfolio version not configured for user '{}'", sanitizedUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Active portfolio version not configured for user: " + sanitizedUsername);
        }

        // On-demand orchestration: If the volume folder is missing or cleared, restore it instantly!
        if (!portfolioStorageService.isPortfolioDeployed(sanitizedUsername)) {
            String activeVersion = dbUser.getActive();
            log.info("displayPortfolio: Static folder missing for '{}'. Dynamically restoring version '{}'...", sanitizedUsername, activeVersion);
            Artifact artifact = artifactRepository.findByVersionAndUserUsername(activeVersion, sanitizedUsername).orElse(null);
            
            if (artifact instanceof ActiveArtifact) {
                try {
                    portfolioStorageService.deployPortfolio(sanitizedUsername, ((ActiveArtifact) artifact).getFile());
                    log.info("displayPortfolio: Successfully restored static assets for '{}'", sanitizedUsername);
                } catch (IOException e) {
                    log.error("displayPortfolio: Failed to dynamically orchestrate sandbox files for '{}'", sanitizedUsername, e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to dynamically orchestrate sandbox files: " + e.getMessage());
                }
            } else {
                log.error("displayPortfolio: Active version ZIP is unavailable or deleted for '{}'", sanitizedUsername);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("The active version ZIP is unavailable or deleted from persistence archives");
            }
        }

        // Generate a dynamic, cryptographically secure display authorization token
        String sessionToken = UUID.randomUUID().toString();
        activeSessions.put(sanitizedUsername, sessionToken);
        log.info("displayPortfolio: Registered display session token for '{}': {}", sanitizedUsername, sessionToken);

        // Detect if secure is needed (either request is secure, or X-Forwarded-Proto is https)
        boolean isSecureContext = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));

        // Scope the temporary auth cookie to the display parent path to ensure trailing slash resilience
        // Set SameSite=None and Secure for HTTPS contexts to support iframe widgets
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("display_auth", sessionToken)
                .path("/app/portfolio/display")  // Scope to parent display path without trailing slash for max resilience
                .maxAge(3600) // 1-hour session limit
                .httpOnly(true);

        if (isSecureContext) {
            cookieBuilder.secure(true).sameSite("None");
            log.info("displayPortfolio: Serving HTTPS/secure context. Applied 'Secure; SameSite=None' to display_auth cookie.");
        } else {
            cookieBuilder.secure(false).sameSite("Lax");
            log.info("displayPortfolio: Serving HTTP context. Applied 'SameSite=Lax' to display_auth cookie.");
        }

        ResponseCookie displayCookie = cookieBuilder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, displayCookie.toString());

        // Use a relative path redirect to ensure the browser remains on the exact same host and port
        String targetUrl = request.getContextPath() + "/display/" + sanitizedUsername + "/index.html";
        log.info("displayPortfolio: Redirecting relative target for '{}' -> {}", sanitizedUsername, targetUrl);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, targetUrl)
                .build();
    }

    // Lightweight stateless auth check validating the display_auth cookie against the active session map
    @GetMapping("/auth/{user}")
    public ResponseEntity<?> verifySession(
            @PathVariable("user") String username,
            @CookieValue(value = "display_auth", required = false) String displayAuthToken) {
        String sanitizedUsername = username.replaceAll("[^a-zA-Z0-9_-]", "");
        String expectedToken = activeSessions.get(sanitizedUsername);
        
        log.info("verifySession: Auth check for user '{}'. Provided Token: '{}', Expected Token: '{}'", 
                sanitizedUsername, displayAuthToken, expectedToken);

        if (expectedToken != null && expectedToken.equals(displayAuthToken)) {
            log.info("verifySession: Authentication SUCCESSFUL for user '{}'", sanitizedUsername);
            return ResponseEntity.ok().build(); // 200 OK
        }
        log.warn("verifySession: Authentication FAILED for user '{}'", sanitizedUsername);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 401 Unauthorized
    }
}

