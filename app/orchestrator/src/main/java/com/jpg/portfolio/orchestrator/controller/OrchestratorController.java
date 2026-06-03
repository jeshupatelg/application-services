package com.jpg.portfolio.orchestrator.controller;

import com.jpg.portfolio.common.model.ActiveArtifact;
import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.DeletedArtifact;
import com.jpg.portfolio.common.model.UserImpl;
import com.jpg.portfolio.orchestrator.dto.GetViewsResponse;
import com.jpg.portfolio.orchestrator.repository.ArtifactRepository;
import com.jpg.portfolio.orchestrator.repository.UserRepository;
import com.jpg.portfolio.orchestrator.service.Generators;
import com.jpg.portfolio.orchestrator.service.PortfolioStorageService;
import com.jpg.portfolio.orchestrator.validation.ValidationChain;
import com.jpg.portfolio.orchestrator.validation.ValidationContext;
import com.jpg.portfolio.orchestrator.validation.ValidationResult;
import com.jpg.portfolio.orchestrator.validation.ValidationScenario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin")
public class OrchestratorController {

    private final UserRepository userRepository;
    private final ArtifactRepository artifactRepository;
    private final ValidationChain validationChain;
    private final PortfolioStorageService portfolioStorageService;

    @Autowired
    public OrchestratorController(UserRepository userRepository,
                                  ArtifactRepository artifactRepository,
                                  ValidationChain validationChain,
                                  PortfolioStorageService portfolioStorageService) {
        this.userRepository = userRepository;
        this.artifactRepository = artifactRepository;
        this.validationChain = validationChain;
        this.portfolioStorageService = portfolioStorageService;
    }

    // 1. GET /app/portfolio/admin - Redirect to the static dashboard page
    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> getUserPortfolioPage(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("<html><body><h3>Access Denied: Unauthorized</h3></body></html>");
        }

        // Make sure the user profile exists and sync alias strictly from JWT attribute
        UserImpl dbUser = userRepository.findByUsername(user).orElse(null);
        String keycloakAlias = (String) request.getAttribute("alias");
        if (dbUser == null) {
            dbUser = new UserImpl(user);
            dbUser.setAlias(keycloakAlias);
            userRepository.save(dbUser);
        } else if (keycloakAlias != null && !keycloakAlias.equals(dbUser.getAlias())) {//alias is mutable since it is Name
            dbUser.setAlias(keycloakAlias);
            userRepository.save(dbUser);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, request.getContextPath() + "/admin/index.html")
                .build();
    }


    // 1c. GET /app/portfolio/admin/user - JSON endpoint for user profile and active details
    @GetMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserDetails(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", 401, "error", "Unauthorized", "message", "Access Denied: Unauthorized"));
        }

        UserImpl dbUser = userRepository.findByUsername(user).orElse(null);
        String keycloakAlias = (String) request.getAttribute("alias");
        if (dbUser == null) {//should we create? Technically has permission since reached this far. Keeping for idempotency
            dbUser = new UserImpl(user);
            dbUser.setAlias(keycloakAlias);
            userRepository.save(dbUser);
        } else if (keycloakAlias != null && !keycloakAlias.equals(dbUser.getAlias())) {//alias is mutable since it is Name
            dbUser.setAlias(keycloakAlias);
            userRepository.save(dbUser);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("username", dbUser.getUserName());
        details.put("alias", dbUser.getAlias());
        details.put("active", dbUser.getActive());
        details.put("latest", dbUser.getLatest());

        return ResponseEntity.ok(details);
    }

    // 1b. GET /app/portfolio/admin/versions - JSON endpoint for version list details
    @GetMapping(value = "/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserPortfolioVersions(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Unauthorized");
        }

        UserImpl dbUser = userRepository.findByUsername(user).orElse(null);
        if (dbUser == null) {
            dbUser = new UserImpl(user);
            userRepository.save(dbUser);
        }

        GetViewsResponse response = new GetViewsResponse();
        response.setActive(dbUser.getActive());

        // Polymorphically iterate a single composed list to format both active and deleted versions
        for (Artifact artifact : dbUser.getArtifacts()) {
            boolean isDeleted = artifact instanceof DeletedArtifact;
            response.getVersions().add(new GetViewsResponse.VersionInfo(
                    artifact.getVersion(),
                    artifact.isMajorVersion(),
                    artifact.getTags(),
                    isDeleted ? null : artifact.getDownloadLink()
            ));
        }

        return ResponseEntity.ok(response);
    }

    // 2. POST /app/portfolio/admin/upload - Upload active portfolio version
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPortfolioVersion(
            @RequestParam("zip") MultipartFile file,
            @RequestParam(value = "isMajor", defaultValue = "false") boolean isMajor,
            @RequestParam(value = "tag", required = false) List<String> tags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "setActive", defaultValue = "false") boolean setActive,
            HttpServletRequest request) {

        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Unauthorized");
        }

        try {
            byte[] fileBytes = file.getBytes();

            ValidationContext context = new ValidationContext();
            context.setUsername(user);
            context.setZipFileBytes(fileBytes);
            context.setTags(tags != null ? tags : new ArrayList<>());
            context.setDesc(desc != null ? desc : "");
            context.setMajor(isMajor);
            context.setSetActive(setActive);

            ValidationResult securityResult = validationChain.validate(context, ValidationScenario.UPLOAD);
            if (!securityResult.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(securityResult.getReason());
            }

            UserImpl dbUser = userRepository.findByUsername(user).orElseGet(() -> {
                UserImpl newUser = new UserImpl(user);
                return userRepository.save(newUser);
            });

            String nextVersion = Generators.VersionGenerator.generateVersion(dbUser, isMajor);
            String downloadLink = Generators.DownloadLinkGenerator.generateDownloadLink(user, nextVersion);

            // First version (v1.0) must always be marked as a Major release
            boolean finalIsMajor = "v1.0".equals(nextVersion) || isMajor;

            // Persist as ActiveArtifact instance polymorphically in artifacts table
            Artifact artifact = new ActiveArtifact(
                    nextVersion,
                    tags != null ? tags : new ArrayList<>(),
                    fileBytes,
                    downloadLink,
                    desc != null ? desc : "",
                    LocalDateTime.now(),
                    finalIsMajor,
                    dbUser
            );

            dbUser.getArtifacts().add(artifact);
            dbUser.setLatest(nextVersion);

            if (setActive || dbUser.getActive() == null) {
                dbUser.setActive(nextVersion);
                portfolioStorageService.deployPortfolio(user, fileBytes);
            }

            userRepository.save(dbUser);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "version", nextVersion,
                    "downloadLink", downloadLink,
                    "isActive", dbUser.getActive().equals(nextVersion)
            ));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to read upload payload: " + e.getMessage());
        }
    }

    // 3. POST /app/portfolio/admin/active - Switch active version polymorphically
    @PostMapping("/active")
    public ResponseEntity<?> setActiveVersion(
            @RequestParam("version") String version,
            HttpServletRequest request) {

        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Unauthorized");
        }

        ValidationContext context = new ValidationContext();
        context.setUsername(user);
        context.setVersion(version);
        context.setSetActive(true);

        ValidationResult validationResult = validationChain.validate(context, ValidationScenario.ACTIVATE);
        if (!validationResult.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationResult.getReason());
        }

        UserImpl dbUser = context.getUser();
        
        // Retrieve the cached, validated ActiveArtifact directly from context
        ActiveArtifact targetArtifact = (ActiveArtifact) context.getTargetArtifact();

        try {
            portfolioStorageService.deployPortfolio(user, targetArtifact.getFile());

            dbUser.setActive(version);
            userRepository.save(dbUser);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Active version successfully switched to " + version
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to decompress and orchestrate sandbox folder: " + e.getMessage());
        }
    }

    // 4. DELETE /app/portfolio/admin/versions - Bulk delete active portfolios into metadata-only states
    @DeleteMapping("/versions")
    public ResponseEntity<?> deletePortfolioVersions(
            @RequestBody List<String> versionsToDelete,
            HttpServletRequest request) {

        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Unauthorized");
        }

        UserImpl dbUser = userRepository.findByUsername(user).orElse(null);
        if (dbUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User profile not found");
        }

        Map<String, Map<String, String>> responseMap = new LinkedHashMap<>();

        for (String targetVersion : versionsToDelete) {
            ValidationContext context = new ValidationContext();
            context.setUsername(user);
            context.setUser(dbUser);
            context.setVersion(targetVersion);
            context.setSetActive(false);

            ValidationResult validationResult = validationChain.validate(context, ValidationScenario.DELETE);
            if (!validationResult.isValid()) {
                responseMap.put(targetVersion, Map.of(
                        "status", "FAIL",
                        "reason", validationResult.getReason()
                ));
                continue;
            }

            // Retrieve the cached ActiveArtifact directly from the validation context
            ActiveArtifact activeArt = (ActiveArtifact) context.getTargetArtifact();

            // Transform polymorphically into a DeletedArtifact copy
            DeletedArtifact deleted = new DeletedArtifact(
                    activeArt.getVersion(),
                    activeArt.getTags(),
                    null, // Clears the download link
                    activeArt.getDesc(),
                    activeArt.getTimestamp(), // Retain original creation timestamp
                    activeArt.isMajorVersion(),
                    dbUser
            );

            // Cascade deletes old ActiveArtifact row, and inserts DeletedArtifact in H2 polymorphically
            dbUser.getArtifacts().remove(activeArt);
            dbUser.getArtifacts().add(deleted);

            responseMap.put(targetVersion, Map.of(
                    "status", "SUCCESS"
            ));
        }

        userRepository.save(dbUser);

        if (dbUser.getActive() != null && versionsToDelete.contains(dbUser.getActive())) {
            portfolioStorageService.deletePortfolio(user);
            dbUser.setActive(null);
            userRepository.save(dbUser);
        }

        return ResponseEntity.ok(responseMap);
    }

    // 5. GET /app/portfolio/admin/download/{version} - Secure authenticated download endpoint
    @GetMapping("/download/{version}")
    public ResponseEntity<?> downloadVersionZip(
            @PathVariable("version") String version,
            HttpServletRequest request) {

        String user = (String) request.getAttribute("username");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access Denied: Unauthorized");
        }

        Artifact artifact = artifactRepository.findByVersionAndUserUsername(version, user).orElse(null);
        if (artifact == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Requested version ZIP package not found");
        }

        // Return 400 Bad Request if the version has been deleted
        if (artifact instanceof DeletedArtifact) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Requested portfolio version has been deleted");
        }

        ActiveArtifact activeArtifact = (ActiveArtifact) artifact;
        byte[] zipBytes = activeArtifact.getFile();
        if (zipBytes == null || zipBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Binary files missing from database");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portfolio-" + version + ".zip\"")
                .body(zipBytes);
    }
}
