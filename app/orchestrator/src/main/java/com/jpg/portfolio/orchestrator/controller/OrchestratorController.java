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
@RequestMapping("/app/portfolio/admin")
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

    private boolean isUnAuthorizedUser(HttpServletRequest request, String pathUsername) {
        String tokenUsername = (String) request.getAttribute("username");
        return tokenUsername == null || !tokenUsername.equalsIgnoreCase(pathUsername);
    }

    // 1. GET /app/portfolio/admin/{user} - Display unified portfolio versions list
    @GetMapping("/{user}")
    public ResponseEntity<?> getUserPortfolio(@PathVariable("user") String user, HttpServletRequest request) {
        if (isUnAuthorizedUser(request, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: You can only query your own portfolio details");
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

    // 2. POST /app/portfolio/admin/{user}/upload - Upload active portfolio version
    @PostMapping(value = "/{user}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPortfolioVersion(
            @PathVariable("user") String user,
            @RequestParam("zip") MultipartFile file,
            @RequestParam(value = "isMajor", defaultValue = "false") boolean isMajor,
            @RequestParam(value = "tag", required = false) List<String> tags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "setActive", defaultValue = "false") boolean setActive,
            HttpServletRequest request) {

        if (isUnAuthorizedUser(request, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: You cannot upload files for another user");
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

            // Persist as ActiveArtifact instance polymorphically in artifacts table
            Artifact artifact = new ActiveArtifact(
                    nextVersion,
                    tags != null ? tags : new ArrayList<>(),
                    fileBytes,
                    downloadLink,
                    desc != null ? desc : "",
                    LocalDateTime.now(),
                    isMajor,
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

    // 3. POST /app/portfolio/admin/{user}/active - Switch active version polymorphically
    @PostMapping("/{user}/active")
    public ResponseEntity<?> setActiveVersion(
            @PathVariable("user") String user,
            @RequestParam("version") String version,
            HttpServletRequest request) {

        if (isUnAuthorizedUser(request, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: Action unauthorized");
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

    // 4. DELETE /app/portfolio/admin/{user}/versions - Bulk delete active portfolios into metadata-only states
    @DeleteMapping("/{user}/versions")
    public ResponseEntity<?> deletePortfolioVersions(
            @PathVariable("user") String user,
            @RequestBody List<String> versionsToDelete,
            HttpServletRequest request) {

        if (isUnAuthorizedUser(request, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: Action unauthorized");
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

    // 5. GET /app/portfolio/admin/{user}/download/{version} - Secure authenticated download endpoint
    @GetMapping("/{user}/download/{version}")
    public ResponseEntity<?> downloadVersionZip(
            @PathVariable("user") String user,
            @PathVariable("version") String version,
            HttpServletRequest request) {

        if (isUnAuthorizedUser(request, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: You cannot download another user's portfolios");
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
